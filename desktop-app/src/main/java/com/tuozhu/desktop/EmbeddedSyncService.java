package com.tuozhu.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmbeddedSyncService implements AutoCloseable {
    private static final Pattern STRING_FIELD_PATTERN_TEMPLATE =
        Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
    private static final Pattern LONG_FIELD_PATTERN_TEMPLATE =
        Pattern.compile("\"%s\"\\s*:\\s*(-?\\d+)");
    private static final Pattern OBJECT_PATTERN =
        Pattern.compile("\\{[^{}]*\"externalJobId\"[^{}]*}", Pattern.DOTALL);

    private final Supplier<Config> configSupplier;
    private final Consumer<String> logger;
    private final Set<Path> queuedExplicitPaths = new LinkedHashSet<>();

    private HttpServer server;
    private ExecutorService executor;
    private GcodeWatchService gcodeWatchService;
    private volatile Process syncProcess;
    private boolean queuedFullSync;

    EmbeddedSyncService(
        Supplier<Config> configSupplier,
        Consumer<String> logger
    ) {
        this.configSupplier = configSupplier;
        this.logger = logger;
    }

    synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        Config config = configSupplier.get();
        server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/health", this::handleHealth);
        server.createContext("/api/sync/pull", this::handlePull);
        server.createContext("/api/sync/confirm", this::handleConfirm);
        server.start();

        log("桌面同步服务已启动：http://0.0.0.0:" + config.port() + "/");
        log("GET  /health");
        log("GET  /api/sync/pull");
        log("POST /api/sync/confirm");

        startWatcher(config);
        startBackgroundSync("startup");
    }

    synchronized boolean isRunning() {
        return server != null;
    }

    synchronized void stop() {
        GcodeWatchService watcher = gcodeWatchService;
        gcodeWatchService = null;
        if (watcher != null) {
            watcher.close();
        }

        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        Process process = syncProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        syncProcess = null;
        queuedExplicitPaths.clear();
        queuedFullSync = false;
    }

    @Override
    public void close() {
        stop();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        log(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        String body = "{"
            + "\"status\":\"ok\","
            + "\"source\":\"DESKTOP_AGENT\","
            + "\"serverTime\":" + System.currentTimeMillis() + ","
            + "\"syncBusy\":" + isSyncBusy()
            + "}";
        sendJson(exchange, 200, body);
    }

    private void handlePull(HttpExchange exchange) throws IOException {
        log(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        startBackgroundSync("pull");
        sendJson(exchange, 200, buildPullPayload());
    }

    private void handleConfirm(HttpExchange exchange) throws IOException {
        log(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        String requestBody = readBody(exchange.getRequestBody());
        ConfirmationReceipt receipt = ConfirmationReceipt.parse(requestBody);
        if (receipt.externalJobId().isBlank()) {
            sendJson(exchange, 400, "{\"status\":\"ERROR\",\"message\":\"externalJobId is required.\"}");
            return;
        }

        writeConfirmation(receipt);
        startBackgroundSync("confirm");
        String body = "{"
            + "\"status\":\"SUCCESS\","
            + "\"source\":\"DESKTOP_AGENT\","
            + "\"syncedAt\":" + System.currentTimeMillis() + ","
            + "\"message\":\"Confirmation recorded: " + escapeJson(receipt.externalJobId()) + "\""
            + "}";
        sendJson(exchange, 200, body);
    }

    private synchronized boolean startBackgroundSync(String reason) {
        return startBackgroundSync(reason, Collections.emptyList());
    }

    private synchronized boolean startBackgroundSync(String reason, List<Path> explicitPaths) {
        if (isSyncBusy()) {
            queueFollowUpSync(reason, explicitPaths);
            return false;
        }

        Config config = configSupplier.get();
        ProcessBuilder builder = buildSyncAgentProcess(config, explicitPaths);
        Path workingDirectory = config.agentRoot().getParent() != null
            ? config.agentRoot().getParent()
            : config.agentRoot();
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            syncProcess = process;
            if (explicitPaths.isEmpty()) {
                log("后台同步已启动（" + reason + "）。");
            } else {
                log("后台同步已启动（" + reason + "），目标文件 " + explicitPaths.size() + " 个。");
            }
            Thread pumpThread = new Thread(() -> pumpSyncOutput(process), "desktop-sync-agent-pump");
            pumpThread.setDaemon(true);
            pumpThread.start();
            return true;
        } catch (IOException exception) {
            log("启动后台同步失败：" + exception.getMessage());
            return false;
        }
    }

    private void pumpSyncOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), processOutputCharset())
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                log("[同步] " + line);
            }
            int exitCode = process.waitFor();
            log("后台同步已结束，退出码 " + exitCode + "。");
        } catch (Exception exception) {
            log("后台同步异常：" + exception.getMessage());
        } finally {
            synchronized (this) {
                if (syncProcess == process) {
                    syncProcess = null;
                }
            }
            startQueuedSyncIfNeeded();
        }
    }

    private synchronized void queueFollowUpSync(String reason, List<Path> explicitPaths) {
        if (explicitPaths.isEmpty()) {
            queuedFullSync = true;
            log("后台同步仍在运行，已排队等待补做一次全量同步（" + reason + "）。");
            return;
        }

        for (Path path : explicitPaths) {
            queuedExplicitPaths.add(path.toAbsolutePath().normalize());
        }
        log("[监听] 同步进行中，已排队 " + explicitPaths.size() + " 个 G-code 文件。");
    }

    private void startQueuedSyncIfNeeded() {
        List<Path> explicitPaths = Collections.emptyList();
        boolean fullSync = false;
        synchronized (this) {
            if (isSyncBusy()) {
                return;
            }
            if (!queuedExplicitPaths.isEmpty()) {
                explicitPaths = new ArrayList<>(queuedExplicitPaths);
                queuedExplicitPaths.clear();
            } else if (queuedFullSync) {
                queuedFullSync = false;
                fullSync = true;
            }
        }

        if (!explicitPaths.isEmpty()) {
            startBackgroundSync("queued-watch", explicitPaths);
        } else if (fullSync) {
            startBackgroundSync("queued");
        }
    }

    private boolean isSyncBusy() {
        Process process = syncProcess;
        return process != null && process.isAlive();
    }

    private void startWatcher(Config config) throws IOException {
        GcodeWatchService watcher = gcodeWatchService;
        if (watcher != null) {
            watcher.close();
            gcodeWatchService = null;
        }

        if (config.useSample()) {
            log("[监听] 示例模式下不启用自动监听。");
            return;
        }

        List<Path> roots = new ArrayList<>();
        for (String root : config.gcodeRoots()) {
            if (root != null && !root.isBlank()) {
                roots.add(Path.of(root));
            }
        }

        GcodeWatchService nextWatcher = new GcodeWatchService(
            roots,
            this::log,
            path -> startBackgroundSync("watcher", List.of(path))
        );
        nextWatcher.start();
        gcodeWatchService = nextWatcher;
    }

    private ProcessBuilder buildSyncAgentProcess(Config config, List<Path> explicitPaths) {
        List<String> command = new ArrayList<>();
        command.add(DesktopSyncApp.resolvePowerShellExecutable());
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(config.agentRoot().resolve("run-sync-agent.ps1").toString());
        command.add("-MaxFileAgeDays");
        command.add(Integer.toString(config.maxAgeDays()));
        if (config.useSample()) {
            command.add("-UseSample");
        } else {
            command.add("-UseBambuGcode");
            if (!explicitPaths.isEmpty()) {
                command.add("-GcodePaths");
                explicitPaths.stream()
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .forEach(command::add);
            } else if (!config.gcodeRoots().isEmpty()) {
                command.add("-GcodeSearchRoots");
                command.addAll(config.gcodeRoots());
            }
        }
        return new ProcessBuilder(command);
    }

    private String buildPullPayload() {
        Config config = configSupplier.get();
        Path outboxPath = config.agentRoot().resolve("outbox").resolve("desktop-outbox.json");
        Path statePath = config.agentRoot().resolve("state").resolve("state.json");
        String draftJobsJson = readJsonArray(outboxPath);
        StateSnapshot snapshot = StateSnapshot.read(statePath);
        boolean syncBusy = isSyncBusy();
        boolean hasCache = Files.exists(outboxPath) || Files.exists(statePath);

        String message;
        String status;
        if (!hasCache && syncBusy) {
            message = "桌面端正在准备同步数据，请稍后再试。";
            status = "IDLE";
        } else if (syncBusy) {
            message = "已返回当前缓存，桌面端正在后台刷新。";
            status = "SUCCESS";
        } else {
            message = "桌面同步完成，待确认任务 " + snapshot.pendingDrafts(draftJobsJson) + " 条。";
            status = "SUCCESS";
        }

        return "{"
            + "\"status\":\"" + status + "\","
            + "\"source\":\"DESKTOP_AGENT\","
            + "\"syncedAt\":" + snapshot.updatedAt() + ","
            + "\"message\":\"" + escapeJson(message) + "\","
            + "\"draftJobs\":" + draftJobsJson + ","
            + "\"warnings\":" + toJsonArray(snapshot.warnings()) + ","
            + "\"inputMode\":" + jsonStringOrNull(snapshot.inputMode()) + ","
            + "\"syncBusy\":" + syncBusy
            + "}";
    }

    private synchronized void writeConfirmation(ConfirmationReceipt receipt) throws IOException {
        Config config = configSupplier.get();
        Path confirmationPath = config.agentRoot().resolve("outbox").resolve("confirmation-log.json");
        List<ConfirmationReceipt> receipts = readConfirmations(confirmationPath);
        List<ConfirmationReceipt> updated = new ArrayList<>();
        boolean replaced = false;
        for (ConfirmationReceipt item : receipts) {
            if (Objects.equals(item.externalJobId(), receipt.externalJobId())) {
                updated.add(receipt);
                replaced = true;
            } else {
                updated.add(item);
            }
        }
        if (!replaced) {
            updated.add(receipt);
        }
        Files.createDirectories(confirmationPath.getParent());
        Path tempPath = confirmationPath.resolveSibling("confirmation-log.json.tmp");
        Files.writeString(tempPath, ConfirmationReceipt.toJsonArray(updated), StandardCharsets.UTF_8);
        try {
            Files.move(tempPath, confirmationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, confirmationPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<ConfirmationReceipt> readConfirmations(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return Collections.emptyList();
        }
        List<ConfirmationReceipt> items = new ArrayList<>();
        Matcher matcher = OBJECT_PATTERN.matcher(raw);
        while (matcher.find()) {
            items.add(ConfirmationReceipt.parse(matcher.group()));
        }
        return items;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }

    private String readBody(InputStream stream) throws IOException {
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readJsonArray(Path path) {
        try {
            if (Files.exists(path)) {
                String raw = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!raw.isEmpty()) {
                    return raw;
                }
            }
        } catch (IOException ignored) {
        }
        return "[]";
    }

    private static String toJsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeJson(values.get(i))).append('"');
        }
        return builder.append(']').toString();
    }

    private static String jsonStringOrNull(String value) {
        if (value == null || value.isBlank()) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private static String extractString(String json, String fieldName) {
        Pattern pattern = Pattern.compile(
            String.format(STRING_FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(fieldName)),
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    private static Long extractLong(String json, String fieldName) {
        Pattern pattern = Pattern.compile(String.format(LONG_FIELD_PATTERN_TEMPLATE.pattern(), Pattern.quote(fieldName)));
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static List<String> extractStringArray(String json, String fieldName) {
        String content = extractArrayContent(json, fieldName);
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(content);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(unescapeJson(matcher.group(1)));
        }
        return values;
    }

    private static String extractArrayContent(String json, String fieldName) {
        int fieldIndex = json.indexOf("\"" + fieldName + "\"");
        if (fieldIndex < 0) {
            return null;
        }
        int start = json.indexOf('[', fieldIndex);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        return null;
    }

    private void log(String line) {
        logger.accept(line);
    }

    private static Charset processOutputCharset() {
        return Charset.defaultCharset();
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i == value.length() - 1) {
                builder.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '"':
                    builder.append('"');
                    break;
                case '\\':
                    builder.append('\\');
                    break;
                case '/':
                    builder.append('/');
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                default:
                    builder.append(next);
                    break;
            }
        }
        return builder.toString();
    }

    record Config(
        Path agentRoot,
        int port,
        int maxAgeDays,
        boolean useSample,
        List<String> gcodeRoots
    ) {
    }

    private record StateSnapshot(
        long updatedAt,
        String inputMode,
        List<String> warnings
    ) {
        static StateSnapshot read(Path statePath) {
            if (!Files.exists(statePath)) {
                return new StateSnapshot(System.currentTimeMillis(), null, Collections.emptyList());
            }
            try {
                String raw = Files.readString(statePath, StandardCharsets.UTF_8);
                Long updatedAt = extractLong(raw, "updatedAt");
                String inputMode = extractString(raw, "inputMode");
                List<String> warnings = extractStringArray(raw, "warnings");
                return new StateSnapshot(
                    updatedAt != null ? updatedAt : System.currentTimeMillis(),
                    inputMode,
                    warnings
                );
            } catch (IOException ignored) {
                return new StateSnapshot(System.currentTimeMillis(), null, Collections.emptyList());
            }
        }

        int pendingDrafts(String rawDrafts) {
            Matcher matcher = OBJECT_PATTERN.matcher(rawDrafts);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        }
    }

    private record ConfirmationReceipt(
        String externalJobId,
        long confirmedAt,
        Long targetRollId
    ) {
        static ConfirmationReceipt parse(String json) {
            String externalJobId = Objects.requireNonNullElse(extractString(json, "externalJobId"), "");
            Long confirmedAt = extractLong(json, "confirmedAt");
            Long targetRollId = extractLong(json, "targetRollId");
            return new ConfirmationReceipt(
                externalJobId,
                confirmedAt != null ? confirmedAt : 0L,
                targetRollId
            );
        }

        static String toJsonArray(List<ConfirmationReceipt> receipts) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < receipts.size(); i++) {
                ConfirmationReceipt receipt = receipts.get(i);
                if (i > 0) {
                    builder.append(',');
                }
                builder.append('{')
                    .append("\"externalJobId\":\"").append(escapeJson(receipt.externalJobId())).append("\",")
                    .append("\"confirmedAt\":").append(receipt.confirmedAt());
                if (receipt.targetRollId() != null) {
                    builder.append(",\"targetRollId\":").append(receipt.targetRollId());
                }
                builder.append('}');
            }
            return builder.append(']').toString();
        }
    }
}
