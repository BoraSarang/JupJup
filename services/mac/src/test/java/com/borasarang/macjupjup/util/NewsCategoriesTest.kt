package com.borasarang.macjupjup.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsCategoriesTest {

    @Test
    fun `맥_분류`() {
        assertEquals("루머", NewsCategories.classify("mac", "Gurman: New MacBook Pro", "", null))
        assertEquals("Apple Silicon", NewsCategories.classify("mac", "M4 chip benchmarks", "", null))
        assertEquals("macOS", NewsCategories.classify("mac", "macOS Tahoe beta released", "", null))
        assertEquals("앱·업데이트", NewsCategories.classify("mac", "Raycast update 1.90 released", "", null))
        assertEquals("팁", NewsCategories.classify("mac", "How to use shortcuts", "", null))
        assertEquals("전체", NewsCategories.classify("mac", "오늘 날씨 맑음", "관련 없는 내용", null))
    }

    @Test
    fun `AI_분류`() {
        assertEquals("모델 출시", NewsCategories.classify("ai", "GPT-6 model release", "", null))
        assertEquals("연구/논문", NewsCategories.classify("ai", "New arxiv paper on benchmarks", "", null))
        assertEquals("정책", NewsCategories.classify("ai", "EU AI Act regulation update", "", null))
        assertEquals("비즈니스", NewsCategories.classify("ai", "Startup funding round enterprise", "", null))
    }

    @Test
    fun `보안_분류`() {
        assertEquals("취약점/CVE", NewsCategories.classify("sec", "CVE-2026-1234 exploit patch", "", null))
        assertEquals("랜섬웨어", NewsCategories.classify("sec", "LockBit ransomware attack", "", null))
        assertEquals("국내", NewsCategories.classify("sec", "KISA 보안 공지", "", "보안뉴스"))
        assertEquals("Apple보안", NewsCategories.classify("sec", "iOS security update Gatekeeper", "", null))
        assertEquals("개인정보", NewsCategories.classify("sec", "Privacy breach personal data leak", "", null))
    }

    @Test
    fun `RSS카테고리_우선`() {
        assertEquals("루머", NewsCategories.classify("mac", "어떤 제목", "어떤 본문", "Rumor"))
    }

    @Test
    fun `서브목록_유효성`() {
        assertEquals(6, NewsCategories.subsOf("mac").size)
        assertEquals(6, NewsCategories.subsOf("ai").size)
        assertEquals(6, NewsCategories.subsOf("sec").size)
        assertTrue(NewsCategories.isValid("mac", "팁"))
        assertTrue(NewsCategories.isValid("ai", "정책"))
        assertTrue(!NewsCategories.isValid("mac", "정책"))
        assertEquals(11, NewsCategories.APP_CHIPS.size)
    }

    @Test
    fun `요약_첫문장`() {
        assertEquals("첫 문장입니다.", NewsCategories.summarize("첫 문장입니다. 둘째 문장입니다."))
        assertEquals("첫 문장입니다.", NewsCategories.summarize("  첫 문장입니다.\n둘째 줄입니다.  "))
        assertNull(NewsCategories.summarize("   "))
        assertNotNull(NewsCategories.summarize("마침표 없는 본문 내용"))
        val long = "가".repeat(500)
        assertTrue(NewsCategories.summarize(long)!!.length <= 200)
    }
}
