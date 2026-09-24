package com.borasarang.planjupjup.crawler.compare

import com.borasarang.planjupjup.crawler.BaseCrawler
import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import com.borasarang.planjupjup.crawler.FieldSelectors
import com.borasarang.planjupjup.crawler.MergeUtils
import com.borasarang.planjupjup.crawler.PlanDraft
import com.borasarang.planjupjup.data.db.entity.CrawlSource

/**
 * 알뜰폰허브 크롤러. 실측 구조(2026-09-06) 기반.
 * - 카드: div.plan_card (data-product-id)
 * - 이름: p.tit / 망·사업자: .posi li[0..1] / 가격: .price .now span
 * - 할인후: .time_after span + "N개월 이후" / 데이터·통화·문자: li.wifi/call/mes
 * - 망뱃지: .tips .purple (없으면 제목에서 5G 추론) / 특전: .tips .green
 * - 상세: a.click-guard href (/product/products/{id}.do)
 * - 페이지네이션: ?page=N 시도-중단 (신규 product-id 0이면 종료)
 */
class MvnohubCrawler(source: CrawlSource) : BaseCrawler(source) {

    override val sourceName: String = "알뜰폰허브"

    override fun defaultConfig(): CrawlerSelectorConfig = CrawlerSelectorConfig(
        listPageUrl = "https://www.mvnohub.kr/product/products.do",
        itemSelector = "div.plan_card",
        fields = FieldSelectors(
            carrierName = ".posi ul li:eq(1)",
            planName = "p.tit",
            price = ".price .now span",
            priceAfterDiscount = ".time_after span",
            discountMonths = ".time_after",
            dataAmount = "li.wifi",
            voice = "li.call",
            sms = "li.mes",
            networkType = ".tips .purple",
            eventBadge = ".tips .green",
            detailUrl = "a.click-guard",
            network = ".posi ul li:eq(0)",
        ),
        maxPages = 10,
    )

    override suspend fun crawl(): Result<List<PlanDraft>> {
        return try {
            logStart()
            val config = config()
            val seenProductIds = LinkedHashSet<String>()
            val drafts = mutableListOf<PlanDraft>()

            for (page in 0 until config.maxPages) {
                val url = if (page == 0) config.listPageUrl else "${config.listPageUrl}?page=$page"
                val html = try {
                    fetchGet(url)
                } catch (e: Exception) {
                    if (page == 0) throw e
                    break
                }
                val doc = parseHtml(html, config.listPageUrl)
                val cards = doc.select(config.itemSelector)
                if (cards.isEmpty()) break

                var newOnPage = 0
                cards.forEach { card ->
                    val productId = card.attr("data-product-id").ifBlank { null }
                    if (productId != null && !seenProductIds.add(productId)) return@forEach
                    val draft = parseCard(card, config.listPageUrl) ?: return@forEach
                    drafts += draft
                    newOnPage++
                }
                if (newOnPage == 0) break
                politenessDelay()
            }
            logDone(drafts.size)
            Result.success(drafts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 카드 1건 파싱 (단위 테스트 대상). 실패·스킵 시 null */
    internal fun parseCard(card: org.jsoup.nodes.Element, baseUrl: String): PlanDraft? {
        val fields = defaultConfig().fields
        val planName = card.select(fields.planName).firstOrNull()?.text()?.trim()
            ?: return null
        val posiItems = card.select(".posi ul li").eachText()
        val networkRaw = posiItems.getOrNull(0) ?: ""
        val carrierName = posiItems.getOrNull(1)
            ?: card.select(fields.carrierName).firstOrNull()?.text()?.trim()
            ?: return null
        val price = MergeUtils.parsePrice(
            card.select(fields.price).firstOrNull()?.text(),
        )
        if (price <= 0) return null
        val afterText = card.select(fields.priceAfterDiscount ?: "").firstOrNull()?.text()
        val priceAfter = MergeUtils.parsePrice(afterText).takeIf { it > 0 }
        val months = MergeUtils.parseDiscountMonths(
            card.select(fields.discountMonths ?: "").firstOrNull()?.text(),
        )
        val data = stripLabel(card.select(fields.dataAmount).firstOrNull(), "데이터")
        val voice = stripLabel(card.select(fields.voice).firstOrNull(), "통화")
        val sms = stripLabel(card.select(fields.sms).firstOrNull(), "문자")
        val netHint = card.select(fields.networkType ?: "").firstOrNull()?.text()
        val networkType = MergeUtils.inferNetworkType(planName, netHint)
        val event = card.select(fields.eventBadge ?: "").firstOrNull()?.text()?.trim()
        val detailUrl = card.select(fields.detailUrl).firstOrNull()?.attr("abs:href")
            ?: baseUrl

        return buildDraft(
            carrierName = carrierName,
            planName = planName,
            price = price,
            priceAfterDiscount = priceAfter,
            discountMonths = months,
            dataAmount = data,
            voice = voice,
            sms = sms,
            networkType = networkType,
            mvnoNetwork = normalizeHubNetwork(networkRaw)
                .takeIf { it != "UNKNOWN" }
                // posi[0]이 해당 상품의 실제 망. 모호할 때만 사업자명 유추
                ?: MergeUtils.inferNetwork(carrierName),
            eventBadge = event,
            detailUrl = detailUrl,
            rawHtml = card.outerHtml(),
        )
    }

    /** 허브 표기 "LGU+"/"SKT"/"KT" 그대로 사용, 모호하면 사업자명 유추 */
    private fun normalizeHubNetwork(raw: String): String {
        val n = raw.replace(" ", "").uppercase()
        return when {
            "LGU+" in n -> "LGU+"
            n == "SKT" || n.startsWith("SK") -> "SKT"
            n == "KT" || n.startsWith("KT") -> "KT"
            else -> "UNKNOWN"
        }
    }
}
