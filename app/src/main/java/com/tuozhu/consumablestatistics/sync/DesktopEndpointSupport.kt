package com.tuozhu.consumablestatistics.sync

import java.net.URL

const val DEFAULT_DESKTOP_SYNC_PORT = 8823

enum class DesktopEndpointKind {
    NONE,
    TAILSCALE,
    MAGIC_DNS,
    LAN,
    CUSTOM,
}

fun DesktopEndpointKind.connectionScopeLabel(): String = when (this) {
    DesktopEndpointKind.TAILSCALE,
    DesktopEndpointKind.MAGIC_DNS -> "跨网络可用"
    DesktopEndpointKind.LAN -> "仅同一 Wi‑Fi 可用"
    DesktopEndpointKind.CUSTOM -> "按实际地址判断"
    DesktopEndpointKind.NONE -> "尚未配置"
}

fun DesktopEndpointKind.connectionScopeHint(): String = when (this) {
    DesktopEndpointKind.TAILSCALE ->
        "当前保存的是 Tailscale 地址。手机和电脑只要都在线并加入同一个 tailnet，就不需要再依赖局域网。"
    DesktopEndpointKind.MAGIC_DNS ->
        "当前保存的是 MagicDNS 地址。它和 Tailscale 一样支持跨网络访问，但更适合长期固定使用。"
    DesktopEndpointKind.LAN ->
        "当前保存的是局域网地址。只有手机和电脑在同一 Wi‑Fi 或同一内网时才能连接。"
    DesktopEndpointKind.CUSTOM ->
        "当前保存的是自定义地址，请按它自己的网络范围使用。若希望跨网络更稳定，优先改成 Tailscale 或 MagicDNS。"
    DesktopEndpointKind.NONE ->
        "还没有绑定桌面同步地址。若已接入 Tailscale，优先填写桌面端提供的 Tailscale 或 MagicDNS 地址。"
}

fun normalizeDesktopBaseUrl(rawValue: String): String {
    val cleaned = rawValue
        .trim()
        .replace('：', ':')
        .trimEnd('/')
    if (cleaned.isEmpty()) {
        return ""
    }

    val withScheme = if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
        cleaned
    } else {
        "http://$cleaned"
    }

    return runCatching {
        val parsed = URL(withScheme)
        val protocol = parsed.protocol.lowercase()
        val host = parsed.host?.takeIf { it.isNotBlank() } ?: return@runCatching withScheme
        val port = if (parsed.port == -1) DEFAULT_DESKTOP_SYNC_PORT else parsed.port
        val path = parsed.path?.takeIf { it.isNotBlank() && it != "/" }.orEmpty()
        val query = parsed.query?.let { "?$it" }.orEmpty()
        "$protocol://$host:$port$path$query"
    }.getOrElse { withScheme }
}

fun classifyDesktopBaseUrl(baseUrl: String): DesktopEndpointKind {
    if (baseUrl.isBlank()) {
        return DesktopEndpointKind.NONE
    }
    val host = runCatching { URL(normalizeDesktopBaseUrl(baseUrl)).host.lowercase() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return DesktopEndpointKind.CUSTOM

    return when {
        isTailscaleIp(host) -> DesktopEndpointKind.TAILSCALE
        host.endsWith(".tailnet.ts.net") -> DesktopEndpointKind.MAGIC_DNS
        isPrivateLanIpv4(host) -> DesktopEndpointKind.LAN
        else -> DesktopEndpointKind.CUSTOM
    }
}

fun normalizeScannedDesktopBaseUrl(rawValue: String?): String? {
    val value = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (value.contains(' ')) {
        return null
    }
    if (!value.contains("://") && !value.contains(':')) {
        return null
    }
    val normalized = normalizeDesktopBaseUrl(value)
    val host = runCatching { URL(normalized).host.lowercase() }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    if (host == "localhost" || host == "0.0.0.0" || host == "::1" || host.startsWith("127.")) {
        return null
    }
    return normalized
}

private fun isPrivateLanIpv4(host: String): Boolean {
    val parts = host.split('.')
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

private fun isTailscaleIp(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) {
        return false
    }
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) {
        return false
    }
    return octets[0] == 100 && octets[1] in 64..127
}
