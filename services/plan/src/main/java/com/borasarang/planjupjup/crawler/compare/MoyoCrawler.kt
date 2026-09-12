package com.borasarang.planjupjup.crawler.compare

import com.borasarang.planjupjup.crawler.BaseCrawler
import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import com.borasarang.planjupjup.crawler.MergeUtils
import com.borasarang.planjupjup.crawler.PlanDraft
import com.borasarang.planjupjup.data.db.entity.CrawlSource

/**
 * 모요 크롤러. 실측 구조(2026-09-06) 기반 — /plans SSR 카드 파싱.
 * - 카드: a[href^=/plans/] 중 /plans/{숫자} 형태
 * - 통신사: 카드 내 로고 img[alt]
 * - 본문 span 키워드 앵커 파싱: 요금제명 / 데이터(GB·Mbps) / 통화 / 문자 / 망 / 5G·LTE /
 *   특전 / 월 N원 / N개월 이후 M원
 * - 상세: 카드 href 절대경로
 */
class MoyoCrawler(source: CrawlSource) : BaseCrawler(source) {

    override val sourceName: String = "모요"

    override fun defaultConfig(): CrawlerSelectorConfig = CrawlerSelectorConfig(
        listPageUrl = "https://www.moyoplan.com/plans",
        itemSelector = "a[href^=/plans/]",
        maxPages = 5,
    )

    override suspend fun crawl(): Result<List<PlanDraft>> {
        return try {
            logStart()
            val config = config()
            val drafts = mutableListOf<PlanDraft>()
            val seenIds = LinkedHashSet<String>()

            for (page in 0 until config.maxPages) {
                val url = if (page == 0) config.listPageUrl else "${config.listPageUrl}?page=${page + 1}"
                val html = try {
                    fetchGet(url)
                } catch (e: Exception) {
                    if (page == 0) throw e
                    break
                }
                val doc = parseHtml(html, config.listPageUrl)
                val cards = doc.select(config.itemSelector)
                    .filter { PLAN_LINK.matches(it.attr("href")) }
                if (cards.isEmpty()) break

                var newOnPage = 0
                cards.forEach { card ->
                    val href = card.attr("href")
                    if (!seenIds.add(href)) return@forEach
                    val draft = parseCard(card) ?: return@forEach
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
    internal fun parseCard(card: org.jsoup.nodes.Element): PlanDraft? {
        val detailUrl = card.attr("abs:href").ifBlank { return null }
        val carrierName = card.select("img[alt]").firstOrNull {
            it.attr("alt").isNotBlank()
        }?.attr("alt")?.trim() ?: return null

        val spans = card.select("span").eachText()
            .map { it.trim() }.filter { it.isNotEmpty() }
        val parsed = parseSpans(spans) ?: return null

        return buildDraft(
            carrierName = carrierName,
            planName = parsed.planName,
            price = parsed.price,
            priceAfterDiscount = parsed.priceAfter,
            discountMonths = parsed.months,
            dataAmount = parsed.data,
            voice = parsed.voice,
            sms = parsed.sms,
            networkType = MergeUtils.inferNetworkType(parsed.planName, parsed.netType),
            mvnoNetwork = MergeUtils.normalizeMoyoNetwork(parsed.network),
            eventBadge = parsed.badge,
            detailUrl = detailUrl,
            rawHtml = card.outerHtml(),
        )
    }

    private data class MoyoFields(
        val planName: String,
        val data: String,
        val voice: String,
        val sms: String,
        val network: String,
        val netType: String?,
        val badge: String?,
        val price: Int,
        val priceAfter: Int?,
        val months: Int?,
    )

    private fun parseSpans(spans: List<String>): MoyoFields? {
        // [0]=평점(예: 4.5), [1]=요금제명
        if (spans.size < 4) return null
        val planName = spans.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val rest = spans.drop(2)
        val data = rest.firstOrNull { "GB" in it.uppercase() || "MBPS" in it.uppercase() } ?: ""
        val voice = rest.firstOrNull { it.startsWith("통화") }?.removePrefix("통화")?.trim() ?: ""
        val sms = rest.firstOrNull { it.startsWith("문자") }?.removePrefix("문자")?.trim() ?: ""
        val network = rest.firstOrNull { it.endsWith("망") } ?: ""
        val netType = rest.firstOrNull { it == "5G" || it == "LTE" }
        val priceText = rest.firstOrNull { it.startsWith("월") && "원" in it } ?: return null
        val price = MergeUtils.parsePrice(priceText)
        if (price <= 0) return null
        val afterText = rest.firstOrNull { "개월 이후" in it }
        val months = MergeUtils.parseDiscountMonths(afterText)
        // "7개월 이후 59,400원" — 개월 수가 가격 숫자에 섞이지 않도록 접두 제거 후 파싱
        val priceAfter = afterText
            ?.replace(Regex("""\d+\s*개월\s*이후"""), "")
            ?.let { MergeUtils.parsePrice(it) }
            ?.takeIf { it > 0 }
        // 특전: 데이터·통화·문자·망·타입·가격·할인후·평점에 해당하지 않는 짧은 텍스트
        val badge = rest.firstOrNull { s ->
            !(s.startsWith("통화") || s.startsWith("문자") || s.endsWith("망") ||
                s == "5G" || s == "LTE" || s.startsWith("월") ||
                "개월 이후" in s || "GB" in s.uppercase() || "MBPS" in s.uppercase() ||
                s.matches(Regex("""\d\.\d""")))
        }?.takeIf { it.length <= 30 }
        val cleanData = data.removePrefix("월").trim()
        return MoyoFields(planName, cleanData, voice, sms, network, netType, badge, price, priceAfter, months)
    }

    companion object {
        private val PLAN_LINK = Regex("""/plans/\d+(\?.*)?$""")
    }
}
