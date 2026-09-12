package com.borasarang.planjupjup.crawler

import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 크롤러 공통 베이스. 네트워크 I/O는 IO 디스패처, 요청 간 예의 대기.
 * 한 크롤러의 실패는 예외로 상위에 전달 → Worker가 격리 처리.
 */
abstract class BaseCrawler(
    protected val source: CrawlSource,
) : PlanCrawler {

    /** DB 오버라이드 우선, 없으면 코드 기본값 */
    protected open fun config(): CrawlerSelectorConfig = CrawlerSelectorConfig.fromJson(source.selectorConfigJson)
        ?: defaultConfig()

    protected abstract fun defaultConfig(): CrawlerSelectorConfig

    protected suspend fun fetchGet(url: String): String = withContext(Dispatchers.IO) {
        val result = CrawlHttp.get(url)
        if (!result.isOk) {
            throw IllegalStateException("GET 실패 url=$url code=${result.code} err=${result.error}")
        }
        result.body
    }

    protected suspend fun fetchPost(url: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val result = CrawlHttp.postForm(url, params)
            if (!result.isOk) {
                throw IllegalStateException("POST 실패 url=$url code=${result.code} err=${result.error}")
            }
            result.body
        }

    protected suspend fun politenessDelay() {
        delay(Constants.CRAWL_REQUEST_DELAY_MS)
    }

    protected fun parseHtml(html: String, baseUrl: String) = Jsoup.parse(html, baseUrl)

    /** 라벨 제거: li.wifi "데이터 10GB+1Mbps" → "10GB+1Mbps" */
    protected fun stripLabel(element: Element?, vararg labels: String): String {
        if (element == null) return ""
        var text = element.text().trim()
        labels.forEach { text = text.replace(it, "") }
        return text.trim()
    }

    protected fun buildDraft(
        carrierName: String,
        planName: String,
        price: Int,
        priceAfterDiscount: Int?,
        discountMonths: Int?,
        dataAmount: String,
        voice: String,
        sms: String,
        networkType: String,
        mvnoNetwork: String,
        eventBadge: String?,
        detailUrl: String,
        rawHtml: String?,
    ): PlanDraft? {
        if (carrierName.isBlank() || planName.isBlank()) return null
        val id = MergeUtils.generateId(carrierName, planName)
        val now = System.currentTimeMillis()
        val plan = Plan(
            id = id,
            carrierName = carrierName,
            mvnoNetwork = mvnoNetwork,
            planName = planName,
            price = price,
            priceAfterDiscount = priceAfterDiscount,
            discountMonths = discountMonths,
            dataAmount = dataAmount.ifBlank { "0GB" },
            voice = voice.ifBlank { "무제한" },
            sms = sms.ifBlank { "무제한" },
            networkType = networkType,
            eventBadge = eventBadge?.takeIf { it.isNotBlank() },
            collectedAt = now,
            // 최초 수집 시각은 저장 시점에 확정 (신규=now, 갱신=기존 유지)
            firstCollectedAt = 0L,
            isNew = false,
            sourceId = source.id,
            tags = MergeUtils.generateTags(price, dataAmount),
        )
        val mapping = PlanSourceMapping(
            planId = id,
            sourceName = sourceName,
            sourceUrl = detailUrl,
            fetchedAt = now,
            rawDataJson = rawHtml?.take(4000),
        )
        return PlanDraft(plan, listOf(mapping))
    }

    protected fun logStart() {
        DebugLogger.i("수집", "수집 시작 source=${source.name}")
    }

    protected fun logDone(count: Int) {
        DebugLogger.i("수집", "수집 완료 source=${source.name} count=$count")
    }
}
