package com.borasarang.macjupjup.util

import com.borasarang.macjupjup.util.merge.MergeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeUtilsTest {

    @Test
    fun `동일앱_대소문자공백차이_동일키`() {
        assertEquals(
            MergeUtils.generateId("Raycast", "Raycast Technologies"),
            MergeUtils.generateId("  raycast ", "raycast-technologies!"),
        )
    }

    @Test
    fun `다른앱_다른키`() {
        assertNotEquals(
            MergeUtils.generateId("Raycast", "Raycast Technologies"),
            MergeUtils.generateId("Alfred", "Running with Crayons"),
        )
    }

    @Test
    fun `긴이름_전체128자절단`() {
        val id = MergeUtils.generateId("A".repeat(100), "B".repeat(100))
        assertEquals(128, id.length)
    }

    @Test
    fun `정규화_단일진실`() {
        assertEquals("soundpaste", MergeUtils.normalizeName("SoundPaste!"))
        assertEquals("macmenubar", MergeUtils.normalizeName("MacMenuBar"))
    }

    @Test
    fun `키릴_CJK_정규화_보존_id충돌방지`() {
        // 라틴 외 제거하면 모두 "appstorrent" 단일 id → 상호 덮어쓰기
        val idA = MergeUtils.generateId("Космическая Одиссея", "AppStorrent")
        val idB = MergeUtils.generateId("RimWorld", "AppStorrent")
        val idC = MergeUtils.generateId("日本語タイトル", "AppStorrent")
        assertNotEquals(idA, idB)
        assertNotEquals(idA, idC)
        assertNotEquals(idB, idC)
        assertTrue(idA.contains("appstorrent"))
        assertTrue(MergeUtils.normalizeName("Космос").isNotBlank())
        assertTrue(MergeUtils.normalizeName("日本語").isNotBlank())
    }

    @Test
    fun `전부특수문자_결과는_hexfallback_빈id금지`() {
        val n = MergeUtils.normalizeName("!!!")
        assertTrue(n.isNotBlank())
        assertNotEquals(MergeUtils.normalizeName("???"), n)
    }

    @Test
    fun `버전비교_공백차이_동일버전_T132`() {
        assertTrue(MergeUtils.sameVersion("4.3.4", "4.3.4 "))
        assertTrue(MergeUtils.sameVersion(" 4.3.4\n", "4.3.4"))
        assertFalse(MergeUtils.sameVersion("4.3.4", "4.3.5"))
        assertFalse(MergeUtils.sameVersion("4.3.4", null))
        assertTrue(MergeUtils.sameVersion(null, null))
    }
}
