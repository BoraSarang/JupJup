package com.borasarang.planjupjup

import com.borasarang.planjupjup.crawler.compare.MoyoCrawler
import com.borasarang.planjupjup.crawler.compare.MvnohubCrawler
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CrawlerParseTest {

    private fun hubSource() = CrawlSource(
        id = "mvnohub", name = "알뜰폰허브", type = "COMPARE_SITE",
        baseUrl = "https://www.mvnohub.kr", enabled = true, intervalHours = 6,
        intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null,
        selectorConfigJson = null,
    )

    private fun moyoSource() = CrawlSource(
        id = "moyo", name = "모요", type = "COMPARE_SITE",
        baseUrl = "https://www.moyoplan.com", enabled = true, intervalHours = 6,
        intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null,
        selectorConfigJson = null,
    )

    private fun fixture(name: String): String {
        return javaClass.getResource("/fixtures/$name")!!.readText()
    }

    @Test
    fun mvnohub_parsesRealCardStructure() {
        val doc = Jsoup.parse(fixture("mvnohub_cards.html"), "https://www.mvnohub.kr")
        val cards = doc.select("div.plan_card")
        assertEquals(2, cards.size)

        val crawler = MvnohubCrawler(hubSource())
        val first = crawler.parseCard(cards[0], "https://www.mvnohub.kr/product/products.do")
        assertNotNull(first)
        val plan = first!!.plan
        assertEquals("KCT (티플러스)", plan.carrierName)
        assertEquals("LGU+", plan.mvnoNetwork)
        assertEquals("5G 티플 10GB＋(300분)", plan.planName)
        assertEquals(10, plan.price)
        assertEquals(24200, plan.priceAfterDiscount)
        assertEquals(6, plan.discountMonths)
        assertEquals("10GB+1Mbps", plan.dataAmount)
        assertEquals("300분", plan.voice)
        assertEquals("100건", plan.sms)
        // .purple 없음 → 제목 5G 추론
        assertEquals("5G", plan.networkType)
        assertEquals("허브전용", plan.eventBadge)
        assertEquals("https://www.mvnohub.kr/product/products/7171.do", first.mappings[0].sourceUrl)
        assertEquals("알뜰폰허브", first.mappings[0].sourceName)
        assert(plan.tags!!.contains("가성비청년")) { plan.tags!! }

        val second = crawler.parseCard(cards[1], "https://www.mvnohub.kr/product/products.do")
        assertNotNull(second)
        // .purple LTE 명시
        assertEquals("LTE", second!!.plan.networkType)
        assertEquals("SKT", second.plan.mvnoNetwork)
        assertEquals(14000, second.plan.price)
    }

    @Test
    fun mvnohub_skipsCardWithoutPrice() {
        val doc = Jsoup.parse("<div class='plan_card'><p class='tit'>이름</p></div>", "https://www.mvnohub.kr")
        val draft = MvnohubCrawler(hubSource()).parseCard(doc.select(".plan_card").first()!!, "https://x")
        assertNull(draft)
    }

    @Test
    fun moyo_parsesSsrCardStructure() {
        val doc = Jsoup.parse(fixture("moyo_cards.html"), "https://www.moyoplan.com")
        val cards = doc.select("a[href^=/plans/]")
        assertEquals(2, cards.size)

        val crawler = MoyoCrawler(moyoSource())
        val first = crawler.parseCard(cards[0])
        assertNotNull(first)
        val plan = first!!.plan
        assertEquals("찬스모바일", plan.carrierName)
        assertEquals("LGU+", plan.mvnoNetwork)
        assertEquals("[모요핫딜]5G 음성기본 150GB+5Mbps", plan.planName)
        assertEquals(12700, plan.price)
        assertEquals(59400, plan.priceAfterDiscount)
        assertEquals(7, plan.discountMonths)
        assertEquals("150GB + 5Mbps", plan.dataAmount)
        assertEquals("무제한", plan.voice)
        assertEquals("무제한", plan.sms)
        assertEquals("5G", plan.networkType)
        assertEquals("페이백 포함", plan.eventBadge)
        assertEquals("https://www.moyoplan.com/plans/37123", first.mappings[0].sourceUrl)

        val second = crawler.parseCard(cards[1])
        assertNotNull(second)
        assertEquals("KT엠모바일", second!!.plan.carrierName)
        assertEquals("KT", second.plan.mvnoNetwork)
        assertEquals(38200, second.plan.price)
        // 특전 없음 → null
        assertNull(second.plan.eventBadge)
    }
}
