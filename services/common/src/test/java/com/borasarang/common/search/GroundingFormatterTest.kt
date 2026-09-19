package com.borasarang.common.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingFormatterTest {

    private fun result(url: String, title: String, date: String? = null, excerpt: String? = null) =
        SearchResult(url, title, date, excerpt)

    @Test
    fun 포맷_쿼리_결과_정리() {
        val queries = listOf(
            "OpenRouter 무료 모델" to listOf(
                result("https://openrouter.ai/models", "OpenRouter 모델 목록", "2026-09-18T00:00:00.000Z", "무료 모델 다수"),
            ),
            "빈 검색어" to emptyList(),
        )
        val out = GroundingFormatter.format(queries)
        assertTrue(out.startsWith("[웹 검색 결과 — Exa 실측]"))
        assertTrue(out.contains("### 검색어: OpenRouter 무료 모델"))
        assertTrue(out.contains("OpenRouter 모델 목록 (2026-09-18)"))
        assertTrue(out.contains("https://openrouter.ai/models"))
        assertTrue(out.contains("발췌: 무료 모델 다수"))
        assertFalse("빈 쿼리 섹션 없어야", out.contains("빈 검색어"))
    }

    @Test
    fun 전부_빈결과_사용불가폴백() {
        val out = GroundingFormatter.format(listOf("q1" to emptyList(), "q2" to emptyList()))
        assertEquals(GroundingFormatter.UNAVAILABLE_BLOCK, out)
        assertTrue(out.contains("사용 불가"))
    }

    @Test
    fun 크기_제한() {
        val queries = listOf(
            "q" to (1..10).map { result("https://e/$it", "title $it".repeat(50), excerpt = "excerpt ${it}".repeat(30)) },
        )
        val out = GroundingFormatter.format(queries, limitChars = 500)
        assertTrue(out.length <= 500)
    }

    @Test
    fun 마커_일치() {
        assertTrue(GroundingFormatter.MARKER.contains("웹 검색 결과"))
    }
}