package com.tuozhu.desktop;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

public class GcodeWatchServiceTest {
    @Test
    public void watcher_detectsStableGcodeAfterNewSliceIsCreated() throws Exception {
        Path parent = Files.createTempDirectory("gcode-watch-new");
        Path root = parent.resolve("bamboo_model");
        List<Path> captured = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (GcodeWatchService watcher = new GcodeWatchService(
            List.of(root),
            line -> { },
            path -> {
                captured.add(path);
                latch.countDown();
            }
        )) {
            watcher.start();
            Thread.sleep(400L);

            Path sliceDir = root.resolve("Mon_Apr_13").resolve("22_29_24#27000#66");
            Files.createDirectories(sliceDir.resolve("Metadata"));
            Path gcodePath = sliceDir.resolve(".27000.0.gcode");
            Files.writeString(gcodePath, "; BambuStudio" + System.lineSeparator(), StandardCharsets.UTF_8);
            Thread.sleep(450L);
            Files.writeString(
                gcodePath,
                "; total filament weight [g] : 12.4" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
            );

            Assert.assertTrue("Timed out waiting for watcher callback.", latch.await(10L, TimeUnit.SECONDS));
            Assert.assertEquals(List.of(gcodePath.toAbsolutePath().normalize()), captured);
        }
    }

    @Test
    public void watcher_backfillsExistingRecentSliceOnStartup() throws Exception {
        Path root = Files.createTempDirectory("gcode-watch-backfill");
        Path sliceDir = root.resolve("Mon_Apr_13").resolve("22_29_24#27000#66");
        Files.createDirectories(sliceDir.resolve("Metadata"));
        Path gcodePath = sliceDir.resolve(".27000.0.gcode");
        Files.writeString(
            gcodePath,
            "; BambuStudio" + System.lineSeparator() + "; total filament weight [g] : 8.8" + System.lineSeparator(),
            StandardCharsets.UTF_8
        );

        List<Path> captured = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (GcodeWatchService watcher = new GcodeWatchService(
            List.of(root),
            line -> { },
            path -> {
                captured.add(path);
                latch.countDown();
            }
        )) {
            watcher.start();
            Assert.assertTrue("Timed out waiting for startup backfill.", latch.await(10L, TimeUnit.SECONDS));
            Assert.assertEquals(List.of(gcodePath.toAbsolutePath().normalize()), captured);
        }
    }

    @Test
    public void watcher_detectsSliceWhenRootAppearsAfterStart() throws Exception {
        Path parent = Files.createTempDirectory("gcode-watch-late-root");
        Path root = parent.resolve("bamboo_model");
        List<Path> captured = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (GcodeWatchService watcher = new GcodeWatchService(
            List.of(root),
            line -> { },
            path -> {
                captured.add(path);
                latch.countDown();
            }
        )) {
            watcher.start();
            Thread.sleep(500L);

            Path sliceDir = root.resolve("Mon_Apr_13").resolve("22_29_24#27000#66");
            Files.createDirectories(sliceDir.resolve("Metadata"));
            Path gcodePath = sliceDir.resolve(".27000.0.gcode");
            Files.writeString(
                gcodePath,
                "; BambuStudio" + System.lineSeparator() + "; total filament weight [g] : 11.2" + System.lineSeparator(),
                StandardCharsets.UTF_8
            );

            Assert.assertTrue("Timed out waiting for late-root watcher callback.", latch.await(10L, TimeUnit.SECONDS));
            Assert.assertEquals(List.of(gcodePath.toAbsolutePath().normalize()), captured);
        }
    }
}
