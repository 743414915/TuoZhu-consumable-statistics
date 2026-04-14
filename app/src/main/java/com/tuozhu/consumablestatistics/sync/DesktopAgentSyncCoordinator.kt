package com.tuozhu.consumablestatistics.sync

import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopAgentSyncCoordinator(
    private val settingsProvider: () -> SyncSettings,
) : SyncCoordinator {
    constructor(settingsStore: SyncSettingsStore) : this(settingsProvider = settingsStore::currentSettings)

    override suspend fun pull(): SyncPullResult = withContext(Dispatchers.IO) {
        val baseUrl = settingsProvider().desktopBaseUrl
        if (baseUrl.isBlank()) {
            return@withContext SyncPullResult(
                status = SyncConnectionStatus.OFFLINE,
                source = SyncSourceType.DESKTOP_AGENT,
                syncedAt = System.currentTimeMillis(),
                message = "\u8bf7\u5148\u586b\u5199\u684c\u9762\u540c\u6b65\u5730\u5740",
            )
        }

        return@withContext try {
            val responseText = request(
                url = "$baseUrl/api/sync/pull",
                method = "GET",
            )
            parsePullResult(responseText)
        } catch (timeout: SocketTimeoutException) {
            offlineResult("\u8fde\u63a5\u684c\u9762\u540c\u6b65\u670d\u52a1\u8d85\u65f6\uff0c\u8bf7\u68c0\u67e5\u7535\u8111\u7aef\u670d\u52a1\u662f\u5426\u5728\u8fd0\u884c")
        } catch (exception: Exception) {
            exception.printStackTrace()
            offlineResult(mapPullException(baseUrl, exception))
        }
    }

    override suspend fun pushConfirmation(receipt: SyncConfirmationReceipt): SyncPushResult = withContext(Dispatchers.IO) {
        val baseUrl = settingsProvider().desktopBaseUrl
        if (baseUrl.isBlank()) {
            return@withContext SyncPushResult(
                status = SyncConnectionStatus.OFFLINE,
                source = SyncSourceType.DESKTOP_AGENT,
                syncedAt = System.currentTimeMillis(),
                message = "\u684c\u9762\u5730\u5740\u672a\u914d\u7f6e\uff0c\u786e\u8ba4\u56de\u6267\u6682\u672a\u53d1\u9001",
            )
        }

        return@withContext try {
            val responseText = request(
                url = "$baseUrl/api/sync/confirm",
                method = "POST",
                requestBody = buildConfirmationJson(receipt),
            )
            parsePushResult(responseText)
        } catch (timeout: SocketTimeoutException) {
            SyncPushResult(
                status = SyncConnectionStatus.OFFLINE,
                source = SyncSourceType.DESKTOP_AGENT,
                syncedAt = System.currentTimeMillis(),
                message = "\u672c\u5730\u5df2\u786e\u8ba4\uff0c\u4f46\u684c\u9762\u7aef\u786e\u8ba4\u56de\u6267\u8d85\u65f6",
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            SyncPushResult(
                status = SyncConnectionStatus.OFFLINE,
                source = SyncSourceType.DESKTOP_AGENT,
                syncedAt = System.currentTimeMillis(),
                message = "\u672c\u5730\u5df2\u786e\u8ba4\uff0c\u4f46\u684c\u9762\u7aef\u786e\u8ba4\u56de\u6267\u5931\u8d25\uff1a${mapPushException(baseUrl, exception)}",
            )
        }
    }

    private fun request(
        url: String,
        method: String,
        requestBody: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (requestBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (requestBody != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                }
            }

            val code = connection.responseCode
            val payload = readPayload(connection, code)
            if (code !in 200..299) {
                throw HttpStatusException(code, extractErrorMessage(payload))
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }

    private fun readPayload(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return readStream(stream)
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun parsePullResult(payload: String): SyncPullResult {
        val warnings = extractStringArray(payload, "warnings")
        val message = buildString {
            append(extractStringField(payload, "message") ?: "\u684c\u9762\u540c\u6b65\u5b8c\u6210")
            if (warnings.isNotEmpty()) {
                append(" ")
                append(formatDesktopWarnings(warnings))
            }
        }

        return SyncPullResult(
            status = extractEnum(payload, "status", SyncConnectionStatus.SUCCESS),
            source = extractEnum(payload, "source", SyncSourceType.DESKTOP_AGENT),
            syncedAt = extractLongField(payload, "syncedAt") ?: System.currentTimeMillis(),
            message = message,
            draftJobs = extractObjectArray(payload, "draftJobs").map(::parseDraftJob),
        )
    }

    private fun formatDesktopWarnings(warnings: List<String>): String {
        val firstWarning = warnings.first().trim()
        return if (warnings.size == 1) {
            "\u684c\u9762\u544a\u8b66\uff1a$firstWarning"
        } else {
            "\u684c\u9762\u544a\u8b66 ${warnings.size} \u6761\uff0c\u9996\u6761\uff1a$firstWarning"
        }
    }

    private fun parsePushResult(payload: String): SyncPushResult {
        return SyncPushResult(
            status = extractEnum(payload, "status", SyncConnectionStatus.SUCCESS),
            source = extractEnum(payload, "source", SyncSourceType.DESKTOP_AGENT),
            syncedAt = extractLongField(payload, "syncedAt") ?: System.currentTimeMillis(),
            message = extractStringField(payload, "message") ?: "\u786e\u8ba4\u56de\u6267\u5df2\u53d1\u9001",
        )
    }

    private fun parseDraftJob(objectJson: String): SyncDraftJob {
        return SyncDraftJob(
            externalJobId = extractStringField(objectJson, "externalJobId")
                ?: error("externalJobId is required"),
            source = extractEnum(objectJson, "source", SyncSourceType.DESKTOP_AGENT),
            modelName = extractStringField(objectJson, "modelName")
                ?: error("modelName is required"),
            estimatedUsageGrams = extractLongField(objectJson, "estimatedUsageGrams")?.toInt()
                ?: error("estimatedUsageGrams is required"),
            targetMaterial = extractStringField(objectJson, "targetMaterial"),
            note = extractStringField(objectJson, "note").orEmpty(),
            createdAt = extractLongField(objectJson, "createdAt")
                ?: error("createdAt is required"),
        )
    }

    private fun buildConfirmationJson(receipt: SyncConfirmationReceipt): String {
        return buildString {
            append("{")
            append("\"externalJobId\":\"")
            append(escapeJson(receipt.externalJobId))
            append("\",")
            append("\"confirmedAt\":")
            append(receipt.confirmedAt)
            receipt.targetRollId?.let {
                append(",\"targetRollId\":")
                append(it)
            }
            append("}")
        }
    }

    private fun mapPullException(baseUrl: String, exception: Exception): String {
        return when (exception) {
            is UnknownHostException -> "无法解析桌面地址，请检查填写的 Tailscale IP、MagicDNS 域名或局域网 IP 是否正确"
            is ConnectException -> {
                if (isLoopbackHost(baseUrl)) {
                    "手机端不能使用 localhost 或 127.0.0.1，请填写桌面端推荐的 Tailscale 地址；未使用 Tailscale 时，再填写电脑在同一 Wi‑Fi 下的局域网 IP，例如 http://192.168.1.8:8823"
                } else {
                    "无法连接到桌面服务，请先确认电脑端桌面同步程序已启动服务；优先使用桌面端给出的 Tailscale 地址，未使用 Tailscale 时再检查同一 Wi‑Fi 下的局域网地址和 8823 端口"
                }
            }
            is MalformedURLException -> "桌面地址格式不正确，请使用 100.x.x.x:8823、电脑名.tailnet.ts.net:8823 或 192.168.x.x:8823"
            is SSLException -> "桌面端 HTTPS 连接失败，请检查证书，或先改用 HTTP 的 Tailscale / 局域网地址"
            is UnknownServiceException -> mapUnknownServiceException(exception)
            is HttpStatusException -> "桌面服务返回错误（HTTP ${exception.statusCode}）：${safeDetail(exception.message)}"
            is SocketException -> "网络连接异常：${safeDetail(exception.message)}"
            else -> "无法连接桌面同步服务：${formatException(exception)}"
        }
    }

    private fun mapPushException(baseUrl: String, exception: Exception): String {
        return when (exception) {
            is UnknownHostException -> "无法解析桌面地址"
            is ConnectException -> {
                if (isLoopbackHost(baseUrl)) {
                    "手机端不能使用 localhost 或 127.0.0.1"
                } else {
                    "无法连接到桌面服务，请确认桌面同步程序仍在运行"
                }
            }
            is MalformedURLException -> "桌面地址格式不正确"
            is SSLException -> "桌面端 HTTPS 连接失败"
            is UnknownServiceException -> mapUnknownServiceException(exception)
            is HttpStatusException -> "桌面服务返回错误（HTTP ${exception.statusCode}）：${safeDetail(exception.message)}"
            is SocketException -> "网络连接异常：${safeDetail(exception.message)}"
            else -> formatException(exception)
        }
    }

    private fun mapUnknownServiceException(exception: UnknownServiceException): String {
        val detail = exception.message.orEmpty()
        return if (
            detail.contains("CLEARTEXT", ignoreCase = true) ||
            detail.contains("cleartext", ignoreCase = true)
        ) {
            "当前系统拒绝 HTTP 明文请求，请检查地址协议或安全配置"
        } else {
            "桌面服务连接方式不受支持：${safeDetail(detail)}"
        }
    }

    private fun isLoopbackHost(baseUrl: String): Boolean {
        return runCatching { URL(baseUrl).host.lowercase() }
            .getOrNull()
            ?.let { host ->
                host == "localhost" ||
                    host == "0.0.0.0" ||
                    host == "::1" ||
                    host.startsWith("127.")
            }
            ?: false
    }

    private fun formatException(exception: Exception): String {
        val type = exception::class.simpleName ?: "Exception"
        val detail = safeDetail(exception.message)
        return if (detail == UNKNOWN_DETAIL) {
            type
        } else {
            "$type: $detail"
        }
    }

    private fun safeDetail(message: String?): String {
        val normalized = message
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            .orEmpty()
        return if (normalized.isBlank()) {
            UNKNOWN_DETAIL
        } else {
            normalized.take(160)
        }
    }

    private fun extractStringField(payload: String, fieldName: String): String? {
        val pattern = Regex("\"$fieldName\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(payload) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun extractLongField(payload: String, fieldName: String): Long? {
        val pattern = Regex("\"$fieldName\"\\s*:\\s*(-?\\d+)")
        return pattern.find(payload)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun extractStringArray(payload: String, fieldName: String): List<String> {
        val content = extractArrayContent(payload, fieldName) ?: return emptyList()
        val pattern = Regex("\"((?:\\\\.|[^\"])*)\"")
        return pattern.findAll(content).map { unescapeJson(it.groupValues[1]) }.toList()
    }

    private fun extractObjectArray(payload: String, fieldName: String): List<String> {
        val content = extractArrayContent(payload, fieldName) ?: return emptyList()
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        content.forEachIndexed { index, char ->
            when (char) {
                '{' -> {
                    if (depth == 0) {
                        start = index
                    }
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        objects += content.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractArrayContent(payload: String, fieldName: String): String? {
        val fieldIndex = payload.indexOf("\"$fieldName\"")
        if (fieldIndex < 0) {
            return null
        }
        val start = payload.indexOf('[', startIndex = fieldIndex)
        if (start < 0) {
            return null
        }
        var depth = 0
        for (index in start until payload.length) {
            when (payload[index]) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return payload.substring(start + 1, index)
                    }
                }
            }
        }
        return null
    }

    private inline fun <reified T : Enum<T>> extractEnum(
        payload: String,
        fieldName: String,
        fallback: T,
    ): T {
        return extractStringField(payload, fieldName)
            ?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
            ?: fallback
    }

    private fun extractErrorMessage(payload: String): String {
        val message = extractStringField(payload, "message") ?: payload.take(120)
        return if (message.isBlank()) {
            "\u670d\u52a1\u672a\u8fd4\u56de\u5177\u4f53\u9519\u8bef\u4fe1\u606f"
        } else {
            message
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJson(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current != '\\' || index == value.lastIndex) {
                builder.append(current)
                index += 1
                continue
            }
            when (val next = value[index + 1]) {
                '"', '\\', '/' -> {
                    builder.append(next)
                    index += 2
                }
                'b' -> {
                    builder.append('\b')
                    index += 2
                }
                'f' -> {
                    builder.append('\u000C')
                    index += 2
                }
                'n' -> {
                    builder.append('\n')
                    index += 2
                }
                'r' -> {
                    builder.append('\r')
                    index += 2
                }
                't' -> {
                    builder.append('\t')
                    index += 2
                }
                'u' -> {
                    if (index + 5 < value.length) {
                        val hex = value.substring(index + 2, index + 6)
                        val decoded = hex.toIntOrNull(16)?.toChar()
                        if (decoded != null) {
                            builder.append(decoded)
                            index += 6
                        } else {
                            builder.append("\\u")
                            index += 2
                        }
                    } else {
                        builder.append("\\u")
                        index += 2
                    }
                }
                else -> {
                    builder.append(next)
                    index += 2
                }
            }
        }
        return builder.toString()
    }

    private fun offlineResult(message: String): SyncPullResult {
        return SyncPullResult(
            status = SyncConnectionStatus.OFFLINE,
            source = SyncSourceType.DESKTOP_AGENT,
            syncedAt = System.currentTimeMillis(),
            message = message,
        )
    }

    private class HttpStatusException(
        val statusCode: Int,
        detail: String,
    ) : IllegalStateException(detail)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 15_000
        const val UNKNOWN_DETAIL = "\u672a\u77e5\u9519\u8bef"
    }
}
