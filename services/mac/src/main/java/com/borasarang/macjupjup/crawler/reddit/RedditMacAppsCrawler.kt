package com.borasarang.macjupjup.crawler.reddit

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.splitTitle
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.DebugLogger
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * Reddit r/macapps 앱 발굴 (공개 Atom .rss, 키 불필요, 실행당 HTTP 1회).
 * JSON /new.json은 커스텀 UA로 403 — .rss(Atom)는 200 확인 (2026-09).
 * 미인증 리밋 대비 12h 주기 1회. 버전·trackId는 ITunesNameMatcher / LookupPoller 보완.
 */
class RedditMacAppsCrawler(
    source: CrawlSource,
) : BaseCrawler(source) {

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val url = "${source.baseUrl}/.rss"
        val drafts = parsePosts(fetchGet(url))
        politenessDelay()
        DebugLogger.i("수집", "Reddit r/macapps 완료 atom found=${drafts.size}")
        drafts
    }

    internal fun parsePosts(body: String): List<AppDraft> {
        val doc = try {
            Jsoup.parse(body, "", Parser.xmlParser())
        } catch (_: Exception) {
            parseFail("Reddit Atom")
        }
        val entries = doc.select("entry")
        if (entries.isEmpty() && !body.contains("<feed")) parseFail("Reddit Atom")
        return entries.mapNotNull { el ->
            try {
                val rawTitle = el.select("title").firstOrNull()?.text()?.trim().orEmpty()
                if (rawTitle.isBlank()) return@mapNotNull null
                val lower = rawTitle.lowercase()
                if (NSFW_TITLE.containsMatchIn(lower)) return@mapNotNull null
                if (SKIP_TITLE.containsMatchIn(lower)) return@mapNotNull null
                val detailUrl = el.select("link[rel=alternate]").firstOrNull()?.attr("href")
                    ?.trim()?.takeIf { it.startsWith("http") }
                    ?: el.select("link").firstOrNull()?.attr("href")?.trim()?.takeIf { it.startsWith("http") }
                    ?: return@mapNotNull null
                val contentHtml = el.select("content").firstOrNull()?.text()?.trim().orEmpty()
                val contentDoc = if (contentHtml.isNotBlank()) {
                    Jsoup.parseBodyFragment(contentHtml, "https://www.reddit.com")
                } else {
                    null
                }
                val text = contentDoc?.text()?.trim().orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .ifBlank { rawTitle }
                val homepageUrl = contentDoc?.select("a[href]")
                    ?.mapNotNull { a -> a.absUrl("href").ifBlank { a.attr("href") } }
                    ?.firstOrNull { u ->
                        u.startsWith("http") &&
                            !u.contains("reddit.com") &&
                            !u.contains("redd.it") &&
                            !u.contains("/comments/")
                    }
                val iconUrl = contentDoc?.select("img[src]")?.firstOrNull()?.let { img ->
                    val u = img.absUrl("src").ifBlank { img.attr("src") }
                    u.takeIf { it.startsWith("http") && !it.contains("reddit.com/static") }
                }
                val published = el.select("published, updated").firstOrNull()?.text()?.trim()
                val created = published?.let { p ->
                    try {
                        java.time.OffsetDateTime.parse(p).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        try {
                            java.time.Instant.parse(p).toEpochMilli()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                val flair = el.select("category").firstOrNull()?.attr("label")?.trim()?.takeIf {
                    it.isNotBlank() && !it.equals("macapps", true) && !it.equals("r/macapps", true)
                }
                val name = cleanName(rawTitle)
                buildDraft(
                    name = name,
                    // 같은 앱을 다른 유저가 올려도 병합되도록 소스명 고정
                    developer = DEVELOPER,
                    descriptionSnippet = text.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN),
                    longDescription = text.take(com.borasarang.macjupjup.util.Constants.APP_BODY_MAX),
                    homepageUrl = homepageUrl,
                    releaseDate = created,
                    iconUrl = iconUrl,
                    topics = listOfNotNull("reddit", "r/macapps", flair?.lowercase()),
                    detailUrl = detailUrl,
                )
            } catch (_: Exception) {
                null
            }
        }.let { dedupById(it) }
    }

    /** "[FooBar] …" / "Show HN: FooBar — …" 등 Reddit 제목 → 앱 이름 */
    internal fun cleanName(title: String): String {
        var n = title.trim()
        val bracket = BRACKET.find(n)
        if (bracket != null) {
            val inner = bracket.groupValues[1].trim()
            if (inner.isNotBlank()) n = inner
        } else {
            n = n.replace(LEADING_PREFIX, "")
            n = splitTitle(n, listOf(": "))
        }
        return n.trim().ifBlank { title.trim() }
    }

    companion object {
        const val DEVELOPER = "r/macapps"
        private val BRACKET = Regex("""^\[([^\]]{2,80})]""")
        private val LEADING_PREFIX = Regex(
            """^(?:show hn|i made|i built|i released|i published|just released|new release|released|app)\s*:\s*""",
            RegexOption.IGNORE_CASE,
        )
        private val NSFW_TITLE = Regex("""\bnsfw\b""")
        private val SKIP_TITLE = Regex(
            """^(?:\[?(?:megathread|monthly roundup|weekly discussion|discussion thread)\]?|weekly free)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
