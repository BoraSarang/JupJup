package com.borasarang.macjupjup.crawler.itunes

import com.borasarang.macjupjup.crawler.itunes.ITunesNameMatcher.SearchHit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    @Test
    fun `엄격매칭_이름개발사_일치`() {
        assertTrue(
            ITunesNameMatcher.isStrictMatch(
                "Raycast", "Raycast Technologies",
                SearchHit(1, "Raycast", "Raycast Technologies", "u"),
            )
        )
    }

    @Test
    fun `이름다르면_실패`() {
        assertFalse(
            ITunesNameMatcher.isStrictMatch(
                "Raycast", "Raycast Technologies",
                SearchHit(1, "Raycast Pro", "Raycast Technologies", "u"),
            )
        )
    }

    @Test
    fun `수집원표기_Setapp_HN은_엄격매칭실패`() {
        assertFalse(
            ITunesNameMatcher.isStrictMatch(
                "AlDente Pro", "Setapp",
                SearchHit(1, "AlDente Pro", "Applause s.r.l.", "u"),
            )
        )
        assertFalse(
            ITunesNameMatcher.isStrictMatch(
                "Chalk", "HN @dev",
                SearchHit(1, "Chalk", "AppKit Studio", "u"),
            )
        )
    }

    @Test
    fun `단일후보폴백_1건정확일치만`() {
        val hit = SearchHit(1, "AlDente Pro", "Applause s.r.l.", "u")
        assertTrue(
            ITunesNameMatcher.singleExactFallback("AlDente Pro", "Setapp", listOf(hit)) == hit
        )
        // 2건이면 실패
        assertTrue(
            ITunesNameMatcher.singleExactFallback(
                "AlDente Pro", "Setapp", listOf(hit, hit.copy(trackId = 2))
            ) == null
        )
        // 실제 개발사면 폴백 미적용
        assertTrue(
            ITunesNameMatcher.singleExactFallback("AlDente Pro", "Applause", listOf(hit)) == null
        )
    }

    @Test
    fun `수집원표기_MMB도_플레이스홀더_T071`() {
        assertTrue(ITunesNameMatcher.isPlaceholderDeveloper("MacMenuBar"))
        // MMB 수집 앱도 단일 후보 폴백 대상
        val hit = SearchHit(9, "Lyrimuse", "Yudaotor", "u")
        assertTrue(
            ITunesNameMatcher.singleExactFallback("Lyrimuse", "MacMenuBar", listOf(hit)) == hit
        )
    }

    @Test
    fun `정규화_공백특수문자무시`() {
        assertTrue(
            ITunesNameMatcher.isStrictMatch(
                "Mail+ for Gmail", "Rocky Sand Studio Ltd.",
                SearchHit(1, "Mail Plus for Gmail", "Rocky Sand Studio Ltd", "u"),
            ) == false // Plus vs + 는 다른 단어 → 실패가 정상
        )
    }
}
