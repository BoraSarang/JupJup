package com.borasarang.planjupjup.worker

import com.borasarang.planjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineLogicTest {

    private fun source(
        id: String = "s1",
        enabled: Boolean = true,
        intervalMinutes: Int = 60,
        lastRunAt: Long? = null,
        type: String = "COMPARE_SITE",
    ) = CrawlSource(
        id = id,
        name = id,
        type = type,
        baseUrl = "https://example.com",
        enabled = enabled,
        intervalHours = intervalMinutes / 60,
        intervalMinutes = intervalMinutes,
        lastRunAt = lastRunAt,
        lastStatus = "NEVER_RUN",
        errorMessage = null,
        selectorConfigJson = null,
    )

    @Test
    fun isDue_neverRun_isDue() {
        assertTrue(PipelineLogic.isDue(source(lastRunAt = null), now = 1_000_000L))
    }

    @Test
    fun isDue_disabled_isNotDue() {
        assertFalse(PipelineLogic.isDue(source(enabled = false, lastRunAt = null), now = 1_000_000L))
    }

    @Test
    fun isDue_intervalElapsed_isDue() {
        val now = 10_000_000L
        val s = source(intervalMinutes = 30, lastRunAt = now - 31 * 60_000L)
        assertTrue(PipelineLogic.isDue(s, now))
    }

    @Test
    fun isDue_intervalNotElapsed_isNotDue() {
        val now = 10_000_000L
        val s = source(intervalMinutes = 30, lastRunAt = now - 10 * 60_000L)
        assertFalse(PipelineLogic.isDue(s, now))
    }

    @Test
    fun isDue_minFloor_15min() {
        val now = 10_000_000L
        // 설정 10분이라도 15분 하단
        val s = source(intervalMinutes = 10, lastRunAt = now - 14 * 60_000L)
        assertFalse(PipelineLogic.isDue(s, now))
        val s2 = source(intervalMinutes = 10, lastRunAt = now - 16 * 60_000L)
        assertTrue(PipelineLogic.isDue(s2, now))
    }

    @Test
    fun selectDue_oldestFirst_andLimit() {
        val now = 100_000_000L
        // interval 60분 = 3_600_000ms — 그 이상 경과분만 due
        val a = source(id = "a", lastRunAt = now - 70 * 60_000L)
        val b = source(id = "b", lastRunAt = now - 120 * 60_000L)
        val c = source(id = "c", lastRunAt = null)
        val d = source(id = "d", enabled = false, lastRunAt = null)
        val e = source(id = "e", intervalMinutes = 1440, lastRunAt = now - 60_000L)

        val due = PipelineLogic.selectDue(listOf(a, b, c, d, e), now, limit = 3)
        assertEquals(listOf("c", "b", "a"), due.map { it.id })
    }

    @Test
    fun selectDue_emptyWhenNoneDue() {
        val now = 10_000_000L
        val fresh = source(intervalMinutes = 1440, lastRunAt = now - 1000L)
        assertTrue(PipelineLogic.selectDue(listOf(fresh), now).isEmpty())
    }
}
