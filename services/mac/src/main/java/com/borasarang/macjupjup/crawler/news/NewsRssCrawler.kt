package com.borasarang.macjupjup.crawler.news

import com.borasarang.macjupjup.crawler.CrawlHttp
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.NewsCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.safety.Safelist
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 뉴스 RSS 피드 1건 (이름·URL·main 분류) */
data class NewsFeed(
    val name: String,
    val url: String,
    val main: String,
)

/**
 * 호스트별 최소 요청 간격 강제 (수집 예의 1초, 병렬 시에도 동일 호스트 연타 금지).
 * community HostThrottler와 동일 설계 — R30 공통화 시 common으로 승격 예정. 순수 JVM.
 */
class HostThrottler(private val minGapMs: Long = 1000L) {
    private val mutex = Mutex()
    private val lastHit = mutableMapOf<String, Long>()

    suspend fun waitFor(url: String) {
        val host = hostOf(url)
        while (true) {
            val wait = mutex.withLock {
                val now = System.currentTimeMillis()
                val prev = lastHit[host]
                if (prev == null || now - prev >= minGapMs) {
                    lastHit[host] = now
                    0L
                } else {
                    minGapMs - (now - prev)
                }
            }
            if (wait <= 0) return
            delay(wait)
        }
    }

    companion object {
        fun hostOf(url: String): String {
            return try {
                URI(url).host?.lowercase() ?: url
            } catch (_: Exception) {
                url
            }
        }
    }
}

/**
 * 항목 일괄 병렬 수집 헬퍼 (R36).
 * [concurrency] 상한 세마포어 + [throttler] 호스트 예의 강제.
 * 반환 순서는 items와 동일. 순수 코루틴 (단위테스트 가능).
 */
internal suspend fun <T, R> parallelNews(
    items: List<T>,
    throttler: HostThrottler,
    concurrency: Int,
    urlOf: (T) -> String,
    fetch: suspend (T) -> R,
): List<R> = coroutineScope {
    val sem = Semaphore(concurrency.coerceAtLeast(1))
    items.map { item ->
        async(Dispatchers.IO) {
            sem.withPermit {
                throttler.waitFor(urlOf(item))
                fetch(item)
            }
        }
    }.awaitAll()
}

/** 뉴스 수집 결과 (기사 + 앱 연동) */
data class NewsOutcome(
    val articles: List<NewsArticle>,
    val relations: List<NewsAppRelation>,
)

/**
 * 뉴스 RSS 크롤러 (R32 PLAN_v17).
 * RSS → hash 중복 제거 → content:encoded 우선, 없으면 원문 fetch + jsoup 추출 →
 * 이미지 절대경로 변환 → 키워드 분류 + 첫문장 요약 → 앱 이름 매칭.
 * 파서는 jsoup XML 파서 사용 (Android 전용 XmlPullParser 회피, 단위테스트 가능).
 */
