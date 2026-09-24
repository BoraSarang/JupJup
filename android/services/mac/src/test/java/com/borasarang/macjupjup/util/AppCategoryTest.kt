package com.borasarang.macjupjup.util

import com.borasarang.macjupjup.util.category.AppCategory
import com.borasarang.macjupjup.util.category.AppLicense
import com.borasarang.macjupjup.util.category.AppTag
import com.borasarang.macjupjup.util.category.classifyLicense
import com.borasarang.macjupjup.util.category.maskToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryTest {

    @Test
    fun `카테고리는_10개_Setapp기준`() {
        assertEquals(10, AppCategory.ALL.size)
        assertTrue(AppCategory.isValid("개발"))
        assertFalse(AppCategory.isValid("게임"))
    }

    @Test
    fun `라이선스_자동분류_repo우선`() {
        assertEquals(AppLicense.OSS, classifyLicense("owner/repo", 9.99))
        assertEquals(AppLicense.PAID, classifyLicense(null, 9.99))
        assertEquals(AppLicense.FREE, classifyLicense(null, 0.0))
        assertEquals(AppLicense.FREE, classifyLicense("  ", 0.0))
    }

    @Test
    fun `태그는_AI-Agent와_MenuBar만`() {
        assertTrue(AppTag.isValid(AppTag.AI_AGENT))
        assertTrue(AppTag.isValid(AppTag.MENU_BAR))
        assertFalse(AppTag.isValid("생산성"))
    }

    @Test
    fun `토큰_마스킹_뒤4자리만`() {
        assertEquals("****1234", maskToken("ghp_abcd1234"))
        assertEquals("****", maskToken("abc"))
    }
}
