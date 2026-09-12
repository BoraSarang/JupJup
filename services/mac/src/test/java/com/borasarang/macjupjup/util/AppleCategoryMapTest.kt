package com.borasarang.macjupjup.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppleCategoryMapTest {

    @Test
    fun `애플카테고리_매핑`() {
        assertEquals("생산성", AppleCategoryMap.map("Productivity"))
        assertEquals("개발", AppleCategoryMap.map("Developer Tools"))
        assertEquals("디자인·크리에이티브", AppleCategoryMap.map("Graphics & Design"))
        assertEquals("미디어·엔터", AppleCategoryMap.map("Music"))
        assertEquals("금융", AppleCategoryMap.map("Finance"))
        assertEquals("유틸리티", AppleCategoryMap.map("Unknown Genre"))
    }
}
