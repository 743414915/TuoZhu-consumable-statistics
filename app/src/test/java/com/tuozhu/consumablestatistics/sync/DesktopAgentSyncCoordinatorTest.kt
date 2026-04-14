package com.tuozhu.consumablestatistics.sync

import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopAgentSyncCoordinatorTest {
    @Test
    fun pull_readsDraftJobsFromDesktopServer() = runTest {
        val responseBody = """
            {
              "status": "SUCCESS",
              "source": "DESKTOP_AGENT",
              "syncedAt": 1775806800000,
              "message": "\u684c\u9762\u540c\u6b65\u5b8c\u6210",
              "warnings": ["w1"],
              "draftJobs": [
                {
                  "externalJobId": "job-1",
                  "source": "DESKTOP_AGENT",
                  "modelName": "Benchy",
                  "estimatedUsageGrams": 43,
                  "targetMaterial": "PETG Basic",
                  "note": "\u6765\u81ea\u684c\u9762",
                  "createdAt": 1775806700000
                }
              ]
            }
        """.trimIndent()
        val server = OneShotHttpServer(responseBody = responseBody)
        server.start()

        val coordinator = DesktopAgentSyncCoordinator {
            SyncSettings(desktopBaseUrl = "http://127.0.0.1:${server.port}")
        }

        val result = coordinator.pull()

        server.awaitHandled()
        server.close()

        assertEquals(SyncConnectionStatus.SUCCESS, result.status)
        assertEquals(SyncSourceType.DESKTOP_AGENT, result.source)
        assertEquals(1, result.draftJobs.size)
        assertEquals("job-1", result.draftJobs.single().externalJobId)
        assertEquals("来自桌面", result.draftJobs.single().note)
        assertTrue(result.message.contains("桌面告警：w1"))
        assertTrue(server.lastRequestLine!!.startsWith("GET /api/sync/pull"))
    }

    @Test
    fun pushConfirmation_postsReceiptToDesktopServer() = runTest {
        val server = OneShotHttpServer(
            responseBody = """
                {
                  "status": "SUCCESS",
                  "source": "DESKTOP_AGENT",
                  "syncedAt": 1775806800000,
                  "message": "\u786e\u8ba4\u56de\u6267\u5df2\u8bb0\u5f55"
                }
            """.trimIndent(),
        )
        server.start()

        val coordinator = DesktopAgentSyncCoordinator {
            SyncSettings(desktopBaseUrl = "http://127.0.0.1:${server.port}")
        }

        val result = coordinator.pushConfirmation(
            SyncConfirmationReceipt(
                externalJobId = "job-1",
                confirmedAt = 1775806800000,
                targetRollId = 7,
            ),
        )

        server.awaitHandled()
        server.close()

        assertEquals(SyncConnectionStatus.SUCCESS, result.status)
        assertEquals("确认回执已记录", result.message)
        assertTrue(server.lastRequestLine!!.startsWith("POST /api/sync/confirm"))
        assertTrue(server.lastRequestBody!!.contains("\"externalJobId\":\"job-1\""))
        assertTrue(server.lastRequestBody!!.contains("\"targetRollId\":7"))
    }

    @Test
    fun pull_returnsActionableMessageWhenLoopbackHostIsUnavailable() = runTest {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val coordinator = DesktopAgentSyncCoordinator {
            SyncSettings(desktopBaseUrl = "http://127.0.0.1:$unusedPort")
        }

        val result = coordinator.pull()

        assertEquals(SyncConnectionStatus.OFFLINE, result.status)
        assertTrue(result.message.contains("127.0.0.1"))
        assertTrue(result.message.contains("Tailscale"))
    }
}

private class OneShotHttpServer(
    private val responseBody: String,
) : AutoCloseable {
    private val serverSocket = ServerSocket(0)
    private val handledLatch = CountDownLatch(1)
    private var thread: Thread? = null

    val port: Int
        get() = serverSocket.localPort

    @Volatile
    var lastRequestLine: String? = null

    @Volatile
    var lastRequestBody: String? = null

    fun start() {
        thread = Thread {
            serverSocket.use { socketServer ->
                val socket = socketServer.accept()
                handle(socket)
            }
        }.apply { start() }
    }

    fun awaitHandled() {
        assertTrue("server request timed out", handledLatch.await(5, TimeUnit.SECONDS))
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
            lastRequestLine = reader.readLine()
            var contentLength = 0
            while (true) {
                val line = reader.readLine()
                if (line.isNullOrEmpty()) {
                    break
                }
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toInt()
                }
            }
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars)
                lastRequestBody = String(bodyChars)
            } else {
                lastRequestBody = ""
            }

            val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
            val writer = client.getOutputStream().bufferedWriter(Charsets.UTF_8)
            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Content-Length: ${responseBytes.size}\r\n")
            writer.write("\r\n")
            writer.flush()
            client.getOutputStream().write(responseBytes)
            client.getOutputStream().flush()
            handledLatch.countDown()
        }
    }

    override fun close() {
        runCatching { serverSocket.close() }
        thread?.join(1_000)
    }
}
