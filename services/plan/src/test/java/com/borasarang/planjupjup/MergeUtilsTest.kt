package com.borasarang.planjupjup

import com.borasarang.planjupjup.crawler.MergeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeUtilsTest {

    @Test
    fun normalizeKey_ignoresCaseAndSpecialChars() {
        assertEquals(
            MergeUtils.normalizeKey("KT 엠모바일", "5G 맘껏!"),
            MergeUtils.normalizeKey("kt엠모바일", "5g맘껏"),
        )
    }

    @Test
    fun inferNetwork_mapsMajorCarriers() {
        assertEquals("LGU+", MergeUtils.inferNetwork("KCT (티플러스)"))
        assertEquals("KT", MergeUtils.inferNetwork("KT엠모바일"))
        assertEquals("SKT", MergeUtils.inferNetwork("SK세븐모바일"))
        assertEquals("UNKNOWN", MergeUtils.inferNetwork("무명통신"))
    }

    @Test
    fun normalizeMoyoNetwork_handlesSpacedNotation() {
        assertEquals("LGU+", MergeUtils.normalizeMoyoNetwork("LG U+망"))
        assertEquals("KT", MergeUtils.normalizeMoyoNetwork("KT망"))
        assertEquals("SKT", MergeUtils.normalizeMoyoNetwork("SKT망"))
    }

    @Test
    fun inferNetworkType_prefersHintThenTitle() {
        assertEquals("5G", MergeUtils.inferNetworkType("티플 10G+", "5G"))
        assertEquals("LTE", MergeUtils.inferNetworkType("5G 같은 이름", "LTE"))
        assertEquals("5G", MergeUtils.inferNetworkType("[모요핫딜]5G 음성기본", null))
        assertEquals("LTE", MergeUtils.inferNetworkType("가성비플러스", null))
    }

    @Test
    fun parsePrice_extractsDigits() {
        assertEquals(12700, MergeUtils.parsePrice("월 12,700원"))
        assertEquals(2900, MergeUtils.parsePrice("2,900"))
        assertEquals(10, MergeUtils.parsePrice("10"))
        assertEquals(0, MergeUtils.parsePrice(null))
        assertEquals(0, MergeUtils.parsePrice("문의"))
    }

    @Test
    fun parseDiscountMonths_extractsMonths() {
        assertEquals(6, MergeUtils.parseDiscountMonths("6개월 이후 24,200원/월"))
        assertEquals(null, MergeUtils.parseDiscountMonths(null))
        assertEquals(null, MergeUtils.parseDiscountMonths("평생할인"))
    }

    @Test
    fun parseDataGb_handlesVariants() {
        assertEquals(15, MergeUtils.parseDataGb("15GB"))
        assertEquals(10, MergeUtils.parseDataGb("10GB+1Mbps"))
        assertEquals(150, MergeUtils.parseDataGb("월 150GB + 5Mbps"))
        assertEquals(Int.MAX_VALUE, MergeUtils.parseDataGb("무제한"))
        assertEquals(0, MergeUtils.parseDataGb("300분"))
    }

    @Test
    fun generateTags_assignsCurationTags() {
        val youth = MergeUtils.generateTags(10000, "15GB")
        assert(youth.contains("가성비청년")) { youth }
        val heavy = MergeUtils.generateTags(38200, "100GB+5Mbps")
        assert(heavy.contains("해비유저")) { heavy }
        val senior = MergeUtils.generateTags(1700, "500MB")
        assert(senior.contains("효도폰")) { senior }
        assertEquals("", MergeUtils.generateTags(60000, "2GB"))
    }
}
