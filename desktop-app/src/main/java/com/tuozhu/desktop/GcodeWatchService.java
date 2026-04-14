package com.tuozhu.desktop;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class GcodeWatchService implements AutoCloseable {
    private static final long INITIAL_SCAN_DELAY_MS = 1200L;
    private static final long STABILITY_DELAY_MS = 1200L;
    private static final int REQUIRED_STABLE_PASSES = 2;
    private static final int MAX_SCAN_DEPTH = 6;

    private final List<Path> roots;
    private final Consumer<String> logger;
    private final Consumer<Path> stableFileConsumer;
    private final Map<WatchKey, Path> watchedDirectories = new ConcurrentHashMap<>();
    private final Set<Path> registeredDirectories = ConcurrentHashMap.newKeySet();
    private final Set<Path> pendingRoots = ConcurrentHashMap.newKeySet();
    private final Map<Path, CandidateState> candidateStates = new ConcurrentHashMap<>();
    private final Map<Path, ScheduledFuture<?>> pendingProbes = new ConcurrentHashMap<>();
    private final Map<Path, String> processedFingerprints = new ConcurrentHashMap<>();
    private final ExecutorService watchExecutor;
    private final ScheduledExecutorService scheduler;

    private volatile WatchService watchService;
    private volatile boolean running;

    GcodeWatchService(List<Path> roots, Consumer<String> logger, Consumer<Path> stableFileConsumer) {
        this.roots = normalizeRoots(roots);
        this.logger = logger;
        this.stableFileConsumer = stableFileConsumer;
        this.watchExecutor = Executors.newSingleThreadExecutor(namedThreadFactory("gcode-watch-loop"));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedThreadFactory("gcode-watch-probe"));
    }

    void start() throws IOException {
        if (running) {
            return;
        }
        watchService = Paths.get(".").getFileSystem().newWatchService();
        running = true;

        if (roots.isEmpty()) {
            log("[监听] 未配置 G-code 监听目录。");
            return;
        }

        for (Path root : roots) {
            registerRoot(root);
        }

        watchExecutor.execute(this::watchLoop);
        log("[监听] 已启动 G-code 自动监听，共 " + roots.size() + " 个目录。");
    }

    @Override
    public void close() {
        running = false;
        for (ScheduledFuture<?> future : pendingProbes.values()) {
            future.cancel(false);
        }
        pendingProbes.clear();
        watchedDirectories.clear();
        registeredDirectories.clear();
        pendingRoots.clear();
        candidateStates.clear();
        processedFingerprints.clear();
        try {
            WatchService service = watchService;
            if (service != null) {
                service.close();
            }
        } catch (IOException ignored) {
        }
        scheduler.shutdownNow();
        watchExecutor.shutdownNow();
    }

    private void registerRoot(Path root) {
        Path existing = nearestExistingAncestor(root);
        if (existing == null) {
            pendingRoots.add(root);
            log("[监听] 等待目录出现：" + root);
            return;
        }

        try {
            if (Files.exists(root) && Files.isDirectory(root)) {
                pendingRoots.remove(root);
                registerRecursively(root);
                scheduleDirectoryScan(root, "启动回补");
            } else {
                pendingRoots.add(root);
                registerDirectorySafely(existing);
                log("[监听] 等待缓存目录出现：" + root);
            }
        } catch (IOException exception) {
            log("[监听] 监听目录失败：" + root + "，" + exception.getMessage());
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException exception) {
                return;
            }

            Path directory = watchedDirectories.get(key);
            if (directory == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    scheduleDirectoryScan(directory, "目录事件溢出");
                    continue;
                }

                Object context = event.context();
                if (!(context instanceof Path relativePath)) {
                    continue;
                }

                Path changedPath = directory.resolve(relativePath).toAbsolutePath().normalize();
                handlePathEvent(kind, changedPath);
            }

            if (!key.reset()) {
                watchedDirectories.remove(key);
            }
        }
    }

    private void handlePathEvent(WatchEvent.Kind<?> kind, Path changedPath) {
        activatePendingRoots();

        if (isMetadataPath(changedPath)) {
            Path parent = changedPath.getParent();
            if (parent != null) {
                scheduleDirectoryScan(parent, "Metadata 更新");
            }
            if (Files.isDirectory(changedPath) && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                try {
                    registerRecursively(changedPath);
                } catch (IOException exception) {
                    log("[监听] 注册 Metadata 目录失败：" + changedPath + "，" + exception.getMessage());
                }
            }
            return;
        }

        if (Files.isDirectory(changedPath)) {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                try {
                    registerRecursively(changedPath);
                } catch (IOException exception) {
                    log("[监听] 注册新目录失败：" + changedPath + "，" + exception.getMessage());
                }
            }
            scheduleDirectoryScan(changedPath, "目录变更");
            return;
        }

        if (isGcodeCandidate(changedPath)) {
            scheduleProbe(changedPath, "检测到切片文件");
        }
    }

    private void activatePendingRoots() {
        for (Path root : List.copyOf(pendingRoots)) {
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue;
            }
            pendingRoots.remove(root);
            try {
                registerRecursively(root);
                scheduleDirectoryScan(root, "目录出现");
                log("[监听] 已接入缓存目录：" + root);
            } catch (IOException exception) {
                log("[监听] 接入缓存目录失败：" + root + "，" + exception.getMessage());
            }
        }
    }

    private void registerRecursively(Path root) throws IOException {
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream
                .filter(Files::isDirectory)
                .map(path -> path.toAbsolutePath().normalize())
                .forEach(this::registerDirectorySafely);
        }
    }

    private void registerDirectorySafely(Path directory) {
        if (!registeredDirectories.add(directory)) {
            return;
        }
        try {
            WatchKey key = directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );
            watchedDirectories.put(key, directory);
        } catch (IOException exception) {
            registeredDirectories.remove(directory);
            log("[监听] 注册目录失败：" + directory + "，" + exception.getMessage());
        }
    }

    private void scheduleDirectoryScan(Path path, String reason) {
        if (!running || path == null) {
            return;
        }
        scheduler.schedule(() -> scanDirectory(path, reason), INITIAL_SCAN_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void scanDirectory(Path path, String reason) {
        if (!running || path == null || !Files.exists(path)) {
            return;
        }

        if (Files.isRegularFile(path) && isGcodeCandidate(path)) {
            scheduleProbe(path, reason);
            return;
        }

        if (!Files.isDirectory(path)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(path, MAX_SCAN_DEPTH)) {
            stream
                .filter(Files::isRegularFile)
                .filter(this::isGcodeCandidate)
                .map(candidate -> candidate.toAbsolutePath().normalize())
                .forEach(candidate -> scheduleProbe(candidate, reason));
        } catch (IOException exception) {
            log("[监听] 扫描目录失败：" + path + "，" + exception.getMessage());
        }
    }

    private void scheduleProbe(Path file, String reason) {
        if (!running || file == null) {
            return;
        }
        Path normalized = file.toAbsolutePath().normalize();
        ScheduledFuture<?> previous = pendingProbes.get(normalized);
        if (previous != null) {
            previous.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(
            () -> probeCandidate(normalized, reason),
            STABILITY_DELAY_MS,
            TimeUnit.MILLISECONDS
        );
        pendingProbes.put(normalized, future);
    }

    private void probeCandidate(Path file, String reason) {
        pendingProbes.remove(file);
        if (!running || !Files.exists(file) || !Files.isRegularFile(file)) {
            candidateStates.remove(file);
            return;
        }

        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            CandidateFingerprint fingerprint = new CandidateFingerprint(
                attributes.size(),
                attributes.lastModifiedTime()
            );

            CandidateState previous = candidateStates.get(file);
            if (previous == null || !previous.fingerprint().equals(fingerprint)) {
                candidateStates.put(file, new CandidateState(fingerprint, 1));
                log("[监听] 正在稳定文件：" + file.getFileName() + "（" + reason + "）");
                scheduleProbe(file, "继续稳定");
                return;
            }

            int stablePasses = previous.stablePasses() + 1;
            if (stablePasses < REQUIRED_STABLE_PASSES) {
                candidateStates.put(file, new CandidateState(fingerprint, stablePasses));
                scheduleProbe(file, "继续稳定");
                return;
            }

            candidateStates.remove(file);
            String processedFingerprint = fingerprint.toToken();
            String lastProcessed = processedFingerprints.put(file, processedFingerprint);
            if (processedFingerprint.equals(lastProcessed)) {
                return;
            }

            log("[监听] 文件稳定，准备同步：" + file);
            stableFileConsumer.accept(file);
        } catch (IOException exception) {
            log("[监听] 读取文件状态失败：" + file + "，" + exception.getMessage());
            scheduleProbe(file, "读取失败后重试");
        }
    }

    private boolean isMetadataPath(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return "Metadata".equalsIgnoreCase(fileName);
    }

    private boolean isGcodeCandidate(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".gcode");
    }

    private Path nearestExistingAncestor(Path path) {
        Path current = path == null ? null : path.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current) && Files.isDirectory(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void log(String message) {
        logger.accept(message);
    }

    private static List<Path> normalizeRoots(List<Path> roots) {
        List<Path> normalized = new ArrayList<>();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            Path value = root.toAbsolutePath().normalize();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static ThreadFactory namedThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private record CandidateState(CandidateFingerprint fingerprint, int stablePasses) {
    }

    private record CandidateFingerprint(long size, FileTime lastModifiedTime) {
        String toToken() {
            return size + "|" + lastModifiedTime.toMillis();
        }
    }
}
