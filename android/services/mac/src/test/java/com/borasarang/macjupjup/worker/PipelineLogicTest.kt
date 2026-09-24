package com.borasarang.macjupjup.worker

import com.borasarang.macjupjup.data.db.entity.CrawlSource
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
        type: String = "GITHUB_SEARCH",
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
        val s = source(intervalMinutes = 60, lastRunAt = now - 61 * 60_000L)
        assertTrue(PipelineLogic.isDue(s, now))
    }

    @Test
    fun isDue_intervalNotElapsed_isNotDue() {
        val now = 10_000_000L
        val s = source(intervalMinutes = 60, lastRunAt = now - 30 * 60_000L)
        assertFalse(PipelineLogic.isDue(s, now))
    }

    @Test
    fun isDue_newsFloor_30min() {
        val now = 10_000_000L
        // 설정 15분이어도 뉴스는 30분 하단
        val s = source(intervalMinutes = 15, lastRunAt = now - 20 * 60_000L, type = "NEWS_RSS")
        assertFalse(PipelineLogic.isDue(s, now))
        val s2 = source(intervalMinutes = 15, lastRunAt = now - 31 * 60_000L, type = "NEWS_RSS")
        assertTrue(PipelineLogic.isDue(s2, now))
    }

    @Test
    fun isDue_communityFloor_60min() {
        val now = 10_000_000L
        val s = source(intervalMinutes = 15, lastRunAt = now - 45 * 60_000L, type = "COMMUNITY_BOARD")
        assertFalse(PipelineLogic.isDue(s, now))
        val s2 = source(intervalMinutes = 15, lastRunAt = now - 61 * 60_000L, type = "COMMUNITY_BOARD")
        assertTrue(PipelineLogic.isDue(s2, now))
    }

    @Test
    fun floorMinutes_byType() {
        assertEquals(30, PipelineLogic.floorMinutes("NEWS_RSS"))
        assertEquals(60, PipelineLogic.floorMinutes("COMMUNITY_BOARD"))
        assertEquals(15, PipelineLogic.floorMinutes("GITHUB_SEARCH"))
        assertEquals(15, PipelineLogic.floorMinutes(null))
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
        // null lastRunAt(0)最先, 그다음 오래된 순, 비활성·미到期 제외, 상한 3
        assertEquals(listOf("c", "b", "a"), due.map { it.id })
    }

    @Test
    fun selectDue_emptyWhenNoneDue() {
        val now = 10_000_000L
        val fresh = source(intervalMinutes = 1440, lastRunAt = now - 1000L)
        assertTrue(PipelineLogic.selectDue(listOf(fresh), now).isEmpty())
    }
}
