package com.borasarang.macjupjup.crawler

import com.borasarang.macjupjup.crawler.itunes.itunesLookupUrl
import com.borasarang.macjupjup.crawler.itunes.itunesSearchUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonCrawlTest {

    @Test
    fun `제목분리_PH_HN관례`() {
        assertEquals("DockDuck", splitTitle("DockDuck — 파일 관리자"))
        // HN은 "Show HN" 접두 제거 후 분리 (실제 호출 순서와 동일)
        assertEquals("Chalk", splitTitle(" Chalk — 설명", listOf(": ")))
        assertEquals("단일제목", splitTitle("단일제목"))
        assertEquals("Show HN: Chalk", splitTitle("Show HN: Chalk — 설명"))
    }

    @Test
    fun `mac판정_공통_전용`() {
        assertTrue(isMacRelated("macOS menu bar app"))
        assertFalse(isMacRelated("Windows launcher"))
        assertTrue(isMacRelated("Apple silicon optimized", setOf("apple silicon")))
        assertTrue(isMacRelated("SwiftUI app", setOf("swiftui")))
        assertFalse(isMacRelated("SwiftUI app"))
    }

    @Test
    fun `itunes_URL조립`() {
        assertEquals(
            "https://itunes.apple.com/lookup?id=1,2&country=us&entity=macSoftware",
            itunesLookupUrl("1,2"),
        )
        assertEquals(
            "https://itunes.apple.com/search?term=a+b&country=us&entity=macSoftware&limit=5",
            itunesSearchUrl("a b"),
        )
    }
}
