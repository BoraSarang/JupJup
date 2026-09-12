package com.borasarang.macjupjup.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryInferTest {

    @Test
    fun `메뉴바키워드_유틸리티_MenuBar태그`() {
        val r = CategoryInfer.infer("One Switch menubar toggle", listOf("menu-bar"))
        assertEquals("유틸리티", r.category)
        assertTrue(r.tags.contains(AppTag.MENU_BAR))
    }

    @Test
    fun `AI에이전트_태그`() {
        val r = CategoryInfer.infer("Claudexor AI agent manager", listOf("ai-agents", "macos"))
        assertTrue(r.tags.contains(AppTag.AI_AGENT))
    }

    @Test
    fun `터미널_개발`() {
        assertEquals("개발", CategoryInfer.infer("powerful terminal emulator", listOf("terminal")).category)
    }

    @Test
    fun `모호하면_유틸리티`() {
        val r = CategoryInfer.infer("some random thing", emptyList())
        assertEquals("유틸리티", r.category)
        assertTrue(r.tags.isEmpty())
    }

    @Test
    fun `짧은키워드_단어경계_Hide는개발아님_details는AI아님`() {
        val r = CategoryInfer.infer("Hide sensitive details fast", emptyList())
        assertEquals("유틸리티", r.category)
        assertTrue(r.tags.isEmpty())
    }
}
