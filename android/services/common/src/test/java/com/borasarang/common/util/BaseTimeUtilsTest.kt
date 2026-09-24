package com.borasarang.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseTimeUtilsTest {

    private val tu = object : BaseTimeUtils() {}

    @Test
    fun `상대시각_경계`() {
        assertEquals("없음", tu.formatRelative(null))
        assertEquals("없음", tu.formatRelative(0L))
        assertEquals("방금 전", tu.formatRelative(System.currentTimeMillis()))
        assertEquals("5분 전", tu.formatRelative(System.currentTimeMillis() - 5 * 60_000L))
        assertEquals("3시간 전", tu.formatRelative(System.currentTimeMillis() - 3 * 3_600_000L))
    }

    @Test
    fun `절대시각_소요시간`() {
        assertEquals("없음", tu.formatAbsolute(null))
        assertTrue(tu.formatAbsolute(System.currentTimeMillis()).matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        assertEquals("소요 30초", tu.formatDuration(1_000L, 31_000L))
        assertEquals("소요 2분 5초", tu.formatDuration(1L, 126_000L))
        assertNull(tu.formatDuration(null, 1L))
        assertNull(tu.formatDuration(2L, 1L))
    }

    @Test
    fun `주기포맷_월간포함`() {
        assertEquals("미설정", tu.formatInterval(0))
        assertEquals("30분마다", tu.formatInterval(30))
        assertEquals("월 1회", tu.formatInterval(43200))
        assertEquals("주 1회", tu.formatInterval(10080))
        assertEquals("24시간마다", tu.formatInterval(1440))
    }

    @Test
    fun `오늘자정_범위`() {
        val now = System.currentTimeMillis()
        val midnight = tu.startOfToday()
        assertTrue(midnight in now - 86_400_000L..now)
        assertTrue(tu.millisUntilNextHour(9) in 1..86_400_000L)
    }

    @Test
    fun `millisUntilTime_HHMM`() {
        val d = tu.millisUntilTime("09:30")
        assertTrue(d in 0..86_400_000L)
        val bad = tu.millisUntilTime("bad", fallbackMillis = 3_600_000L)
        assertEquals(3_600_000L, bad)
    }
}
