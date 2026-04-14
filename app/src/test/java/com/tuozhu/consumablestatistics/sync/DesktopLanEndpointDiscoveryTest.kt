package com.tuozhu.consumablestatistics.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLanEndpointDiscoveryTest {
    @Test
    fun discover_returnsUnsupportedOutsideLan() = runTest {
        val discovery = DesktopLanEndpointDiscovery(
            currentIpv4Provider = { "100.101.102.103" },
            probeHost = { _, _ -> false },
        )

        val result = discovery.discover()

        assertTrue(result is DesktopLanDiscoveryResult.Unsupported)
    }

    @Test
    fun discover_returnsFirstMatchingLanHost() = runTest {
        val discovery = DesktopLanEndpointDiscovery(
            currentIpv4Provider = { "192.168.8.15" },
            probeHost = { host, _ -> host == "192.168.8.241" },
        )

        val result = discovery.discover()

        assertEquals(
            DesktopLanDiscoveryResult.Success("http://192.168.8.241:8823"),
            result,
        )
    }
}
