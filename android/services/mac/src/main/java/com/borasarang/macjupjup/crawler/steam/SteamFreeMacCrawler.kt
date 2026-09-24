package com.borasarang.macjupjup.crawler.steam

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.category.GameGenres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * Steam 무료 macOS 게임 (PLAN_v21).
 * F2P 장르 페이지 HTML은 JS 렌더 → search/results JSON API 사용
 * (maxprice=free + os=mac, Referer 헤더 필수). 상위 PAGE_SIZE E건.
 * tagids로 1차 장르 추출, 전건 appdetails로 genres·소개본문·스크린샷·요건 보강.
 * descriptionSnippet = 짧은소개 + — README — + about_the_game + — SYSREQ — + 요건 HTML.
 */
class SteamFreeMacCrawler(
    source: CrawlSource,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val detailLimit: Int = DETAIL_LIMIT,
) : BaseCrawler(source) {

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val url = SEARCH_URL +
            "&start=0&count=$pageSize&sort_by=Released_DESC" +
            "&cc=US&l=english&infinite=1" +
            "&maxprice=free&category1=998&os=mac"
        val body = fetchGetHeaders(
            url,
            mapOf(
                "Referer" to "https://store.steampowered.com/",
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
            ),
        )
        val drafts = parseSearch(retryNonJson(body, url))
        politenessDelay()
        // 전건 appdetails 보강 (본문·스크린샷·요건 누락 방지, 예의 딜레이)
        val limit = detailLimit.coerceAtLeast(drafts.size)
        val enriched = drafts.take(limit).mapNotNull { d ->
            try {
                val appid = STEAM_ID.find(d.app.topics ?: "")?.groupValues?.get(1)
                    ?: return@mapNotNull d
                val detail = fetchGet(DETAIL_URL + appid)
                politenessDelay()
                enrich(d, detail)
            } catch (e: Exception) {
                DebugLogger.w("수집", "Steam appdetails 스킵: ${e.message}")
                d
            }
        }
        val rest = drafts.drop(limit)
        val all = (enriched + rest).let { dedupById(it) }
        DebugLogger.i("수집", "[FEATURE] Steam 무료 맥 게임 found=${all.size}")
        all
    }

    /**
     * Steam이 간헐적으로 JSON 대신 HTML(봇 차단·리다이렉트)을 반환.
     * 본문에 search_result_row가 있으면 HTML로, 아니면 1회 재요청.
     */
    internal suspend fun retryNonJson(body: String, url: String): String {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("{")) return body
        if (body.contains("search_result_row")) return body
        politenessDelay()
        return try {
            fetchGetHeaders(
                url,
                mapOf(
                    "Referer" to "https://store.steampowered.com/",
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                ),
            )
        } catch (_: Exception) {
            body
        }
    }

    /** search/results JSON 또는 검색 HTML → search_result_row 파싱 */
    internal fun parseSearch(body: String): List<AppDraft> {
        val html = if (body.trimStart().startsWith("{")) {
            try {
                val el = Json.parseToJsonElement(body).jsonObject
                if (el["success"]?.jsonPrimitive?.content == "0") return emptyList()
                el["results_html"]?.jsonPrimitive?.content ?: return emptyList()
            } catch (_: Exception) {
                if (body.contains("search_result_row")) body else parseFail("Steam 검색 응답")
            }
        } else if (body.contains("search_result_row")) {
            body
        } else {
            parseFail("Steam 검색 응답")
        }
        if (html.isBlank() || !html.contains("search_result_row")) return emptyList()
        val doc = Jsoup.parse(html, "", Parser.htmlParser())
        val rows = doc.select("a.search_result_row")
        return rows.mapNotNull { row ->
            try {
                val appId = row.attr("data-ds-appid").trim()
                if (appId.isBlank()) return@mapNotNull null
                // macOS 플랫폼 배지 필수 (API가 os=mac이어도 방어)
                if (row.select("span.platform_img.mac").isEmpty()) return@mapNotNull null
                val name = row.select("span.title").firstOrNull()?.text()?.trim() ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                val href = row.attr("href").ifBlank {
                    "https://store.steampowered.com/app/$appId/"
                }
                val capsule = row.select(".search_capsule img").firstOrNull()?.attr("src")
                val releaseText = row.select("div.search_released").firstOrNull()?.text()?.trim()
                val releaseDate = releaseText?.let { parseReleaseDate(it) }
                val tagIdsRaw = row.attr("data-ds-tagids")
                val tagIds = TAGIDS.find(tagIdsRaw)?.groupValues?.get(1)
                    ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                    ?: tagIdsRaw.trim('[', ']').split(",").mapNotNull { it.trim().toIntOrNull() }
                val genre = GameGenres.fromTagIds(tagIds)
                val tags = GameGenres.gameTags(genre, STORE_TAG)
                    .plus("steam-appid:$appId")
                    .joinToString(",")
                val draft = buildDraft(
                    name = name,
                    developer = DEVELOPER,
                    descriptionSnippet = null,
                    homepageUrl = href,
                    releaseDate = releaseDate,
                    iconUrl = capsule,
                    topics = listOf("game", "steam", "steam-appid:$appId"),
                    detailUrl = href,
                ) ?: return@mapNotNull null
                draft.copy(
                    app = draft.app.copy(
                        category = Constants.CATEGORY_GAME,
                        tags = tags,
                        license = Constants.LICENSE_FREE,
                        price = 0.0,
                    ),
                )
            } catch (_: Exception) {
                null
            }
        }.let { dedupById(it) }
    }

    /** appdetails JSON → 장르/캡슐/플랫폼 보강. non-mac이면 null(스킵) */
    internal fun enrich(draft: AppDraft, detailBody: String): AppDraft? {
        val appid = STEAM_ID.find(draft.app.topics ?: "")?.groupValues?.get(1) ?: return draft
        val root = try {
            Json.parseToJsonElement(detailBody).jsonObject[appid]?.jsonObject
        } catch (_: Exception) {
            return draft
        } ?: return draft
        if (root["success"]?.jsonPrimitive?.content == "false") return null
        val data = try {
            root["data"]?.jsonObject
        } catch (_: Exception) {
            return draft
        } ?: return draft
        val platforms = data["platforms"]?.let {
            try { it.jsonObject } catch (_: Exception) { null }
        }
        val mac = platforms?.get("mac")?.jsonPrimitive?.content
        if (mac == "false") return null
        val genreNames = try {
            (data["genres"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                try {
                    (el as? kotlinx.serialization.json.JsonObject)?.get("description")
                        ?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }?.filter { it != "Free To Play" }.orEmpty()
        val genre = genreNames.firstNotNullOfOrNull { GameGenres.fromEnName(it) }
            ?: GameGenres.fromTagIds(emptyList())
            ?: genreNames.firstOrNull()
        val header = data["header_image"]?.jsonPrimitive?.content
            ?: data["capsule_image"]?.jsonPrimitive?.content
        val shortDesc = data["short_description"]?.jsonPrimitive?.content
        val aboutHtml = data["about_the_game"]?.jsonPrimitive?.content
            ?: data["detailed_description"]?.jsonPrimitive?.content
        val name = data["name"]?.jsonPrimitive?.content
        val website = data["website"]?.jsonPrimitive?.content
        val shots = parseScreenshots(data["screenshots"])
        val osTags = buildList {
            platforms?.get("windows")?.jsonPrimitive?.content?.let { if (it == "true") add("windows") }
            platforms?.get("linux")?.jsonPrimitive?.content?.let { if (it == "true") add("linux") }
        }
        val tags = GameGenres.gameTags(genre, STORE_TAG)
            .plus("steam-appid:$appid")
            .plus(genreNames.map { it.lowercase().replace(' ', '-') })
            .plus(osTags)
            .distinct()
            .joinToString(",")
        val desc = composeDescription(
            shortDesc = shortDesc,
            aboutMd = aboutHtmlToMarkdown(aboutHtml),
            sysReqHtml = buildSysReqHtml(data["mac_requirements"], data["pc_requirements"]),
            fallback = draft.app.descriptionSnippet,
        )
        val shortSnip = shortDesc?.take(Constants.APP_SUMMARY_LEN)
            ?: desc?.take(Constants.APP_SUMMARY_LEN)
            ?: draft.app.descriptionSnippet
        val app = draft.app.copy(
            name = name?.takeIf { it.isNotBlank() } ?: draft.app.name,
            category = Constants.CATEGORY_GAME,
            tags = tags,
            license = Constants.LICENSE_FREE,
            price = 0.0,
            iconUrl = header ?: draft.app.iconUrl,
            descriptionSnippet = shortSnip,
            longDescription = desc?.take(Constants.APP_BODY_MAX) ?: draft.app.longDescription,
            screenshotUrls = shots ?: draft.app.screenshotUrls,
            minOs = "macOS",
            homepageUrl = website?.takeIf { it.startsWith("http") } ?: draft.app.homepageUrl,
            sellerName = null,
            supportedLanguages = parseSupportedLanguages(data["supported_languages"])
                ?: draft.app.supportedLanguages,
        )
        return draft.copy(app = app)
    }

    /** 소개: 짧은 설명 + README 마커 + 전체 본문 + SYSREQ 마커 + 시스템요건 HTML */
    internal fun composeDescription(
        shortDesc: String?,
        aboutMd: String,
        sysReqHtml: String,
        fallback: String?,
    ): String? = SteamDetails.composeDescription(shortDesc, aboutMd, sysReqHtml, fallback)

    /** Steam bb HTML 소개 → 마크다운 텍스트 */
    internal fun aboutHtmlToMarkdown(html: String?): String = SteamDetails.aboutHtmlToMarkdown(html)

    /**
     * 시스템 요구 사항 HTML (모달에서 HTML 그대로 표시).
     * mac_requirements 빈 배열이면 macOS 지원 한 줄 + PC 요건(있는 경우)만.
     */
    internal fun buildSysReqHtml(
        macReq: kotlinx.serialization.json.JsonElement?,
        pcReq: kotlinx.serialization.json.JsonElement?,
    ): String = SteamDetails.buildSysReqHtml(macReq, pcReq)

    /** screenshots[] → path_thumbnail(또는 full) 최대 10장, 개행 CSV */
    internal fun parseScreenshots(raw: kotlinx.serialization.json.JsonElement?): String? =
        SteamDetails.parseScreenshots(raw)

    /**
     * appdetails supported_languages → ISO 639-1 CSV (예: en,ko,ja).
     * 객체(키=언어명)와 HTML/영어 설명 문자열 두 형태를 모두 허용.
     */
    internal fun parseSupportedLanguages(raw: kotlinx.serialization.json.JsonElement?): String? =
        SteamDetails.parseSupportedLanguages(raw)

    /** "Sep 22, 2026" → epoch ms */
    internal fun parseReleaseDate(text: String): Long? = try {
        java.time.LocalDate.parse(text.trim(), java.time.format.DateTimeFormatter.ofPattern("d MMM, yyyy", java.util.Locale.US))
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.LocalDate.parse(text.trim(), java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEVELOPER = "Steam"
        const val STORE_TAG = "steam"
        const val DEFAULT_PAGE_SIZE = 40
        const val DETAIL_LIMIT = 40
        /** 게임 소개 본문 상한 (앱 snippet 2000과 분리) */
        const val GAME_DESC_MAX = SteamDetails.GAME_DESC_MAX
        /** 소개/본문 구분 (프론트 excerpt·splitBody 공용) */
        const val README_MARKER = SteamDetails.README_MARKER
        /** 시스템 요구 사항 구분 (프론트 splitSysReq 공용, HTML 저장) */
        const val SYSREQ_MARKER = SteamDetails.SYSREQ_MARKER
        const val SEARCH_URL =
            "https://store.steampowered.com/search/results/?query=&infinite=1"
        const val DETAIL_URL = SteamDetails.DETAIL_URL
        private val TAGIDS = Regex("""\[([^\]]+)]""")
        private val STEAM_ID = Regex("""steam-appid:(\d+)""")
    }
}
