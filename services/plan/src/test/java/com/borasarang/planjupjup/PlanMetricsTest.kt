package com.borasarang.planjupjup

import com.borasarang.planjupjup.util.PlanMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMetricsTest {

    // ---------- parseDataAmount ----------

    @Test
    fun parseDataAmount_plainGb() {
        val a = PlanMetrics.parseDataAmount("15GB")
        assertEquals(15, a.dataGb)
        assertFalse(a.isUnlimited)
        assertEquals(null, a.qosMbps)
        assertTrue(a.isParsed)
    }

    @Test
    fun parseDataAmount_decimalGb_roundsDown() {
        val a = PlanMetrics.parseDataAmount("15.5GB")
        assertEquals(15, a.dataGb)
        assertTrue(a.isParsed)
    }

    @Test
    fun parseDataAmount_unlimited_only_usesAssumed() {
        val a = PlanMetrics.parseDataAmount("무제한")
        assertEquals(PlanMetrics.UNLIMITED_GB_ASSUMED, a.dataGb)
        assertTrue(a.isUnlimited)
        assertFalse(a.isParsed == false)
        assertTrue(a.isParsed)
    }

    @Test
    fun parseDataAmount_unlimited_withQos() {
        val a = PlanMetrics.parseDataAmount("무제한+5Mbps")
        assertEquals(PlanMetrics.UNLIMITED_GB_ASSUMED, a.dataGb)
        assertTrue(a.isUnlimited)
        assertEquals(5, a.qosMbps)
        assertTrue(a.isParsed)
    }

    @Test
    fun parseDataAmount_100gbWithQos_usesGb() {
        val a = PlanMetrics.parseDataAmount("100GB+3Mbps")
        assertEquals(100, a.dataGb)
        assertFalse(a.isUnlimited)
        assertEquals(3, a.qosMbps)
    }

    @Test
    fun parseDataAmount_blank_returnsUnparsed() {
        assertFalse(PlanMetrics.parseDataAmount(null).isParsed)
        assertFalse(PlanMetrics.parseDataAmount("").isParsed)
        assertFalse(PlanMetrics.parseDataAmount("   ").isParsed)
    }

    @Test
    fun parseDataAmount_voiceOnlyText_isUnparsed() {
        val a = PlanMetrics.parseDataAmount("300분")
        assertFalse(a.isParsed)
        assertEquals(0, a.dataGb)
    }

    @Test
    fun parseDataAmount_zeroGb_isUnparsed() {
        val a = PlanMetrics.parseDataAmount("0GB")
        assertFalse(a.isParsed)
    }

    // ---------- parseVoiceMinutes ----------

    @Test
    fun parseVoiceMinutes_number() {
        assertEquals(300, PlanMetrics.parseVoiceMinutes("300분"))
    }

    @Test
    fun parseVoiceMinutes_unlimited_usesAssumed() {
        assertEquals(PlanMetrics.UNLIMITED_VOICE_SMS_ASSUMED, PlanMetrics.parseVoiceMinutes("무제한"))
        assertEquals(PlanMetrics.UNLIMITED_VOICE_SMS_ASSUMED, PlanMetrics.parseVoiceMinutes("음성 기본제공"))
    }

    @Test
    fun parseVoiceMinutes_blank_returnsZero() {
        assertEquals(0, PlanMetrics.parseVoiceMinutes(null))
        assertEquals(0, PlanMetrics.parseVoiceMinutes(""))
    }

    // ---------- parseSmsCount ----------

    @Test
    fun parseSmsCount_number() {
        assertEquals(300, PlanMetrics.parseSmsCount("300건"))
    }

    @Test
    fun parseSmsCount_unlimited_usesAssumed() {
        assertEquals(PlanMetrics.UNLIMITED_VOICE_SMS_ASSUMED, PlanMetrics.parseSmsCount("무제한"))
        assertEquals(PlanMetrics.UNLIMITED_VOICE_SMS_ASSUMED, PlanMetrics.parseSmsCount("문자 기본제공"))
    }

    @Test
    fun parseSmsCount_blank_returnsZero() {
        assertEquals(0, PlanMetrics.parseSmsCount(null))
    }

    // ---------- valueScore ----------

    @Test
    fun valueScore_zeroPrice_isZero() {
        assertEquals(0.0, PlanMetrics.valueScore(20, 300, 100, 0), 0.001)
    }

    @Test
    fun valueScore_noBenefit_isZero() {
        assertEquals(0.0, PlanMetrics.valueScore(0, 0, 0, 10000), 0.001)
    }

    @Test
    fun valueScore_formula() {
        // (20 + 300*0.3 + 100*0.1) / (20000/1000) = (20+90+10)/20 = 6.0
        assertEquals(6.0, PlanMetrics.valueScore(20, 300, 100, 20000), 0.001)
    }

    @Test
    fun valueScore_moreData_higherScore() {
        val cheap = PlanMetrics.valueScore(10, 300, 100, 20000)
        val better = PlanMetrics.valueScore(30, 300, 100, 20000)
        assertTrue(better > cheap)
    }

    @Test
    fun valueScore_unlimitedAssumed_countsAs100() {
        // 무제한(100GB 가정) + 무제한 음성(2000) + 무제한 문자(2000), 3만원
        val score = PlanMetrics.valueScore(100, 2000, 2000, 30000)
        // (100 + 600 + 200) / 30 = 30.0
        assertEquals(30.0, score, 0.001)
    }
}