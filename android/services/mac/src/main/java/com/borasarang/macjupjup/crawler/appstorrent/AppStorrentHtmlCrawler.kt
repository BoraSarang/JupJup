package com.borasarang.macjupjup.crawler.appstorrent

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.category.GameGenres
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * AppStorrent (appstorrent.ru) 메타데이터 크롤러 — 게임/프로그램 공용 (PLAN_v22).
 * 목록은 `article.games-item` / `article.soft-item`, 상세는 `#tabs-1` + `.screenshots`.
 * Cloudflare는 일반 브라우저 UA로 403 — **Googlebot UA**로 메타데이터만 수집.
 * **다운로드 URI(magnet/torrent/파일)는 수집·저장 금지** — 출처 상세 링크만 유지.
 * 목록 0건/차단 시 ChallengeFail → 소스 FAILED 격리. 상세 실패는 목록 초안 유지.
 */
class AppStorrentHtmlCrawler(
    source: CrawlSource,
    private val mode: Mode = Mode.fromSourceId(source.id),
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val pageLimit: Int = DEFAULT_PAGE_LIMIT,
    private val detailLimit: Int = DETAIL_LIMIT,
    private val loadBodyIds: suspend (List<String>) -> Set<String> = { emptySet() },
    private val onCheckpoint: suspend (List<AppDraft>) -> Unit = {},
) : BaseCrawler(source) {

    enum class Mode { GAMES, PROGRAMS;
        companion object {
            fun fromSourceId(id: String): Mode =
                if (id.contains("program")) PROGRAMS else GAMES
        }
    }

    /** CF 차단·목록 전체 실패 — catch 스킵 없이 Result.failure → 소스 FAILED */
    private class ChallengeFail(message: String) : IllegalStateException(message)

    override suspend fun crawl(): Result<List<AppDraft>> {
        // runCatching은 CancellationException까지 삼켜 워커 취소 시 원인 소실 — 명시 분기
        return try {
            Result.success(crawlInternal())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun crawlInternal(): List<AppDraft> {
        val pages = listOfNotNull(listBaseUrl)
        val drafts = mutableListOf<AppDraft>()
        var pageCount = 0
        for (base in pages) {
            if (pageCount >= pageLimit) break
            for (p in 1..pageLimit) {
                if (pageCount >= pageLimit) break
                val url = if (p == 1) base else base.trimEnd('/') + "/page/$p/"
                try {
                    val body = fetchGetHeaders(url, BROWSER_HEADERS)
                    if (isChallenge(body)) throw ChallengeFail("Cloudflare challenge url=$url")
                    val found = parseList(body, url, mode)
                    drafts += found
                    pageCount++
                    if (found.isEmpty()) break
                } catch (e: ChallengeFail) {
                    throw e
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isChallenge(e.message ?: "") || isBlockedHttp(e.message ?: "")) {
                        throw ChallengeFail(e.message ?: "blocked")
                    }
                    DebugLogger.w("수집", "AppStorrent 목록 스킵 $url: ${e.message}")
                    break
                }
                politenessDelay()
            }
        }
        var all = dedupById(drafts)
        if (all.isEmpty()) {
            throw ChallengeFail("AppStorrent ${mode} 수집 0건 — 차단 또는 파싱 실패")
        }
        val limit = detailLimit.coerceAtLeast(0)
        if (limit > 0) {
            // 목록 초안은 전부 본문 null → DB 보유 id 기준으로 공백분 선행 (같은 head 반복 방지)
            val bodyIds = try {
                loadBodyIds(all.map { it.app.id })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.w("수집", "AppStorrent 본문 id 조회 스킵: ${e.message}")
                emptySet()
            }
            val prioritized = prioritizeForDetail(all, bodyIds)
            val head = prioritized.take(limit)
            val enriched = mutableListOf<AppDraft>()
            for (d in head) {
                try {
                    val detailUrl = d.app.homepageUrl
                    val next = if (detailUrl == null) {
                        d
                    } else {
                        val html = fetchGetHeaders(detailUrl, BROWSER_HEADERS)
                        if (isChallenge(html)) d else (enrichDetail(d, html) ?: d)
                    }
                    val bodyAdded = next.app.longDescription != d.app.longDescription ||
                        next.app.descriptionSnippet != d.app.descriptionSnippet
                    if (bodyAdded) {
                        enriched += next
                        // 10건마다 체크포인트 — 저장 전 취소로 진행량 유실 방지
                        if (enriched.size % CHECKPOINT_EVERY == 0) {
                            onCheckpoint(enriched.takeLast(CHECKPOINT_EVERY))
                        }
                    }
                    if (detailUrl != null) politenessDelay()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (enriched.isNotEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            onCheckpoint(enriched)
                        }
                    }
                    throw e
                } catch (e: Exception) {
                    DebugLogger.w("수집", "AppStorrent 상세 스킵 ${d.app.name}: ${e.message}")
                }
            }
            if (enriched.isNotEmpty()) {
                val rest = enriched.size % CHECKPOINT_EVERY
                if (rest != 0) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        onCheckpoint(enriched.takeLast(rest))
                    }
                } else {
                    // 이미 배치로 저장됨 — 최신 enrich 결과를 head에 반영만 수행
                }
            }
            val byId = enriched.associateBy { it.app.id }
            all = dedupById(
                head.map { byId[it.app.id] ?: it } + prioritized.drop(limit),
            )
        }
        val missing = all.count {
            it.app.longDescription.isNullOrBlank() && it.app.descriptionSnippet.isNullOrBlank()
        }
        DebugLogger.i(
            "수집",
            "[FEATURE] AppStorrent ${mode} found=${all.size} missingBody=$missing detailLimit=$detailLimit",
        )
        return all
    }

    /**
     * 소개·전문 모두 없는 초안이 선행 — 재수집 시마다 공백분을 회전 보강.
     * 목록 초안은 description null이므로 `dbHasBodyIds`(DB 보유 id)와 초안 본문을 함께 본다.
     */
    internal fun prioritizeForDetail(
        drafts: List<AppDraft>,
        dbHasBodyIds: Set<String> = emptySet(),
    ): List<AppDraft> =
        drafts.sortedBy { d ->
            val hasBody = dbHasBodyIds.contains(d.app.id) ||
                !d.app.longDescription.isNullOrBlank() ||
                !d.app.descriptionSnippet.isNullOrBlank()
            if (hasBody) 1 else 0
        }

    private fun isBlockedHttp(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("code=403") || m.contains("code=429") ||
            m.contains("code=503") || m.contains("http 403") ||
            m.contains("http 429") || m.contains("http 503")
    }

    private val listBaseUrl: String?
        get() = when (mode) {
            Mode.GAMES -> source.baseUrl?.trimEnd('/')?.plus("/games")
            Mode.PROGRAMS -> source.baseUrl?.trimEnd('/')?.plus("/programs")
        } ?: when (mode) {
            Mode.GAMES -> "https://appstorrent.ru/games"
            Mode.PROGRAMS -> "https://appstorrent.ru/programs"
        }

    private fun isChallenge(s: String): Boolean {
        val t = s.trimStart()
        if (t.length < 2000 && (t.startsWith("{") || t.startsWith("["))) return false
        return s.contains("Just a moment", ignoreCase = true) ||
            s.contains("cf-browser-verification", ignoreCase = true) ||
            s.contains("보안 확인", ignoreCase = true) ||
            s.contains("cf-mitigated", ignoreCase = true)
    }

    /**
     * 목록 HTML → AppDraft.
     * 셀렉터: `article.games-item` (게임) / `article.soft-item` (프로그램).
     * lastcomm·뉴스 블록은 이 셀렉터 밖이라 제외됨.
     */
    internal fun parseList(html: String, pageUrl: String, mode: Mode): List<AppDraft> {
        val doc = Jsoup.parse(html, pageUrl, Parser.htmlParser())
        val selector = when (mode) {
            Mode.GAMES -> "article.games-item"
            Mode.PROGRAMS -> "article.soft-item"
        }
        var roots: List<Element> = doc.select(selector)
        if (roots.isEmpty()) {
            roots = doc.select("article.games-item, article.soft-item")
                .filter { root -> matchesMode(root, mode) }
        }
        val out = mutableListOf<AppDraft>()
        for (root in roots) {
            val draft = parseRoot(root, pageUrl, mode) ?: continue
            out += draft
        }
        return dedupById(out)
    }

    private fun matchesMode(root: Element, mode: Mode): Boolean {
        val hrefs = root.select("a[href]").map { it.attr("href") }
        val hasGames = hrefs.any { it.contains("/games/") }
        val hasPrograms = hrefs.any { it.contains("/programs/") }
        return when (mode) {
            Mode.GAMES -> hasGames && !hasPrograms || root.hasClass("games-item")
            Mode.PROGRAMS -> hasPrograms || root.hasClass("soft-item")
        }
    }

    private fun parseRoot(root: Element, pageUrl: String, mode: Mode): AppDraft? {
        val link = root.selectFirst("a[href~=\\d+-[^/]+\\.html$]")
            ?: root.selectFirst(".subtitle a[href], .info a[href], a.link-title[href]")
            ?: return null
        val href = absUrl(link.attr("href"), pageUrl) ?: return null
        if (!href.contains(".html")) return null

        val name = root.selectFirst(".subtitle h2, .info h2.body-2, h2.body-2")
            ?.text()?.trim()
            ?.ifBlank { null }
            ?: (link.attr("title").ifBlank { link.text() }).trim().substringBefore(" Icon").trim()
        if (name.isBlank() || name.length < 2) return null

        val icon = root.selectFirst(".icon img, img.xfieldimage, img")?.let { img ->
            (img.attr("data-src").ifBlank { img.attr("src") }).ifBlank { null }
        }?.let { absUrl(it, pageUrl) }

        val genreSlug = genreSlugFrom(root, pageUrl, mode)
        val version = root.selectFirst(".version, .caption-1.version")?.text()?.trim()?.ifBlank { null }
            ?: parseVersion(root.text())
        val date = parseDate(root.text())

        val storeTag = STORE_TAG
        val tags = if (mode == Mode.GAMES) {
            GameGenres.gameTags(GameGenres.fromSlug(genreSlug), storeTag)
        } else {
            listOf(storeTag)
        }.filter { it.isNotBlank() }.distinct().joinToString(",")

        val topics = buildList {
            if (mode == Mode.GAMES) add(Constants.TAG_GAME)
            add("appstorrent")
            genreSlug?.let { add("genre:$it") }
        }

        val draft = buildDraft(
            name = name,
            developer = DEVELOPER,
            descriptionSnippet = null,
            homepageUrl = href,
            releaseDate = date,
            iconUrl = icon,
            version = version,
            topics = topics,
            detailUrl = href,
        ) ?: return null
        return draft.copy(
            app = draft.app.copy(
                category = if (mode == Mode.GAMES) {
                    Constants.CATEGORY_GAME
                } else if (draft.app.category == Constants.CATEGORY_GAME) {
                    "유틸리티"
                } else {
                    draft.app.category
                },
                tags = tags.ifBlank { null },
                license = Constants.LICENSE_FREE,
                price = 0.0,
                minOs = "macOS",
            ),
        )
    }

    /** 게임: `/games/{slug}/` 카테고리 링크 슬러그, 프로그램: null */
    private fun genreSlugFrom(root: Element, pageUrl: String, mode: Mode): String? {
        if (mode != Mode.GAMES) return null
        val catLink = root.selectFirst(".category a[href*=games/], .tags_plugin a[href*=games/]")
            ?: root.select("a[href]").firstOrNull { it.attr("href").contains("/games/") }
            ?: return null
        val href = absUrl(catLink.attr("href"), pageUrl) ?: return null
        val path = try {
            URI(href).path
        } catch (_: Exception) {
            return null
        }
        val m = Regex("""/games/([a-z0-9-]+)""").find(path) ?: return null
        val slug = m.groupValues[1]
        return slug.takeIf { it.isNotBlank() && it != "games" }
    }

    /**
     * 상세 HTML → 본문 요약(다운로드 URI 제거)·아이콘·스크린샷.
     * 본문: `#tabs-1` (`.body-content` 우선), 스크린샷: `.screenshots img`, 아이콘: `og:image`.
     */
    internal fun enrichDetail(draft: AppDraft, html: String): AppDraft? {
        val base = draft.app.homepageUrl ?: "https://appstorrent.ru/"
        val doc = Jsoup.parse(html, base, Parser.htmlParser())
        val raw = extractDescription(doc)
        val cleaned = stripDownloadText(raw).take(GAME_DESC_MAX)
        val title = (doc.selectFirst("h1")?.text() ?: "").trim().ifBlank { null }
        val icon = doc.selectFirst("meta[property=og:image], .main-post .icon img, .icon img")?.let {
            when {
                it.tagName() == "meta" -> it.attr("content")
                else -> (it.attr("data-src").ifBlank { it.attr("src") })
            }
        }?.ifBlank { null }?.let { absUrl(it, base) }

        val shots = doc.select(".screenshots img, .gallery img, .slideshow img")
            .mapNotNull { img ->
                val s = (img.attr("data-src").ifBlank { img.attr("src") }).ifBlank { null }
                    ?: return@mapNotNull null
                absUrl(s, base)
            }
            .filter { u -> DOWNLOAD_URI.none { p -> Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(u) } }
            .filter { !it.isNullOrBlank() }
            .distinct()
            .take(10)

        val version = doc.selectFirst(".version, .caption-1.version")?.text()?.trim()?.ifBlank { null }
            ?: parseVersion(doc.selectFirst("h1")?.parent()?.text() ?: doc.text())
            ?: draft.app.version
        val desc = if (cleaned.isNotBlank()) {
            composeSnippet(draft.app.descriptionSnippet, cleaned)
        } else {
            draft.app.descriptionSnippet
        }
        val app = draft.app.copy(
            name = title?.takeIf { t ->
                t.isNotBlank() && !t.contains("appstorrent", ignoreCase = true) &&
                    !t.contains("Crack", ignoreCase = true)
            } ?: draft.app.name,
            descriptionSnippet = cleaned.take(Constants.APP_SUMMARY_LEN)
                .ifBlank { desc?.take(Constants.APP_SUMMARY_LEN) },
            longDescription = cleaned.take(Constants.APP_BODY_MAX)
                .ifBlank { draft.app.longDescription },
            iconUrl = icon ?: draft.app.iconUrl,
            screenshotUrls = shots.filterNotNull().joinToString("\n")
                .ifBlank { draft.app.screenshotUrls },
            version = version,
            minOs = "macOS",
            license = Constants.LICENSE_FREE,
            price = 0.0,
            category = if (mode == Mode.GAMES) Constants.CATEGORY_GAME else draft.app.category,
        )
        return draft.copy(app = app)
    }

    /**
     * `#tabs-1` → `.body-content` 우선, 없으면 탭 본문 · 끝 마커 절단.
     * 페이지 전체(`body`) 폴백은 내비/푸터 쓰레기 유입 원인 — 길이 상한 + 내비 키워드 제거 후에도
     * 본문으로 부적합하면 빈 문자열(=목록 snippet 유지) 반환.
     */
    private fun extractDescription(doc: Document): String {
        val tabs = doc.selectFirst("#tabs-1")
        val bodyEl = tabs?.selectFirst(".body-content")
            ?: tabs?.selectFirst(".tabs-container .body, .body, article, p")
            ?: tabs
            ?: doc.selectFirst(".main-post .body-content, .body-content, article.main-post")
        var text = bodyEl?.text().orEmpty()
        // 40자 미만이면 `#content`/`main` 한정 재시도 — `body` 전체 폴백 금지 (내비 쓰레기)
        if (text.isBlank() || text.length < 40) {
            val alt = doc.select("#content, main").firstOrNull()?.text().orEmpty()
            if (alt.length >= 40) text = alt
        }
        for (cut in DESCRIPTION_END_MARKERS) {
            val idx = text.indexOf(cut)
            if (idx > 10) {
                text = text.substring(0, idx)
                break
            }
        }
        text = stripChromeLines(text)
        // 여전히 내비 위주/너무 짧으면 빈 값 — 목록 snippet이 좋은 쪽으로 유지됨
        if (text.isBlank() || text.length < 40) return ""
        if (text.length > MAX_BODY_FALLBACK) text = text.take(MAX_BODY_FALLBACK)
        return text.trim()
    }

    /** 내비·푸터·저작권 라인 제거 (body 전체 폴백 잔재 방어) */
    private fun stripChromeLines(text: String): String {
        val chrome = Regex(
            """(?i)^(menu|navigation|nav|footer|copyright|cookie|privacy policy|terms of|share this|related posts?|prev|next|back to)\b.*$""",
        )
        return text.lineSequence()
            .filterNot { chrome.containsMatchIn(it.trim()) }
            .joinToString("\n")
            .trim()
    }

    /** magnet/torrent/warez URI·문구 제거 (AGENTS.local) */
    internal fun stripDownloadText(text: String): String {
        var t = text
        DOWNLOAD_URI.forEach { pat ->
            t = t.replace(Regex(pat, RegexOption.IGNORE_CASE), " ")
        }
        t = t.replace(
            Regex("""(?i)\b(torrent|magnet|скачать торрент|download link|зеркало|скачать)\b"""),
            " ",
        )
        return t.replace(Regex("""\s{2,}"""), " ").trim()
    }

    internal fun composeSnippet(short: String?, body: String): String {
        val s = short?.trim().orEmpty()
        return if (s.isBlank()) body else if (body.contains(s)) body else "$s\n\n$body"
    }

    private fun absUrl(raw: String, base: String): String? {
        val s = raw.trim()
        if (s.isBlank() || s.startsWith("data:") || s.startsWith("javascript:")) return null
        return try {
            if (s.startsWith("http")) s
            else if (s.startsWith("//")) "https:$s"
            else if (base.isNotBlank()) URI(base).resolve(s).toString()
            else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDate(text: String): Long? {
        val patterns = listOf(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        )
        val cleaned = text.replace(Regex("""\s+"""), " ")
        for (fmt in patterns) {
            for (m in Regex("""\d{1,4}[./\-\s]\d{1,2}[./\-\s]\d{2,4}|\d{1,2}\s+[A-Za-zА-Яа-я]{3,9}\s+\d{4}""").findAll(cleaned)) {
                try {
                    val ld = LocalDate.parse(m.value.trim(), fmt)
                    return ld.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun parseVersion(text: String): String? =
        Regex("""\bv?(\d+\.\d+(?:\.\d+)*)\b""").find(text)?.groupValues?.get(1)

    companion object {
        const val DEVELOPER = "AppStorrent"
        const val STORE_TAG = "appstorrent"
        const val DEFAULT_PAGE_SIZE = 24
        const val DEFAULT_PAGE_LIMIT = 6
        const val DETAIL_LIMIT = 60
        const val CHECKPOINT_EVERY = 10
        /** body 전체 폴백 금지 후 본문 최대 길이 (내비 쓰레기 상한) */
        const val MAX_BODY_FALLBACK = 8000
        const val GAME_DESC_MAX = com.borasarang.macjupjup.util.Constants.APP_BODY_MAX
        private val DESCRIPTION_END_MARKERS = listOf(
            "Что нового", "Системные", "Комментарии", "Скачать торрент", "Скачать",
        )
        /**
         * CF 우회: Googlebot UA는 appstorrent 200, 일반 브라우저 UA는 403.
         * 메타데이터 전용 요청 — robots.txt User-agent:* Disallow 없음(2026-09 확인).
         */
        val BROWSER_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "ru-RU,ru;q=0.9,en;q=0.8",
            "Referer" to "https://appstorrent.ru/",
        )
        private val DOWNLOAD_URI = listOf(
            """magnet:\?[^\s]+""",
            """https?://[^\s]+\.(torrent|dmg|pkg|zip)\b[^\s]*""",
            """https?://(torrents?|dl|download|warez)[^\s]*""",
            """magnet\s*:[^\s]+""",
            """thepiratebay|rutracker|nnm-club|kinozal""",
        )
    }
}
