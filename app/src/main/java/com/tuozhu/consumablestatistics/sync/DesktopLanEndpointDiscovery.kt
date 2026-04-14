package com.tuozhu.consumablestatistics.sync

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

sealed interface DesktopLanDiscoveryResult {
    data class Success(val baseUrl: String) : DesktopLanDiscoveryResult

    data class Unsupported(val message: String) : DesktopLanDiscoveryResult

    data class NotFound(val message: String) : DesktopLanDiscoveryResult
}

class DesktopLanEndpointDiscovery(
    private val currentIpv4Provider: () -> String? = ::findCurrentIpv4Address,
    private val probeHost: suspend (String, Int) -> Boolean = ::probeDesktopSyncHealth,
) {
    suspend fun discover(port: Int = DEFAULT_DESKTOP_SYNC_PORT): DesktopLanDiscoveryResult = withContext(Dispatchers.IO) {
        val currentIp = currentIpv4Provider()
            ?: return@withContext DesktopLanDiscoveryResult.Unsupported("未检测到可扫描的局域网网络，请直接填写 Tailscale 地址")

        if (!isPrivateLanIpv4Address(currentIp)) {
            return@withContext DesktopLanDiscoveryResult.Unsupported("当前网络不是普通局域网。自动发现仅支持同一 Wi‑Fi；使用 Tailscale 时请直接粘贴桌面推荐地址")
        }

        val prefix = currentIp.substringBeforeLast('.', missingDelimiterValue = "")
        val currentHost = currentIp.substringAfterLast('.', missingDelimiterValue = "")
        if (prefix.isBlank()) {
            return@withContext DesktopLanDiscoveryResult.Unsupported("当前网络地址不可用，请直接填写桌面同步地址")
        }

        val candidates = (1..254)
            .map { "$prefix.$it" }
            .filterNot { it.endsWith(".$currentHost") }

        for (batch in candidates.chunked(24)) {
            val found = coroutineScope {
                batch.map { host ->
                    async {
                        if (probeHost(host, port)) {
                            host
                        } else {
                            null
                        }
                    }
                }.awaitAll().firstOrNull { it != null }
            }
            if (found != null) {
                return@withContext DesktopLanDiscoveryResult.Success("http://$found:$port")
            }
        }

        DesktopLanDiscoveryResult.NotFound("未在同一 Wi‑Fi 下发现桌面同步服务，请先确认电脑端已启动服务，或改用 Tailscale 地址")
    }
}

private fun findCurrentIpv4Address(): String? {
    val candidates = mutableListOf<String>()
    val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
    while (interfaces.hasMoreElements()) {
        val network = interfaces.nextElement()
        if (!network.isUp || network.isLoopback || network.isVirtual) {
            continue
        }
        val addresses = network.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            val host = address.hostAddress ?: continue
            if (host.contains(':') || address.isLoopbackAddress || address.isLinkLocalAddress) {
                continue
            }
            candidates += host
        }
    }
    return candidates.firstOrNull(::isPrivateLanIpv4Address) ?: candidates.firstOrNull()
}

private suspend fun probeDesktopSyncHealth(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    val url = URL("http://$host:$port/health")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 450
        readTimeout = 650
        useCaches = false
        setRequestProperty("Accept", "application/json")
    }

    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            return@withContext false
        }
        val body = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        body.contains("\"status\":\"ok\"") && body.contains("\"source\":\"DESKTOP_AGENT\"")
    } catch (_: Exception) {
        false
    } finally {
        connection.disconnect()
    }
}

private fun isPrivateLanIpv4Address(ip: String): Boolean {
    val parts = ip.split('.')
    if (parts.size != 4) {
        return false
    }
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) {
        return false
    }
    val first = octets[0]
    val second = octets[1]
    return first == 10 ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 168)
}
