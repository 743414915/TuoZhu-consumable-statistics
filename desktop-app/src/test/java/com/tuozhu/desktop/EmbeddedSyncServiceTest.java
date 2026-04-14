package com.tuozhu.desktop;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class EmbeddedSyncServiceTest {
    @Test
    public void embeddedServer_servesHealthPullAndConfirm() throws Exception {
        Path agentRoot = Files.createTempDirectory("embedded-sync-agent");
        Files.createDirectories(agentRoot.resolve("outbox"));
        Files.createDirectories(agentRoot.resolve("state"));
        Files.createDirectories(agentRoot.resolve("inbox"));
        Files.writeString(agentRoot.resolve("run-sync-agent.ps1"), fakeSyncScript(), StandardCharsets.UTF_8);

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        EmbeddedSyncService service = new EmbeddedSyncService(
            () -> new EmbeddedSyncService.Config(agentRoot, port, 7, false, List.of()),
            line -> { }
        );

        try {
            service.start();
            waitForSyncArtifacts(agentRoot);

            String health = read("http://127.0.0.1:" + port + "/health");
            Assert.assertTrue(health.contains("\"status\":\"ok\""));

            String pull = read("http://127.0.0.1:" + port + "/api/sync/pull");
            Assert.assertTrue(pull.contains("\"externalJobId\":\"job-1\""));
            Assert.assertTrue(pull.contains("\"warnings\""));

            String confirm = post(
                "http://127.0.0.1:" + port + "/api/sync/confirm",
                "{\"externalJobId\":\"job-1\",\"confirmedAt\":1776072000000,\"targetRollId\":3}"
            );
            Assert.assertTrue(confirm.contains("\"status\":\"SUCCESS\""));

            String confirmationLog = Files.readString(
                agentRoot.resolve("outbox").resolve("confirmation-log.json"),
                StandardCharsets.UTF_8
            );
            Assert.assertTrue(confirmationLog.contains("\"externalJobId\":\"job-1\""));
            Assert.assertTrue(confirmationLog.contains("\"targetRollId\":3"));
        } finally {
            service.stop();
        }
    }

    private static void waitForSyncArtifacts(Path agentRoot) throws Exception {
        Path outbox = agentRoot.resolve("outbox").resolve("desktop-outbox.json");
        Path state = agentRoot.resolve("state").resolve("state.json");
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            boolean ready = Files.exists(outbox)
                && Files.exists(state)
                && Files.readString(outbox, StandardCharsets.UTF_8).contains("\"externalJobId\":\"job-1\"");
            if (ready) {
                return;
            }
            Thread.sleep(200L);
        }
        Assert.fail("Timed out waiting for sync artifacts.");
    }

    private static String read(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static String post(String url, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static String fakeSyncScript() {
        return """
            param(
                [string]$OutboxPath = "",
                [string]$StatePath = "",
                [string]$ConfirmationPath = "",
                [string]$GeneratedInboxPath = "",
                [string[]]$GcodePaths = @(),
                [int]$MaxFileAgeDays = 7,
                [string[]]$GcodeSearchRoots = @(),
                [switch]$UseSample,
                [switch]$UseBambuGcode
            )

            $ErrorActionPreference = "Stop"
            if (-not $OutboxPath) { $OutboxPath = Join-Path $PSScriptRoot "outbox\\desktop-outbox.json" }
            if (-not $StatePath) { $StatePath = Join-Path $PSScriptRoot "state\\state.json" }
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutboxPath) | Out-Null
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $StatePath) | Out-Null
            Set-Content -LiteralPath $OutboxPath -Encoding UTF8 -Value '[{"externalJobId":"job-1","source":"DESKTOP_AGENT","modelName":"Benchy","estimatedUsageGrams":43,"targetMaterial":"PETG Basic","note":"来自桌面","createdAt":1776071000000}]'
            Set-Content -LiteralPath $StatePath -Encoding UTF8 -Value '{"updatedAt":1776071000000,"warnings":["桌面测试告警"],"jobs":[],"inputMode":"bambu-gcode"}'
            Write-Output "fake sync done"
            """;
    }
}
