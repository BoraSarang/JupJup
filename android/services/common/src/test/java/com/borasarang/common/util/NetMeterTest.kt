package com.borasarang.common.util

import com.borasarang.common.util.net.NetMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetMeterTest {

    @Test
    fun `가산_스냅샷`() {
        NetMeter.reset()
        NetMeter.record("mac", 1000, 200)
        NetMeter.record("mac", 500, 100)
        NetMeter.record("plan", 300, 50)
        val s = NetMeter.snapshot()
        assertEquals(1500L, s["mac"]?.rxBytes)
        assertEquals(300L, s["mac"]?.txBytes)
        assertEquals(2L, s["mac"]?.requests)
        assertEquals(300L, s["plan"]?.rxBytes)
        val t = NetMeter.total()
        assertEquals(1800L, t.rxBytes)
        assertEquals(350L, t.txBytes)
        assertEquals(3L, t.requests)
        NetMeter.reset()
    }

    @Test
    fun `음수_0무시`() {
        NetMeter.reset()
        NetMeter.record("mac", -1, 10)
        NetMeter.record("mac", 0, 0)
        assertTrue(NetMeter.snapshot().isEmpty())
        NetMeter.reset()
    }

    @Test
    fun `리셋_서비스별`() {
        NetMeter.reset()
        NetMeter.record("mac", 10, 10)
        NetMeter.record("plan", 20, 20)
        NetMeter.reset("mac")
        val s = NetMeter.snapshot()
        assertTrue(s["mac"] == null)
        assertEquals(20L, s["plan"]?.rxBytes)
        NetMeter.reset()
    }

    @Test
    fun `포맷`() {
        assertEquals("512B", NetMeter.formatBytes(512))
        assertEquals("1.0KB", NetMeter.formatBytes(1024))
        assertEquals("1.5MB", NetMeter.formatBytes(1572864))
    }
}
