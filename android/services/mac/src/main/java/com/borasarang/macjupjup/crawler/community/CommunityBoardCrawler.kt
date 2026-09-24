package com.borasarang.macjupjup.crawler.community

import com.borasarang.common.crawl.SelectorConfig
import com.borasarang.common.crawl.TimeParser
import com.borasarang.common.util.HostThrottler
import com.borasarang.common.util.parallelFetch
import com.borasarang.macjupjup.crawler.CrawlHttp
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CommunityPost
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import java.security.MessageDigest

/**
 * 커뮤니티 보드 목록 크롤러 (PLAN_v23, community BoardCrawler 이식).
 * 목록 파싱 → URL 중복 제거 → 신규 상세(본문)만 절취 (호스트 스로틀·Googlebot UA).
 */
class CommunityBoardCrawler(
    private val source: CrawlSource,
    private val db: MacDatabase,
    private val config: SelectorConfig = SelectorConfig.parse(source.selectorConfigJson),
    private val throttler: HostThrottler = sharedThrottler,
) {

    data class Outcome(
        val posts: List<CommunityPost>,
        val created: Int,
        val detailEnriched: Int,
    )

    suspend fun crawl(): Result<Outcome> = runCatching {
        val html = fetch(source.baseUrl)
        val drafts = parseList(html, source.baseUrl, config).toMutableList()
        if (drafts.isEmpty()) throw IllegalStateException("목록 행 0건 (셀렉터 확인 필요) E-AND-CRAWL-0201")

        val now = System.currentTimeMillis()
        val ids = drafts.map { it.id }
        val existing = db.communityPostDao().getExistingIds(ids).toSet()
        val fresh = drafts.filter { it.id !in existing }.toMutableList()

        // 신규 상세 본문 (예의: 최대 5건, 호스트 스로틀)
        var enriched = 0
        if (fresh.isNotEmpty()) {
            val targets = fresh.take(MAX_DETAIL_PER_RUN)
            val details = parallelFetch(targets, throttler, MAX_DETAIL_CONCURRENCY, { it.originalUrl }) { p ->
                runCatching { fetchDetail(p) }.getOrNull()
            }
            targets.forEachIndexed { i, p ->
                val d = details.getOrNull(i) ?: return@forEachIndexed
                if (d.contentHtml != null || d.summary != null || d.thumbnailUrl != null) {
                    val idx = fresh.indexOfFirst { it.id == p.id }
                    if (idx >= 0) {
                        fresh[idx] = p.copy(
                            contentHtml = d.contentHtml ?: p.contentHtml,
                            summary = d.summary ?: p.summary,
                            thumbnailUrl = d.thumbnailUrl ?: p.thumbnailUrl,
                        )
                    }
                }
            }
            enriched = details.count { it != null && it.contentHtml != null }
            val byId = fresh.associateBy { it.id }
            for (i in drafts.indices) {
                byId[drafts[i].id]?.let { drafts[i] = it }
            }
        }

        val rowIds = db.communityPostDao().insertIgnore(drafts)
        val created = rowIds.count { it != -1L }
        // 기존 행 상세 미보유분 백필
        val backfill = backfillMissingContent()
        DebugLogger.i(
            "커뮤니티수집",
            "완료 source=${source.name} found=${drafts.size} new=$created " +
                "detail=$enriched backfill=$backfill",
        )
        Outcome(drafts, created, enriched)
    }

    private suspend fun backfillMissingContent(): Int {
        val missing = db.communityPostDao().getMissingContent(BACKFILL_PER_RUN)
        if (missing.isEmpty()) return 0
        var done = 0
        for (p in missing) {
            try {
                val d = fetchDetail(p)
                if (d.contentHtml != null) {
                    db.communityPostDao().updateDetail(p.id, d.contentHtml, d.summary, d.thumbnailUrl)
                    done++
                }
                delay(Constants.CRAWL_REQUEST_DELAY_MS)
            } catch (e: Exception) {
                DebugLogger.w("커뮤니티수집", "백필 스킵 ${p.originalUrl}: ${e.message}")
            }
        }
        return done
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        throttler.waitFor(url)
        delay(Constants.CRAWL_REQUEST_DELAY_MS)
        val result = CrawlHttp.getWithHeaders(url, HEADERS)
        if (!result.isOk) {
            throw IllegalStateException("GET 실패 url=$url code=${result.code} err=${result.error}")
        }
        if (result.body.isBlank()) {
            throw IllegalStateException("응답 본문 없음(차단 의심) url=$url")
        }
        result.body
    }

    /** 목록 HTML → CommunityPost 초안 (저장 전, id=sha256(url)) */
    internal fun parseList(
        html: String,
        baseUrl: String,
        cfg: SelectorConfig,
        mainOverride: String? = null,
    ): List<CommunityPost> {
        val doc = Jsoup.parse(html, baseUrl)
        val rows = doc.select(cfg.listRow)
        if (rows.isEmpty()) throw IllegalStateException("목록 행 0건 (셀렉터 확인 필요) E-AND-CRAWL-0201")
        val main = mainOverride ?: cfg.main.ifBlank { sourceIdToMain(source.id) }
        val now = System.currentTimeMillis()
        val drafts = mutableListOf<CommunityPost>()
        for (row in rows) {
            try {
                if (cfg.excludeRow.isNotBlank() && row.select(cfg.excludeRow).isNotEmpty()) continue
                val parsed = extractRow(row, cfg, baseUrl, main, now) ?: continue
                drafts += parsed
            } catch (e: Exception) {
                DebugLogger.w("커뮤니티수집", "행 스킵: ${e.message}")
            }
        }
        return drafts.distinctBy { it.originalUrl }
    }

    private fun extractRow(
        row: Element,
        cfg: SelectorConfig,
        baseUrl: String,
        main: String,
        now: Long,
    ): CommunityPost? {
        val titleEl = row.select(cfg.title).firstOrNull { it.text().isNotBlank() }
        // 다모앙: listRow가 <a> 이고 제목은 .post-title[title]
        val attrTitle = titleEl?.attr("title")?.trim().orEmpty().ifBlank {
            row.attr("title").trim()
        }
        val title = (attrTitle.ifBlank { titleEl?.text().orEmpty() }).trim()
        if (title.isBlank()) return null

        val rowHref = row.attr("abs:href").trim()
        val titleHref = titleEl?.attr("abs:href")?.trim().orEmpty()
        val href = titleHref.ifBlank { rowHref }
        if (href.isBlank() || !href.startsWith("http")) return null

        val author = cfg.author.takeIf { it.isNotBlank() }
            ?.let { firstText(row, it) }
            ?: row.select(".post-meta-text").firstOrNull()?.text()?.trim()?.take(100)
        val timeText = cfg.time.takeIf { it.isNotBlank() }
            ?.let { firstText(row, it) }
            ?: row.select(".post-meta-text").mapNotNull { el ->
                val t = el.text().trim()
                TimeParser.parse(t)?.let { t }
            }.firstOrNull()
            ?: row.text().take(80)

        val commentCount = cfg.comments.takeIf { it.isNotBlank() }
            ?.let { firstText(row, it) }?.let { parseCount(it) }
            ?: row.select(".comment-count").firstOrNull()?.text()?.let { parseCount(it) }

        val viewCount = cfg.views.takeIf { it.isNotBlank() }
            ?.let { firstText(row, it) }?.let { parseCount(it) }

        val snippet = cfg.snippet.takeIf { it.isNotBlank() }
            ?.let { firstText(row, it) }
            ?: titleEl?.text()?.take(Constants.MAX_SUMMARY_LEN)

        val publishedAt = TimeParser.parse(timeText) ?: now

        return CommunityPost(
            id = sha256Hex(href),
            sourceId = source.id,
            sourceName = source.name,
            main = main,
            title = title.take(300),
            summary = snippet?.take(Constants.MAX_SUMMARY_LEN),
            authorName = author?.take(100),
            originalUrl = href,
            thumbnailUrl = cfg.thumbnail.takeIf { it.isNotBlank() }?.let { sel ->
                try {
                    row.select(sel).firstOrNull()?.attr("abs:src")?.trim()?.ifBlank { null }
                } catch (_: Exception) {
                    null
                }
            },
            commentCount = commentCount,
            viewCount = viewCount,
            contentHtml = null,
            publishedAt = publishedAt,
            collectedAt = now,
        )
    }

    private data class DetailFill(
        val contentHtml: String?,
        val summary: String?,
        val thumbnailUrl: String?,
    )

    private suspend fun fetchDetail(post: CommunityPost): DetailFill {
        val html = fetch(post.originalUrl)
        val doc = Jsoup.parse(html, post.originalUrl)
        val bodyEl = if (config.detailContent.isNotBlank()) {
            doc.select(config.detailContent).firstOrNull()
        } else {
            null
        } ?: doc.select(".prose, .post_content, article, .post_article, .write_note").firstOrNull()
            ?: doc.body()

        val cleaned = bodyEl?.let { sanitize(it.html(), post.originalUrl) }
        val text = bodyEl?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val summary = text.take(Constants.MAX_SUMMARY_LEN).ifBlank { null }
        val thumb = doc.select("meta[property=og:image]").firstOrNull()
            ?.attr("content")?.trim()?.ifBlank { null }
            ?: config.thumbnail.takeIf { it.isNotBlank() }
                ?.let { doc.select(it).firstOrNull()?.attr("abs:src")?.trim()?.ifBlank { null } }
            ?: post.thumbnailUrl
        val content = cleaned?.take(Constants.NEWS_MAX_BODY_LEN)?.ifBlank { null }
        return DetailFill(content, summary, thumb)
    }

    private fun sanitize(html: String, baseUrl: String): String {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, iframe, form, noscript").remove()
        for (img in doc.select("img")) {
            val abs = img.attr("abs:src").ifBlank { img.attr("src") }
            if (abs.isNotBlank()) img.attr("src", abs)
        }
        for (a in doc.select("a[href]")) {
            a.attr("href", a.attr("abs:href").ifBlank { a.attr("href") })
        }
        return Jsoup.clean(doc.body()?.html().orEmpty(), Safelist.relaxed())
    }

    private fun firstText(row: Element, selector: String): String? =
        row.select(selector).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }

    private fun parseCount(raw: String): Int? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val mult = when {
            t.endsWith("k", true) || t.endsWith("천", true) -> 1000L
            t.endsWith("m", true) || t.endsWith("만", true) -> 1_000_000L
            else -> 1L
        }
        val num = t.trimEnd('k', 'K', 'm', 'M', '천', '만')
            .filter { it.isDigit() || it == '.' }
            .toDoubleOrNull() ?: return null
        return (num * mult).toLong().takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    companion object {
        /** 상세 진입 상한 (예의·수행시간 bound) — 본문 전체화 위해 상향 */
        private const val MAX_DETAIL_PER_RUN = 15
        private const val BACKFILL_PER_RUN = 30
        internal const val MAX_DETAIL_CONCURRENCY = 2
        private val sharedThrottler = HostThrottler()

        /** 다모앙은 Googlebot UA 필수 (일반 UA 차단) */
        val HEADERS: Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Accept-Language" to "ko-KR,ko;q=0.9",
        )

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun sourceIdToMain(sourceId: String): String = when {
            sourceId.contains("apple") -> Constants.MAIN_APPLE
            sourceId.contains("ai") && !sourceId.contains("mac") -> Constants.MAIN_AI_COMMUNITY
            sourceId.contains("mac") -> Constants.MAIN_MAC_COMMUNITY
            else -> Constants.MAIN_MAC_COMMUNITY
        }
    }
}