class NewsRssCrawler(
    private val source: CrawlSource,
    private val db: MacDatabase,
    private val feeds: List<NewsFeed> = feedsFor(source),
    private val throttler: HostThrottler = sharedThrottler,
) {

    suspend fun crawlNews(): Result<NewsOutcome> = runCatching {
        val drafts = mutableListOf<ParsedNews>()
        for (feed in feeds) {
            try {
                val xml = fetchGet(feed.url)
                drafts += parseRss(xml, feed)
            } catch (e: Exception) {
                DebugLogger.w("뉴스수집", "피드 스킵 ${feed.name}: ${e.message}")
            }
            delay(Constants.CRAWL_REQUEST_DELAY_MS)
        }
        // URL 기준 중복 제거 (피드 간 중복 포함)
        val unique = drafts.distinctBy { it.id }
        DebugLogger.i("뉴스수집", "파싱 완료 feeds=${feeds.size} found=${drafts.size} unique=${unique.size}")

        // 기존 저장분 제외 (원문 fetch 낭비 방지, IN 배치 1회)
        val existing = if (unique.isEmpty()) {
            emptySet()
        } else {
            db.newsArticleDao().getExistingIds(unique.map { it.id }).toSet()
        }
        val fresh = unique.filter { it.id !in existing }
        val now = System.currentTimeMillis()
        // R36: 상세 순차 → 최대 3병렬 (호스트 스로틀로 예의 유지, 실패 건 스킵)
        val articles = parallelNews(fresh, throttler, MAX_NEWS_CONCURRENCY, { it.link }) { d ->
            runCatching { buildArticle(d, now) }
                .onFailure { e -> DebugLogger.w("뉴스수집", "본문 스킵 ${d.link}: ${e.message}") }
                .getOrNull()
        }.filterNotNull()
        val relations = matchApps(articles)
        NewsOutcome(articles, relations)
    }

    /** 원문 본문 확보 + 분류 + 요약 → 저장용 엔티티 */
    private suspend fun buildArticle(d: ParsedNews, now: Long): NewsArticle {
        var bodyText = d.feedText
        var bodyHtml = d.feedHtml
        var thumb = d.thumbnailUrl
        if (bodyText.isBlank()) {
            // 피드 본문 없음 → 원문 fetch 후 jsoup 추출 (Readability 대체)
            val html = fetchGet(d.link)
            val extracted = extractBody(html, d.link)
            bodyText = extracted.text
            bodyHtml = extracted.html
            if (thumb == null) thumb = extracted.firstImage
        } else if (bodyHtml != null) {
            bodyHtml = sanitizeHtml(bodyHtml, d.link)
            if (thumb == null) thumb = firstImageOf(bodyHtml)
        }
        if (bodyText.isBlank()) throw IllegalStateException("본문 없음")
        // 피드 설명만 있고 HTML이 없으면 텍스트를 문단 단위로 나눠 본문 구성 (단일 <p> 한줄 표시 방지)
        if (bodyHtml == null) {
            bodyHtml = paragraphize(bodyText)
        }
        val sub = NewsCategories.classify(d.main, d.title, bodyText, d.rssCategory)
        val summary = NewsCategories.summarize(bodyText)
        return NewsArticle(
            id = d.id,
            sourceId = source.id,
            sourceName = source.name,
            main = d.main,
            sub = sub,
            title = d.title.take(300),
            summary = summary,
            contentHtml = bodyHtml?.take(Constants.NEWS_MAX_BODY_LEN),
            originalUrl = d.link,
            thumbnailUrl = thumb,
            publishedAt = d.publishedAt ?: now,
            collectedAt = now,
        )
    }

    /** 제목에 앱 이름이 있으면 연동 (3자 이상, 대소문자 무시) */
    private suspend fun matchApps(articles: List<NewsArticle>): List<NewsAppRelation> {
        if (articles.isEmpty()) return emptyList()
        val names = db.appDao().getAllNames().filter { it.name.length >= 3 }
        if (names.isEmpty()) return emptyList()
        val relations = mutableListOf<NewsAppRelation>()
        for (a in articles) {
            for (appId in matchAppIds(a.title, names.map { it.id to it.name })) {
                relations += NewsAppRelation(newsId = a.id, appId = appId)
            }
        }
        return relations.distinct()
    }

    private suspend fun fetchGet(url: String): String = withContext(Dispatchers.IO) {
        val result = CrawlHttp.get(url)
        if (!result.isOk) throw IllegalStateException("GET 실패 url=$url code=${result.code}")
        result.body
    }

    /** 파싱 중간 결과 (저장 전) */
    internal data class ParsedNews(
        val id: String,
        val link: String,
        val title: String,
        val main: String,
        val rssCategory: String?,
        val feedText: String,
        val feedHtml: String?,
        val thumbnailUrl: String?,
        val publishedAt: Long?,
    )

    internal data class ExtractedBody(
        val text: String,
        val html: String,
        val firstImage: String?,
    )

    companion object {
        /** RSS 12종 (맥 4·AI 4·보안 4). sourceId별 피드 1개씩 */
        val ALL_FEEDS: List<NewsFeed> = listOf(
            NewsFeed("MacRumors", "https://feeds.macrumors.com/MacRumors-All", NewsCategories.MAIN_MAC),
            NewsFeed("9to5Mac", "https://9to5mac.com/feed/", NewsCategories.MAIN_MAC),
            NewsFeed("Apple Newsroom", "https://www.apple.com/newsroom/rss-feed.rss", NewsCategories.MAIN_MAC),
            NewsFeed("Macworld", "https://www.macworld.com/feed", NewsCategories.MAIN_MAC),
            NewsFeed("MarkTechPost", "https://www.marktechpost.com/feed/", NewsCategories.MAIN_AI),
            NewsFeed("Google Research", "https://research.google/blog/rss/", NewsCategories.MAIN_AI),
            NewsFeed("OpenAI", "https://openai.com/blog/rss/", NewsCategories.MAIN_AI),
            NewsFeed("TechCrunch AI", "https://techcrunch.com/category/artificial-intelligence/feed/", NewsCategories.MAIN_AI),
            NewsFeed("The Hacker News", "https://feeds.feedburner.com/TheHackersNews", NewsCategories.MAIN_SEC),
            NewsFeed("BleepingComputer", "https://www.bleepingcomputer.com/feed/", NewsCategories.MAIN_SEC),
            NewsFeed("보안뉴스", "https://www.boannews.com/media/news_rss.xml", NewsCategories.MAIN_SEC),
            NewsFeed("데일리시큐", "https://www.dailysecu.com/rss/allArticle.xml", NewsCategories.MAIN_SEC),
        )

        /** 소스별 피드 선택 (baseUrl 일치 우선, 없으면 main 추정) */
        fun feedsFor(source: CrawlSource): List<NewsFeed> {
            val byUrl = ALL_FEEDS.filter { it.url == source.baseUrl }
            if (byUrl.isNotEmpty()) return byUrl
            return ALL_FEEDS
        }

        /** 피드당 최대 처리 건수 (워커 점유 방지) */
        const val MAX_ITEMS_PER_FEED = 15

        /** R36: 상세 병렬 상한 (호스트 스로틀과 함께 수집 예의 유지) */
        internal const val MAX_NEWS_CONCURRENCY = 3

        /** 프로세스 전역 공유 스로틀러 (워커 간 동일 호스트 연타 방지) */
        private val sharedThrottler = HostThrottler()

        /**
         * 제목 내 앱 이름 매칭 (순수 함수).
         * ASCII 이름은 단어 경계 매칭("Starting"에 "start" 오탐 방지),
         * 그 외(한글 등)는 부분 문자열 매칭. 순수 JVM (단위테스트 가능).
         */
        internal fun matchAppIds(title: String, names: List<Pair<String, String>>): List<String> {
            val out = mutableListOf<String>()
            for ((id, name) in names) {
                if (name.length < 3) continue
                val matched = if (name.all { it.isLetterOrDigit() && it.code < 128 }) {
                    Regex("(?i)(?<![A-Za-z0-9])" + Regex.escape(name) + "(?![A-Za-z0-9])").containsMatchIn(title)
                } else {
                    name.lowercase() in title.lowercase()
                }
                if (matched) out += id
            }
            return out.distinct()
        }

        /** 원문 URL SHA-256 hex (중복 제거 기준) */
        fun sha256Hex(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** RSS/Atom 파싱 (RSS2 item + Atom entry). 순수 JVM (단위테스트 가능) */
        internal fun parseRss(xml: String, feed: NewsFeed): List<ParsedNews> {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val items = doc.select("item")
            val entries = if (items.isEmpty()) doc.select("entry") else emptyList()
            val out = mutableListOf<ParsedNews>()
            for (el in items + entries) {
                try {
                    val title = childText(el, setOf("title"))?.trim().orEmpty()
                    val link = extractLink(el)?.trim().orEmpty()
                    if (title.isBlank() || link.isBlank()) continue
                    val desc = childText(el, setOf("description", "summary"))?.trim().orEmpty()
                    val encoded = childText(el, setOf("encoded"))?.trim()
                    val category = childText(el, setOf("category"))
                        ?: el.ownerDocument()?.select("channel > title")?.first()?.text()
                    val pub = childText(el, setOf("pubdate", "published", "updated", "date"))
                        ?.let { parseNewsDate(it) }
                    var thumb = extractThumb(el)
                    if (thumb == null) {
                        // 피드 본문 HTML 내 첫 이미지 (9to5Mac feat-image 등)
                        val htmlForImg = if (!encoded.isNullOrBlank()) encoded else desc
                        thumb = Jsoup.parseBodyFragment(htmlForImg).selectFirst("img")
                            ?.attr("src")?.trim()?.takeIf { it.isNotBlank() }
                    }
                    val feedText = if (!encoded.isNullOrBlank()) {
                        Jsoup.parse(encoded).text()
                    } else {
                        Jsoup.parse(desc).text()
                    }
                    out += ParsedNews(
                        id = sha256Hex(link),
                        link = link,
                        title = title,
                        main = feed.main,
                        rssCategory = category?.trim(),
                        feedText = feedText,
                        feedHtml = encoded?.takeIf { it.isNotBlank() },
                        thumbnailUrl = thumb,
                        publishedAt = pub,
                    )
                    if (out.size >= MAX_ITEMS_PER_FEED) break
                } catch (_: Exception) {
                    continue
                }
            }
            return out
        }

        /** 자식 요소 텍스트 (네임스페이스 접미사 매칭: content:encoded → encoded) */
        private fun childText(el: org.jsoup.nodes.Element, names: Set<String>): String? {
            for (child in el.children()) {
                val local = child.tagName().substringAfterLast(":").lowercase()
                if (local in names) return child.text()
            }
            return null
        }

        /** 링크 추출 (RSS text / Atom href 속성) */
        private fun extractLink(el: org.jsoup.nodes.Element): String? {
            for (child in el.children()) {
                val local = child.tagName().substringAfterLast(":").lowercase()
                if (local == "link") {
                    val href = child.attr("href").trim()
                    if (href.isNotBlank()) return href
                    val text = child.text().trim()
                    if (text.isNotBlank()) return text
                }
            }
            return null
        }

        /** 썸네일 추출 (media:thumbnail / media:content / enclosure) */
        private fun extractThumb(el: org.jsoup.nodes.Element): String? {
            for (child in el.children()) {
                val local = child.tagName().substringAfterLast(":").lowercase()
                if (local == "thumbnail" || local == "content" || local == "enclosure") {
                    val url = child.attr("url").trim()
                    if (url.isNotBlank()) return url
                }
            }
            return null
        }

        /** 날짜 파싱 (RFC822·ISO8601·에포크). 정규식 precompile */
        private val EPOCH_REGEX = Regex("^\\d{10,13}$")

        /** RFC822 요일 제거 (피드 요일 오기 빈번, 검증 실패 방지) */
        private val WEEKDAY_REGEX = Regex("^[A-Za-z]+,\\s*")

        internal fun parseNewsDate(raw: String): Long? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            if (EPOCH_REGEX.matches(s)) {
                val v = s.toLongOrNull() ?: return null
                return if (s.length <= 10) v * 1000 else v
            }
            // RFC822 (21 Sep 2026 10:00:00 +0000, 요일 제거 후).
            // 숫자 오프셋은 X, 문자존(GMT 등)은 z로 각각 시도
            try {
                val noWeekday = WEEKDAY_REGEX.replace(s, "")
                val numFmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss X", Locale.US)
                return ZonedDateTime.parse(noWeekday, numFmt).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                val noWeekday = WEEKDAY_REGEX.replace(s, "")
                val nameFmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.US)
                return ZonedDateTime.parse(noWeekday, nameFmt).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                return OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: Exception) {
            }
            try {
                return Instant.parse(s).toEpochMilli()
            } catch (_: Exception) {
            }
            return null
        }

        /** 본문 추출 (jsoup 휴리스틱). 이미지 절대경로 + 속성 강제 */
        internal fun extractBody(html: String, baseUrl: String): ExtractedBody {
            val doc = Jsoup.parse(html, baseUrl)
            val body = doc.selectFirst("article")
                ?: doc.selectFirst("[role=main]")
                ?: doc.selectFirst(".post-content, .entry-content, .article-body, .article-content, main")
                ?: doc.body()
            val cleaned = sanitizeHtml(body.html(), baseUrl)
            return ExtractedBody(
                text = body.text(),
                html = cleaned,
                firstImage = firstImageOf(cleaned),
            )
        }

        /** HTML 정제: 허용 태그만 + img 절대경로·속성 강제 + script/iframe 제거 */
        internal fun sanitizeHtml(html: String, baseUrl: String): String {
            val doc = Jsoup.parseBodyFragment(html, baseUrl)
            doc.select("script, iframe, style, noscript, form, button").remove()
            for (img in doc.select("img")) {
                img.attr("src", img.absUrl("src"))
                img.attr("referrerpolicy", "no-referrer")
                img.attr("loading", "lazy")
                img.removeAttr("srcset")
            }
            for (a in doc.select("a")) {
                a.attr("href", a.absUrl("href"))
            }
            val safelist = Safelist.relaxed()
                .addAttributes("img", "referrerpolicy", "loading")
            return Jsoup.clean(doc.body().html(), baseUrl, safelist)
        }

        private fun firstImageOf(html: String): String? {
            val src = Jsoup.parseBodyFragment(html).selectFirst("img")?.attr("src")?.trim()
            return src?.takeIf { it.isNotBlank() && it.startsWith("http") }
        }

        /**
         * 순수 텍스트를 문단 HTML로 변환 (단일 <p> 한줄 표시 방지).
         * 문장 경계(.!?。！？) 기준 분리 후 2~3문장씩 묶어 <p>로 감싼다.
         * 순수 JVM (단위테스트 가능).
         */
        internal fun paragraphize(text: String, maxCharsPerPara: Int = 600): String {
            val norm = text.replace(Regex("\\s+"), " ").trim()
            if (norm.isBlank()) return "<p></p>"
            val sentences = norm.split(Regex("(?<=[.!?。！？])\\s+")).filter { it.isNotBlank() }
            val parts = if (sentences.size <= 1) {
                // 문장 부호 없는 장문은 길이 기준 절단
                val out = mutableListOf<String>()
                var start = 0
                while (start < norm.length) {
                    var end = (start + maxCharsPerPara).coerceAtMost(norm.length)
                    if (end < norm.length) {
                        val space = norm.lastIndexOf(' ', end)
                        if (space > start + 100) end = space
                    }
                    out += norm.substring(start, end).trim()
                    start = end
                }
                out
            } else {
                val out = mutableListOf<String>()
                val buf = StringBuilder()
                var count = 0
                for (s in sentences) {
                    if (buf.isNotEmpty()) buf.append(' ')
                    buf.append(s.trim())
                    count++
                    if (count >= 3 || buf.length >= maxCharsPerPara) {
                        out += buf.toString()
                        buf.clear()
                        count = 0
                    }
                }
                if (buf.isNotEmpty()) out += buf.toString()
                out
            }
            return parts.joinToString("") {
                "<p>" + it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>"
            }
        }
    }
}
