package com.borasarang.planjupjup.crawler.carrier

import com.borasarang.planjupjup.crawler.BaseCrawler
import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import com.borasarang.planjupjup.crawler.FieldSelectors
import com.borasarang.planjupjup.crawler.MergeUtils
import com.borasarang.planjupjup.crawler.PlanDraft
import com.borasarang.planjupjup.data.db.entity.CrawlSource

/**
 * LG헬로모바일(헬로모바일) 공식 크롤러 — STAGED.
 * 작성 시점(2026-09-06) 개발망에서 hellomobile.co.kr 접속 불가로 셀렉터 미검증.
 * 구조는 타 공식몰(목록 카드 → 상세 링크) 가정 + 실패 격리.
 * 실기기(한국망) 검증 후 selectorConfigJson 확정 → 소스 활성화.
 * 기본값이므로 InitialDataSeeder에서 enabled=false 로 시드된다.
 */
class LiivMCrawler(source: CrawlSource) : BaseCrawler(source) {

    override val sourceName: String = "LG헬로모바일 공식"

    override fun defaultConfig(): CrawlerSelectorConfig = CrawlerSelectorConfig(
        listPageUrl = "https://www.hellomobile.co.kr/",
        itemSelector = ".plan-list .plan-item",
        fields = FieldSelectors(
            carrierName = ".carrier-name",
            planName = ".plan-title",
            price = ".monthly-fee",
            priceAfterDiscount = ".original-fee",
            discountMonths = ".discount-period",
            dataAmount = ".data-amount",
            voice = ".voice-amount",
            sms = ".sms-amount",
            networkType = ".network-badge",
            eventBadge = ".event-tag",
            detailUrl = "a",
        ),
        maxPages = 5,
    )

    override suspend fun crawl(): Result<List<PlanDraft>> {
        return try {
            logStart()
            val config = config()
            val fields = config.fields
            val drafts = mutableListOf<PlanDraft>()
            val seen = LinkedHashSet<String>()

            for (page in 0 until config.maxPages) {
                val url = if (page == 0) config.listPageUrl else "${config.listPageUrl}?page=${page + 1}"
                val html = try {
                    fetchGet(url)
                } catch (e: Exception) {
                    if (page == 0) throw e
                    break
                }
                val doc = parseHtml(html, config.listPageUrl)
                val items = doc.select(config.itemSelector)
                if (items.isEmpty()) break

                var newOnPage = 0
                items.forEach { item ->
                    val planName = item.select(fields.planName).firstOrNull()?.text()?.trim()
                        ?: return@forEach
                    val carrierName = item.select(fields.carrierName).firstOrNull()?.text()?.trim()
                        .takeIf { !it.isNullOrBlank() } ?: "LG헬로모바일"
                    val price = MergeUtils.parsePrice(item.select(fields.price).firstOrNull()?.text())
                    if (price <= 0) return@forEach
                    val key = MergeUtils.generateId(carrierName, planName)
                    if (!seen.add(key)) return@forEach
                    val draft = buildDraft(
                        carrierName = carrierName,
                        planName = planName,
                        price = price,
                        priceAfterDiscount = MergeUtils.parsePrice(
                            item.select(fields.priceAfterDiscount ?: "").firstOrNull()?.text(),
                        ).takeIf { it > 0 },
                        discountMonths = MergeUtils.parseDiscountMonths(
                            item.select(fields.discountMonths ?: "").firstOrNull()?.text(),
                        ),
                        dataAmount = item.select(fields.dataAmount).firstOrNull()?.text()?.trim() ?: "",
                        voice = item.select(fields.voice).firstOrNull()?.text()?.trim() ?: "",
                        sms = item.select(fields.sms).firstOrNull()?.text()?.trim() ?: "",
                        networkType = MergeUtils.inferNetworkType(
                            planName,
                            item.select(fields.networkType ?: "").firstOrNull()?.text(),
                        ),
                        mvnoNetwork = "LGU+",
                        eventBadge = item.select(fields.eventBadge ?: "").firstOrNull()?.text()?.trim(),
                        detailUrl = item.select(fields.detailUrl).firstOrNull()?.attr("abs:href")
                            ?: config.listPageUrl,
                        rawHtml = item.outerHtml(),
                    ) ?: return@forEach
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
}
