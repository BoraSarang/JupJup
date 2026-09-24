package com.borasarang.planjupjup.crawler.brand

import com.borasarang.planjupjup.crawler.BaseCrawler
import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import com.borasarang.planjupjup.crawler.PlanDraft
import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.CarrierBrand
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.util.DebugLogger

/**
 * 알뜰폰허브 브랜드 목록 크롤러. 실측 구조(2026-09-06) 기반.
 * - 목록: https://www.mvnohub.kr/brand.do → .brand_box_list .box
 * - 이름: img.logo[alt] 우선, 없으면 .logo_div p span
 * - 통신망: p.gb ("통신망 SKT/KT/LGU+") → "통신망" 제거 후 원문 저장
 * - 홈페이지: a.link_a[href] / 요금제목록: a[href*=/brand/plan/] → id는 /brand/plan/{id}.do
 * - 로고: img.logo[src]
 * 요금제가 아니라 CarrierBrand를 직접 저장하고 빈 drafts 성공을 반환한다.
 */
class BrandListCrawler(
    source: CrawlSource,
    private val db: PlanDatabase,
) : BaseCrawler(source) {

    override val sourceName: String = "알뜰폰허브 브랜드목록"

    override fun defaultConfig(): CrawlerSelectorConfig = CrawlerSelectorConfig(
        listPageUrl = "https://www.mvnohub.kr/brand.do",
        itemSelector = ".brand_box_list .box",
        maxPages = 1,
    )

    override suspend fun crawl(): Result<List<PlanDraft>> {
        return try {
            logStart()
            val config = config()
            val html = fetchGet(config.listPageUrl)
            val doc = parseHtml(html, config.listPageUrl)
            val boxes = doc.select(config.itemSelector)
            if (boxes.isEmpty()) {
                throw IllegalStateException("브랜드 목록이 비어 있음")
            }
            val now = System.currentTimeMillis()
            var order = 0
            val brands = boxes.mapNotNull { box ->
                val planLink = box.select("a[href*=brand/plan/]").firstOrNull()?.attr("abs:href")
                    ?: return@mapNotNull null
                val id = Regex("""/brand/plan/(\d+)""").find(planLink)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val name = box.select("img.logo[alt]").firstOrNull()?.attr("alt")?.trim()
                    .takeIf { !it.isNullOrBlank() }
                    ?: box.select(".logo_div p span").firstOrNull()?.text()?.trim()
                    ?: return@mapNotNull null
                val networkRaw = box.select("p.gb").firstOrNull()?.text()?.trim()
                    ?.removePrefix("통신망")?.trim() ?: ""
                val homepage = box.select("a.link_a[href]").firstOrNull()?.attr("abs:href")
                val logo = box.select("img.logo[src]").firstOrNull()?.attr("abs:src")
                CarrierBrand(
                    id = "hub-brand-$id",
                    name = name,
                    networkType = networkRaw.ifBlank { "UNKNOWN" },
                    logoUrl = logo?.takeIf { it.isNotBlank() },
                    homepageUrl = homepage?.takeIf { it.isNotBlank() },
                    planListUrl = planLink,
                    isActive = true,
                    sortOrder = order++,
                    collectedAt = now,
                )
            }
            db.carrierBrandDao().upsertAll(brands)
            DebugLogger.i("브랜드", "브랜드 저장 완료 count=${brands.size}")
            logDone(brands.size)
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
