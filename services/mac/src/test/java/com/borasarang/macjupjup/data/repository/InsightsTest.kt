package com.borasarang.macjupjup.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {

    private fun sample() = InsightsInput(
        totalApps = 2000,
        newLast7d = 1900,
        bumpsLast7d = 12,
        topCategory = "유틸리티" to 800,
        topCategorySharePct = 40,
        bestSource = Triple("MAS 키워드 발견", 1994, 1829),
        bestDay = "2026-09-10" to 1829,
        failedSources24h = listOf("GitHub 신규 저장소"),
        untranslated = 150,
    )

    @Test
    fun `인사이트_6종_순서`() {
        val insights = buildInsights(sample())
        assertEquals(6, insights.size)
        assertEquals("🏆", insights[0].icon)
        assertTrue(insights[0].body.contains("MAS 키워드 발견"))
        assertTrue(insights[1].body.contains("2026-09-10"))
        assertTrue(insights[2].body.contains("GitHub 신규 저장소"))
        assertTrue(insights[3].body.contains("150건"))
        assertTrue(insights[4].body.contains("12건"))
        assertTrue(insights[5].body.contains("유틸리티"))
    }

    @Test
    fun `빈데이터_빈카드`() {
        val insights = buildInsights(
            sample().copy(
                bestSource = null, bestDay = null, failedSources24h = emptyList(),
                untranslated = 0, bumpsLast7d = 0, topCategory = null,
            ),
        )
        assertTrue(insights.isEmpty())
    }
}
