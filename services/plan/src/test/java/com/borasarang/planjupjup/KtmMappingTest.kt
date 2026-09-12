package com.borasarang.planjupjup

import com.borasarang.planjupjup.crawler.carrier.KtmMobileCrawler
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KtmMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun source() = CrawlSource(
        id = "ktmmobile", name = "KT엠모바일 공식", type = "CARRIER_SITE",
        baseUrl = "https://www.ktmmobile.com", enabled = true, intervalHours = 12,
        intervalMinutes = 720,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null,
        selectorConfigJson = null,
    )

    private fun fixture(name: String): String {
        return javaClass.getResource("/fixtures/$name")!!.readText()
    }

    @Test
    fun findLeaves_returnsCodesWithoutChildren() {
        val categories = (json.parseToJsonElement(fixture("ktm_categories.json")) as JsonArray)
            .map { it.jsonObject }
        val crawler = KtmMobileCrawler(source())
        val leafCodes = crawler.findLeaves(categories).map {
            it.getValue("rateAdsvcCtgCd").toString().trim('"')
        }.toSet()
        assertEquals(setOf("2", "9", "14", "21"), leafCodes)
    }

    @Test
    fun ancestorDepth2Name_walksUpToLteGroup() {
        val categories = (json.parseToJsonElement(fixture("ktm_categories.json")) as JsonArray)
            .map { it.jsonObject }
        val crawler = KtmMobileCrawler(source())
        // 14(초알뜰) → 4(LTE 요금제)
        assertEquals("LTE 요금제", crawler.ancestorDepth2Name(categories, "14"))
        // 21(5G 모두다 맘껏) → 5(5G 요금제)
        assertEquals("5G 요금제", crawler.ancestorDepth2Name(categories, "21"))
    }

    @Test
    fun toDraft_mapsRealPlanJson() {
        val item = json.parseToJsonElement(fixture("ktm_plan.json")).jsonObject
        val crawler = KtmMobileCrawler(source())
        val draft = crawler.toDraft(item, "LTE 요금제", "https://www.ktmmobile.com")
        assertNotNull(draft)
        val plan = draft!!.plan
        assertEquals("KT엠모바일", plan.carrierName)
        assertEquals("KT", plan.mvnoNetwork)
        assertEquals("초알뜰 1GB/100분", plan.planName)
        // 프로모가 2,900원 → 대표값, 정가 15,400원
        assertEquals(2900, plan.price)
        assertEquals(15400, plan.priceAfterDiscount)
        assertEquals("1GB", plan.dataAmount)
        assertEquals("100분", plan.voice)
        assertEquals("100건", plan.sms)
        assertEquals("LTE", plan.networkType)
        assert(plan.eventBadge!!.contains("바로배송")) { plan.eventBadge!! }
        val url = draft.mappings[0].sourceUrl
        assert(url.contains("rateLayer.do") && url.contains("PL212H918")) { url }
        assertEquals("KT엠모바일 공식", draft.mappings[0].sourceName)
    }

    @Test
    fun toDraft_fallsBackToBasePriceWithoutPromo() {
        val item = json.parseToJsonElement(fixture("ktm_plan.json")).jsonObject.toMutableMap()
        item["promotionAmtVatDesc"] = kotlinx.serialization.json.JsonNull
        val draft = KtmMobileCrawler(source()).toDraft(
            JsonObject(item), "LTE 요금제", "https://www.ktmmobile.com",
        )
        assertNotNull(draft)
        assertEquals(15400, draft!!.plan.price)
        assertEquals(null, draft.plan.priceAfterDiscount)
    }
}
