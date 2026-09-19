package com.borasarang.common.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaSearchClientTest {

    private val client = ExaSearchClient("fake-key")

    @Test
    fun 파서_하이라이트와본문_추출() {
        val body = """
            {
              "results": [
                {"title": "OpenRouter 공식 블로그", "url": "https://openrouter.ai/blog/1",
                 "publishedDate": "2026-09-18T00:00:00.000Z", "highlights": ["내용 하이라이트 1", "여분"]},
                {"title": "카탈로그 문서", "url": "https://openrouter.ai/models",
                 "text": "모델 A는 무료입니다. 모델 B는 유료 전환되었습니다."},
                {"title": "", "url": "", "text": "잘못된 항목 — url 없어 제외"}
              ]
            }
        """.trimIndent()
        val list = client.parseSearchResponse(body)
        assertEquals(2, list.size)
        assertEquals("https://openrouter.ai/blog/1", list[0].url)
        assertEquals("내용 하이라이트 1", list[0].excerpt)
        assertEquals("2026-09-18T00:00:00.000Z", list[0].publishedDate)
        assertEquals("모델 A는 무료입니다. 모델 B는 유료 전환되었습니다.", list[1].excerpt)
    }

    @Test
    fun 파서_url누락제외_및_빈결과() {
        assertEquals(0, client.parseSearchResponse("""{"results":[{"title":"no-url"}]}""").size)
        assertEquals(0, client.parseSearchResponse("""{}""").size)
    }

    @Test
    fun 파서_불완전JSON_예외() {
        val thrown = runCatching { client.parseSearchResponse("{broken") }
        assertTrue(thrown.isFailure)
    }
}