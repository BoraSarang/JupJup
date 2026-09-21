package com.borasarang.communityjupjup.crawler

import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.db.entity.CrawlSource
import com.borasarang.communityjupjup.util.CommunityCategories
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.util.UrlCanonical
import com.borasarang.communityjupjup.util.takeSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * selector_config 기반 범용 보드 크롤러 (V2 GenericSpider의 로컬 구현).
 * 보드 목록 페이지만 수집 — 상세 페이지 진입 없음 (예의·속도).
 * 본문 저장 금지: snippet 500자 절단만.
 */
class BoardCrawler(
    private val source: CrawlSource,
    private val db: CommunityDatabase,
) : CommunityCrawler {

    override val sourceName: String get() = source.name

    override suspend fun crawl(): Result<List<PostDraft>> = runCatching {
        val config = SelectorConfig.parse(source.selectorConfigJson)
        val boards = db.siteBoardDao().getEnabledBySource(source.id)
        if (boards.isEmpty()) throw IllegalStateException("활성 보드 없음 source=${source.id} (E-AND-CRAWL-0201)")
        val drafts = mutableListOf<PostDraft>()
        for (board in boards) {
            try {
                drafts += crawlBoard(board.boardUrl, board.id, board.categoryId, config)
            } catch (e: Exception) {
                DebugLogger.w("수집", "보드 스킵 ${board.boardName}: ${e.message}")
            }
            delay(Constants.CRAWL_REQUEST_DELAY_MS)
        }
        val seen = mutableSetOf<String>()
        val unique = drafts.filter { seen.add(it.originalUrl) }.toMutableList()
        // 상세 요약: 신규 URL만 상세 진입 (DB 기등록은 스킵) + 미요약 백필
        val summarized = ensureSummaries(unique, config)
        val backfilled = backfill(config, BACKFILL_PER_RUN)
        DebugLogger.i("수집", "보드 수집 완료 boards=${boards.size} found=${drafts.size} unique=${unique.size} 요약신규=$summarized 백필=$backfilled")
        unique
    }

    /** 단일 보드 수집 (보드 단위 워커용, 백필 없음 — 백필은 일일 요약 워커가 담당) */
    override suspend fun crawlSingle(board: com.borasarang.communityjupjup.data.db.entity.SiteBoard): Result<List<PostDraft>> =
        runCatching {
            val config = SelectorConfig.parse(source.selectorConfigJson)
            val drafts = crawlBoard(board.boardUrl, board.id, board.categoryId, config)
            delay(Constants.CRAWL_REQUEST_DELAY_MS)
            val seen = mutableSetOf<String>()
            val unique = drafts.filter { seen.add(it.originalUrl) }.toMutableList()
            val summarized = ensureSummaries(unique, config)
            DebugLogger.i("수집", "보드 수집 완료 board=${board.boardName} found=${drafts.size} unique=${unique.size} 요약신규=$summarized")
            unique
        }

    /** 미상세 행 백필 (limit 상한, 오래된 순 — 일일 요약 워커·수동용) */
    suspend fun backfill(config: SelectorConfig, limit: Int): Int {
        val urls = db.postDao().getMissingDetailUrls(limit.coerceAtLeast(1))
        var done = 0
        for (url in urls) {
            val detail = fetchDetail(url, config)
            val summary = detail.summary
            val thumbnail = detail.thumbnailUrl
            val images = encodeImageUrls(detail.imageUrls).takeIf { detail.imageUrls.isNotEmpty() }
            if (!summary.isNullOrBlank() || !thumbnail.isNullOrBlank() || images != null) {
                db.postDao().updateDetailByUrl(url, summary, thumbnail, images)
                done++
            }
            delay(Constants.CRAWL_REQUEST_DELAY_MS)
        }
        return done
    }

    /**
     * 신규 초안의 요약·썸네일 채우기 + DB 기등록 행 중 상세가 빈 것은 직접 갱신.
     * (상한 30/회 — 수집 시점이 가장 관련도 높은 글이라 백필보다 우선)
     */
    private suspend fun ensureSummaries(
        drafts: MutableList<PostDraft>,
        config: SelectorConfig,
    ): Int {
        if (drafts.isEmpty()) return 0
        val existing = db.postDao().getByCanonicalUrls(drafts.map { it.canonicalUrl }.distinct())
            .associateBy { it.canonicalUrl }
        var done = 0
        for (i in drafts.indices) {
            if (done >= MAX_DETAIL_PER_RUN) break
            val d = drafts[i]
            val e = existing[d.canonicalUrl]
            if (e != null && (!e.summary.isNullOrBlank() || !e.thumbnailUrl.isNullOrBlank())) {
                // 기등록 행: 요약·썸네일·이미지 모두 있으면 스킵
                if (!e.summary.isNullOrBlank() && !e.thumbnailUrl.isNullOrBlank() &&
                    decodeImageUrls(e.imageUrls).isNotEmpty()
                ) continue
            }
            if (e == null && (!d.summary.isNullOrBlank() && !d.thumbnailUrl.isNullOrBlank() && d.imageUrls.isNotEmpty())) continue
            val detail = fetchDetail(d.originalUrl, config)
            if (detail.summary.isNullOrBlank() && detail.thumbnailUrl.isNullOrBlank() && detail.imageUrls.isEmpty()) {
                delay(Constants.CRAWL_REQUEST_DELAY_MS)
                continue
            }
            if (e == null) {
                drafts[i] = d.copy(
                    summary = detail.summary ?: d.summary,
                    thumbnailUrl = detail.thumbnailUrl ?: d.thumbnailUrl,
                    imageUrls = detail.imageUrls.ifEmpty { d.imageUrls },
                )
            } else {
                db.postDao().updateDetailByCanonical(
                    d.canonicalUrl,
                    detail.summary?.takeIf { e.summary.isNullOrBlank() },
                    detail.thumbnailUrl?.takeIf { e.thumbnailUrl.isNullOrBlank() },
                    encodeImageUrls(detail.imageUrls).takeIf {
                        detail.imageUrls.isNotEmpty() && decodeImageUrls(e.imageUrls).isEmpty()
                    },
                )
            }
            done++
            delay(Constants.CRAWL_REQUEST_DELAY_MS)
        }
        return done
    }

    private suspend fun fetchSummary(url: String, config: SelectorConfig): DetailResult {
        return fetchDetail(url, config)
    }

    private suspend fun fetchDetail(url: String, config: SelectorConfig): DetailResult {
        return try {
            val result = withContext(Dispatchers.IO) { CrawlHttp.get(url) }
            if (!result.isOk) return DetailResult(null, null, 0)
            parseDetail(result.body, url, config)
        } catch (e: Exception) {
            DebugLogger.w("수집", "상세 스킵 $url: ${e.message}")
            DetailResult(null, null, 0)
        }
    }

    /**
     * 상세 본문 → 요약 + 대표 이미지 (순수 함수).
     * detailContent 우선, 없으면 범용 후보 중 최장 텍스트 블록.
     * 텍스트가 없어도 이미지는 추출 (짤·인증 글 대응).
     */
    internal fun parseDetail(html: String, baseUrl: String, config: SelectorConfig): DetailResult {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, nav, header, footer, form, iframe").remove()
        val selectors = config.detailContent.split(",").map { it.trim() }.filter { it.isNotBlank() } +
            listOf("article[itemprop=articleBody]", "[itemprop=articleBody]", ".view_content", ".post_content article", ".post_article", "article", "main .content", "#content")
        var best: org.jsoup.nodes.Element? = null
        var bestLen = 0
        for (sel in selectors) {
            val el = try {
                doc.select(sel).firstOrNull()
            } catch (_: Exception) {
                null
            } ?: continue
            val len = el.text().length
            if (len >= MIN_DETAIL_LEN && len > bestLen) {
                best = el
                bestLen = len
            }
        }
        // 텍스트 후보가 없어도 이미지용으로 첫 후보 사용
        val target = best ?: selectors.firstNotNullOfOrNull { sel ->
            try {
                doc.select(sel).firstOrNull()
            } catch (_: Exception) {
                null
            }
        } ?: return DetailResult(null, null, 0)
        // 문단 보존: 블록 경계에 개행 주입 후 wholeText로 추출 (text()는 전부 뭉갬)
        for (br in target.select("br")) {
            br.replaceWith(org.jsoup.nodes.TextNode("\n"))
        }
        for (block in target.select("p, div, li, h1, h2, h3, tr, section, figure, blockquote")) {
            block.append("\n")
        }
        val lines = target.wholeText()
            .replace("[ \\t\\u00a0\\r]+".toRegex(), " ")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val links = extractLinks(target, MAX_LINKS)
        val linkBlock = links.joinToString("\n") { "🔗 ${it.text.take(40)}: ${it.href}" }
        val textBudget = if (linkBlock.isBlank()) {
            Constants.MAX_SUMMARY_LEN
        } else {
            (Constants.MAX_SUMMARY_LEN - linkBlock.length - 1).coerceAtLeast(100)
        }
        val body = if (lines.isEmpty()) {
            null
        } else {
            lines.joinToString("\n").takeSafe(textBudget).takeIf { it.isNotBlank() }
        }
        val summary = listOfNotNull(body, linkBlock.takeIf { it.isNotBlank() })
            .joinToString("\n").takeIf { it.isNotBlank() }
        val images = pickContentImages(target, MAX_IMAGES)
        return DetailResult(summary, images.firstOrNull(), images.size, images, links)
    }

    /** 본문 내 링크 추출 (최대 max건, 중복 제거, http(s)만, 이미지 전용 앵커 제외) */
    internal fun extractLinks(body: org.jsoup.nodes.Element, max: Int): List<BodyLink> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<BodyLink>()
        for (a in body.select("a[href]")) {
            if (out.size >= max) break
            val href = a.attr("abs:href").trim()
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue
            if (!seen.add(href)) continue
            // 이미지·내부 미리보기 썸네일만 감싼 앵커는 실제 링크가 아님
            val text = a.text().replace("\\s+".toRegex(), " ").trim().take(40)
            if (text.isBlank()) continue
            if (text == href) continue
            if (a.select("img[src]").isNotEmpty()) continue
            out += BodyLink(text, href)
        }
        return out
    }

    /** 본문 UI 조각 (UI 아이콘·이모티콘·배너) */
    private val uiImgHints = listOf(
        "icon", "button", "btn", "logo", "emoji", "emoticon", "blank", "spacer",
        "pixel", "transparent", "badge", "profile", "level", "star", "login",
        "banner", "noimage", "smiley", "arrow", "check", "close", "menu",
        "search", "share", "report", "download", "print", "files", "comment",
    )

    /** 본문 실내용 이미지 목록 (최대 max장, UI 조각 제외·중복 제거) */
    internal fun pickContentImages(
        body: org.jsoup.nodes.Element,
        max: Int = MAX_IMAGES,
    ): List<String> {
        val out = mutableListOf<String>()
        for (img in body.select("img")) {
            if (out.size >= max) break
            val src = img.attr("abs:src").trim().ifBlank { img.attr("src").trim() }
            if (src.isBlank()) continue
            val lower = src.lowercase()
            if (uiImgHints.any { lower.contains(it) }) continue
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            // 치수 표기가 있고 너무 작으면 UI로 간주 (표기 없으면 본문으로 인정)
            if (w != null && h != null && (w < 150 || h < 100)) continue
            if (out.any { it == src }) continue
            out += src
        }
        return out
    }

    private suspend fun crawlBoard(
        boardUrl: String,
        boardId: Long,
        categoryId: Int,
        config: SelectorConfig,
    ): List<PostDraft> = withContext(Dispatchers.IO) {
        val result = CrawlHttp.get(boardUrl)
        if (!result.isOk) {
            throw IllegalStateException("GET 실패 url=$boardUrl code=${result.code} err=${result.error}")
        }
        parseBoard(result.body, boardUrl, boardId, categoryId, config)
    }

    internal fun parseBoard(
        html: String,
        baseUrl: String,
        boardId: Long,
        categoryId: Int,
        config: SelectorConfig,
    ): List<PostDraft> {
        val doc = Jsoup.parse(html, baseUrl)
        val rows = doc.select(config.listRow)
        if (rows.isEmpty()) throw IllegalStateException("목록 행 0건 (셀렉터 확인 필요)")
        val drafts = mutableListOf<PostDraft>()
        for (row in rows) {
            try {
                if (config.excludeRow.isNotBlank() && row.select(config.excludeRow).isNotEmpty()) continue
                val titleEl = row.select(config.title).firstOrNull { it.text().isNotBlank() }
                    ?: continue
                val title = titleEl.text().trim().takeSafe(300)
                if (title.isBlank()) continue
                val href = titleEl.attr("abs:href").trim()
                if (href.isBlank()) continue
                val author = row.firstText(config.author)?.takeSafe(100)
                val timeText = row.firstText(config.time)
                    ?: row.select(config.time).firstOrNull()?.attr("datetime")?.trim()
                val snippet = config.snippet.takeIf { it.isNotBlank() }
                    ?.let { row.firstText(it)?.takeSafe(Constants.MAX_SUMMARY_LEN) }
                val deal = if (categoryId == CommunityCategories.HOTDEAL || categoryId == CommunityCategories.USED) {
                    PriceParser.parse(title, snippet)
                } else {
                    null
                }
                drafts += PostDraft(
                    boardId = boardId,
                    categoryId = categoryId,
                    originalPostId = href.substringAfterLast("/").substringBefore("?").take(100),
                    title = title,
                    summary = snippet,
                    authorName = author,
                    originalUrl = href,
                    canonicalUrl = UrlCanonical.normalize(href),
                    thumbnailUrl = config.thumbnail.takeIf { it.isNotBlank() }?.let { sel ->
                        try {
                            row.select(sel).firstOrNull()?.attr("abs:src")?.trim()
                                ?.takeIf { it.isNotBlank() && !it.contains("noimage", ignoreCase = true) }
                        } catch (_: Exception) {
                            null
                        }
                    },
                    viewCount = row.firstText(config.views)?.toCount(),
                    likeCount = row.firstText(config.likes)?.toCount(),
                    commentCount = row.firstText(config.comments)?.toCount(),
                    mallName = deal?.mallName,
                    salePrice = deal?.salePrice,
                    originalPrice = deal?.originalPrice,
                    discountRate = deal?.discountRate,
                    isSoldOut = deal?.isSoldOut,
                    dealStatus = deal?.dealStatus,
                    publishedAt = TimeParser.parse(timeText),
                )
            } catch (e: Exception) {
                DebugLogger.w("수집", "행 스킵: ${e.message}")
            }
        }
        return drafts
    }

    private fun String.toCount(): Int? {
        val t = trim()
        if (t.isEmpty()) return null
        // 조회수 단위: "65.5 k" → 65500, "3.5 M" → 3500000
        val mult = when {
            t.endsWith("k", ignoreCase = true) -> 1000L
            t.endsWith("m", ignoreCase = true) -> 1000_000L
            else -> 1L
        }
        val num = t.trimEnd('k', 'K', 'm', 'M').filter { it.isDigit() || it == '.' }.toDoubleOrNull()
            ?: return null
        return (num * mult).toLong().takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    companion object {
        /** 상세 진입 상한 (예의·수행시간 bound) */
        private const val MAX_DETAIL_PER_RUN = 30
        private const val BACKFILL_PER_RUN = 25
        private const val MIN_DETAIL_LEN = 20
        /** 본문 이미지 저장 상한 */
        private const val MAX_IMAGES = 5
        /** 본문 링크 보존 상한 */
        private const val MAX_LINKS = 3
    }
    private fun org.jsoup.nodes.Element.firstText(selector: String): String? {
        if (selector.isBlank()) return null
        return select(selector).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
    }
}
