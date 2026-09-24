package com.borasarang.macjupjup.crawler.chart

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.category.AppleCategoryMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mac App Store 차트 RSS (US, 키 불필요, 차트 1개 = HTTP 1회).
 * topfreemacapps / toppaidmacapps / topgrossingmacapps, limit=100 (상한).
 * 버전·스크린샷 등 상세는 iTunesLookupPoller(T-032)가 보완.
 */
class ChartRssCrawler(
    source: CrawlSource,
    private val feeds: List<ChartFeed> = DEFAULT_FEEDS,
) : BaseCrawler(source) {

    data class ChartFeed(val name: String, val url: String)

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        crawlEach(feeds, "차트 RSS") { feed ->
            parseChart(fetchGet(feed.url), feed.name)
        }
    }

    internal fun parseChart(body: String, chartName: String): List<AppDraft> {
        val entries = try {
            Json.parseToJsonElement(body).jsonObject["feed"]
                ?.jsonObject?.get("entry")?.jsonArray ?: return emptyList()
        } catch (_: Exception) {
            parseFail("차트 RSS")
        }
        return entries.mapNotNull { el ->
            try {
                val o = el.jsonObject
                fun label(key: String): String? {
                    val v = o[key]?.jsonObject?.get("label") ?: return null
                    if (v is JsonNull) return null
                    return v.jsonPrimitive.content
                }
                val name = label("im:name") ?: return@mapNotNull null
                val developer = label("im:artist") ?: "Unknown"
                val idObj = o["id"]?.jsonObject
                val trackId = idObj?.get("attributes")?.jsonObject?.get("im:id")
                    ?.jsonPrimitive?.content?.toLongOrNull()
                val appleCategory = o["category"]?.jsonObject?.get("attributes")
                    ?.jsonObject?.get("label")?.jsonPrimitive?.content ?: ""
                val priceObj = o["im:price"]?.jsonObject
                val price = priceObj?.get("attributes")?.jsonObject?.get("amount")
                    ?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val currency = priceObj?.get("attributes")?.jsonObject?.get("currency")
                    ?.jsonPrimitive?.content ?: "USD"
                val releaseDate = label("im:releaseDate")?.let { parseDate(it) }
                val link = o["link"]?.jsonObject?.get("attributes")
                    ?.jsonObject?.get("href")?.jsonPrimitive?.content
                val draft = buildDraft(
                    name = name,
                    developer = developer,
                    price = price,
                    trackId = trackId,
                    detailUrl = link ?: "https://apps.apple.com/us/search?term=${name}",
                ) ?: return@mapNotNull null
                // Apple 카테고리 매핑이 키워드 추론보다 정확 — 덮어쓰기
                val app = draft.app.copy(
                    category = AppleCategoryMap.map(appleCategory),
                    currency = currency,
                    releaseDate = releaseDate,
                )
                draft.copy(app = app)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        // Apple 측에서 유료/매출 Mac 차트는 빈 배열 반환(2026-09 확인) — 무료만 수집.
        // 유료 커버리지는 MAS 키워드 발견 + lookup 보완으로 확보.
        val DEFAULT_FEEDS = listOf(
            ChartFeed("free", "https://itunes.apple.com/us/rss/topfreemacapps/limit=100/json"),
        )

        /** "2024-01-01T00:00:00-07:00" 등 오프셋 포함 ISO → epoch */
        fun parseDate(iso: String): Long? = try {
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(iso).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
