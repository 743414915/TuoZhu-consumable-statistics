package com.tuozhu.consumablestatistics.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopEndpointSupportTest {
    @Test
    fun normalizeDesktopBaseUrl_addsSchemeAndDefaultPort() {
        assertEquals(
            "http://192.168.8.241:8823",
            normalizeDesktopBaseUrl("192.168.8.241"),
        )
    }

    @Test
    fun normalizeDesktopBaseUrl_handlesChineseColonAndKeepsHttps() {
        assertEquals(
            "https://printer.tailnet.ts.net:8823",
            normalizeDesktopBaseUrl("https://printer.tailnet.ts.net：8823/"),
        )
    }

    @Test
    fun classifyDesktopBaseUrl_distinguishesAddressKinds() {
        assertEquals(DesktopEndpointKind.TAILSCALE, classifyDesktopBaseUrl("100.88.10.6:8823"))
        assertEquals(DesktopEndpointKind.MAGIC_DNS, classifyDesktopBaseUrl("printer.tailnet.ts.net"))
        assertEquals(DesktopEndpointKind.LAN, classifyDesktopBaseUrl("http://192.168.1.20"))
        assertEquals(DesktopEndpointKind.NONE, classifyDesktopBaseUrl(""))
    }

    @Test
    fun normalizeScannedDesktopBaseUrl_acceptsHostPortAndRejectsGarbage() {
        assertEquals(
            "http://192.168.8.241:8823",
            normalizeScannedDesktopBaseUrl("192.168.8.241:8823"),
        )
        assertEquals(
            "https://printer.tailnet.ts.net:8823",
            normalizeScannedDesktopBaseUrl("https://printer.tailnet.ts.net:8823"),
        )
        assertEquals(null, normalizeScannedDesktopBaseUrl("abcdef"))
        assertEquals(null, normalizeScannedDesktopBaseUrl("http://127.0.0.1:8823"))
    }

    @Test
    fun desktopEndpointKind_exposesConnectionScope() {
        assertEquals("跨网络可用", DesktopEndpointKind.TAILSCALE.connectionScopeLabel())
        assertEquals("跨网络可用", DesktopEndpointKind.MAGIC_DNS.connectionScopeLabel())
        assertEquals("仅同一 Wi‑Fi 可用", DesktopEndpointKind.LAN.connectionScopeLabel())
        assertEquals("尚未配置", DesktopEndpointKind.NONE.connectionScopeLabel())
    }
}
