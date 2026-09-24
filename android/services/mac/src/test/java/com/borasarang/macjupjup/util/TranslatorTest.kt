package com.borasarang.macjupjup.util

import com.borasarang.macjupjup.util.translate.MacTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {

    @Test
    fun `gtx응답_결합`() {
        val body = """[[["안녕하세요","Hello",null,null,1]],null,"en"]"""
        assertEquals("안녕하세요", MacTranslator.parseGtx(body))
    }

    @Test
    fun `gtx응답_불량_null`() {
        assertNull(MacTranslator.parseGtx("not json"))
        assertNull(MacTranslator.parseGtx("[]"))
    }

    @Test
    fun `gtx응답_개행결합_T131`() {
        val body = """[[["첫째 줄\n둘째 줄","first\nsecond",null,null,1]],null,"en"]"""
        assertEquals("첫째 줄\n둘째 줄", MacTranslator.parseGtx(body))
    }

    @Test
    fun `리드미마커_판정_T141`() {
        assertTrue(MacTranslator.isReadmeMarker("— README —"))
        assertTrue(MacTranslator.isReadmeMarker("  — README —  "))
        assertFalse(MacTranslator.isReadmeMarker("— README — extra"))
    }

    @Test
    fun `줄앞마커_분리_T141`() {
        assertEquals("## " to "Title", MacTranslator.stripLeadingMarker("## Title"))
        assertEquals("- " to "item", MacTranslator.stripLeadingMarker("- item"))
        assertEquals("> " to "quote", MacTranslator.stripLeadingMarker("> quote"))
        assertEquals("1. " to "first", MacTranslator.stripLeadingMarker("1. first"))
        assertEquals("" to "plain", MacTranslator.stripLeadingMarker("plain"))
    }

    @Test
    fun `마크다운조각_분할_T141`() {
        val segs = MacTranslator.splitMdLine("Use `code` and [docs](https://x.com/a) now")
        assertEquals(
            listOf(
                true to "Use ",
                false to "`code`",
                true to " and ",
                false to "[",
                true to "docs",
                false to "](https://x.com/a)",
                true to " now",
            ),
            segs,
        )
        // URL에 영문이 있어도 보존 조각은 needsTranslation 우회 (번역 호출 제외)
        val keep = segs.filter { !it.first }.joinToString("") { it.second }
        assertTrue(keep.contains("https://x.com/a"))
        assertTrue(keep.contains("`code`"))
    }

    @Test
    fun `영문판정_한글포함제외`() {
        assertTrue(MacTranslator.isEnglish("Hello world"))
        assertFalse(MacTranslator.isEnglish("안녕하세요"))
        assertFalse(MacTranslator.isEnglish("123"))
    }

    @Test
    fun `번역필요_중일러한글판정_T100`() {
        assertTrue(MacTranslator.needsTranslation("Hello world"))
        assertTrue(MacTranslator.needsTranslation("这是一款菜单栏应用"))
        assertTrue(MacTranslator.needsTranslation("メニューバーアプリです"))
        assertTrue(MacTranslator.needsTranslation("Это приложение для macOS"))
        assertFalse(MacTranslator.needsTranslation("안녕하세요 메뉴바 앱입니다"))
        assertFalse(MacTranslator.needsTranslation("123"))
        assertFalse(MacTranslator.needsTranslation("   "))
    }
}
