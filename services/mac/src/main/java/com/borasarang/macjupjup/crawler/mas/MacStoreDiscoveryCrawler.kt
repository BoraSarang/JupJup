package com.borasarang.macjupjup.crawler.mas

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.chart.ChartRssCrawler
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.AppleCategoryMap
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * T-060: MAS 키워드 디스커버리 (US, 키 불필요, 24h).
 * 차트 Top100 밖 롱테일 발견용. iTunes Search API(entity=macSoftware) 키워드 분할 탐색.
 * - 홈페이지는 스토어에 없음 → null (스토어 버튼은 trackId로 유도, 둔갑 금지)
 * - 상세 보강(스크린샷·버전·노트)은 iTunesLookupPoller가 이어받음
 * - 키워드당 HTTP 1회 + 예의 대기 (24개 ≈ 25초/회)
 */
class MacStoreDiscoveryCrawler(
    source: CrawlSource,
    private val keywords: List<String> = DEFAULT_KEYWORDS,
    /** iTunes Search API 상한 200 */
    private val limit: Int = 200,
) : BaseCrawler(source) {

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        DebugLogger.i("수집", "[FEATURE] MAS 키워드 발견 시작 keywords=${keywords.size}")
        crawlEach(
            inputs = keywords,
            label = "MAS 키워드 발견",
            dedupKey = { it.app.trackId ?: it.app.id },
        ) { kw ->
            parseSearch(fetchGet(com.borasarang.macjupjup.crawler.itunes.itunesSearchUrl(kw, limit)))
        }
    }

    internal fun parseSearch(body: String): List<AppDraft> {
        val arr = try {
            Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        } catch (_: Exception) {
            parseFail("MAS 발견 응답")
        }
        return arr.mapNotNull { el ->
            try {
                val o = el.jsonObject
                val trackId = o["trackId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return@mapNotNull null
                val name = o.str("trackName") ?: return@mapNotNull null
                val seller = o.str("sellerName") ?: o.str("artistName") ?: "Unknown"
                val price = o["trackPrice"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: o["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val currency = o.str("currency") ?: "USD"
                val genre = o.str("primaryGenreName") ?: ""
                val version = o.str("version")
                val notes = o.str("releaseNotes")
                val artwork = o.str("artworkUrl512") ?: o.str("artworkUrl100")
                val rating = o["averageUserRating"]?.jsonPrimitive?.content?.toDoubleOrNull()
                val ratingCount = o["userRatingCount"]?.jsonPrimitive?.content?.toIntOrNull()
                val shots = o["screenshotUrls"]?.jsonArray?.mapNotNull {
                    if (it is JsonNull) null else it.jsonPrimitive.content
                }
                val trackViewUrl = o.str("trackViewUrl")
                val releaseDate = o.str("currentVersionReleaseDate")?.let {
                    ChartRssCrawler.parseDate(it)
                }
                val draft = buildDraft(
                    name = name,
                    developer = seller,
                    price = price,
                    trackId = trackId,
                    version = version,
                    releaseNotesSummary = notes?.take(500),
                    releaseNotes = notes?.take(2000),
                    releaseDate = releaseDate,
                    descriptionSnippet = o.str("description")?.take(2000),
                    detailUrl = trackViewUrl ?: "https://apps.apple.com/us/app/id$trackId",
                ) ?: return@mapNotNull null
                val app = draft.app.copy(
                    category = AppleCategoryMap.map(genre),
                    currency = currency,
                    iconUrl = artwork,
                    averageRating = rating,
                    ratingCount = ratingCount,
                    sellerName = seller,
                    screenshotUrls = shots?.take(10)?.joinToString("\n"),
                    fileSize = o.str("fileSizeBytes")?.toLongOrNull(),
                    minOs = o.str("minimumOsVersion"),
                    contentRating = o.str("trackContentRating"),
                    topics = genre.ifBlank { null },
                )
                draft.copy(app = app)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        /** 차트 밖 롱테일 포착용 기능 키워드 24개 */
        val DEFAULT_KEYWORDS = listOf(
            "menu bar manager", "clipboard manager", "tab manager",
            "audio recorder", "file manager", "pdf editor",
            "window manager", "app launcher", "note taking",
            "todo list", "pomodoro timer", "calendar",
            "weather", "password manager", "backup",
            "mac cleaner", "markdown editor", "rss reader",
            "translator", "color picker", "screenshot",
            "video converter", "habit tracker", "focus timer",
        )
    }
}
