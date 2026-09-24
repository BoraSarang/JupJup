package com.borasarang.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrawlStatsTest {

    @Test
    fun `5연속실패_참`() {
        assertTrue(CrawlStats.isFailureStreak(List(5) { "FAILED" }))
        assertTrue(CrawlStats.isFailureStreak(listOf("FAILED", "FAILED", "SUCCESS", "FAILED", "FAILED", "FAILED"), 2))
    }

    @Test
    fun `미달_거짓`() {
        assertFalse(CrawlStats.isFailureStreak(List(4) { "FAILED" }))
        assertFalse(CrawlStats.isFailureStreak(listOf("FAILED", "SUCCESS", "FAILED", "FAILED", "FAILED")))
        assertFalse(CrawlStats.isFailureStreak(emptyList()))
    }
}
