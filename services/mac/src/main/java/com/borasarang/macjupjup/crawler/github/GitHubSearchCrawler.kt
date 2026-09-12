package com.borasarang.macjupjup.crawler.github

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.int
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.Instant

/**
 * GitHub 신규·갱신 저장소 발굴.
 * 쿼리: topic:macos / topic:mac-app / topic:menu-bar, sort=updated (신규·갱신 포착 우선).
 * 인증 시 분당 30회·시간당 5000회. 미인증도 3회 호출이라 동작은 하나 토큰 권장.
 */
class GitHubSearchCrawler(
    source: CrawlSource,
    private val token: String = "",
    private val queries: List<String> = DEFAULT_QUERIES,
    /** 실행당 README 보강 상한 (레이트 절약, stars 순) */
    private val readmeLimit: Int = 10,
) : BaseCrawler(source) {

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val headers = githubHeaders(token)
        val drafts = mutableListOf<AppDraft>()
        for (q in queries) {
            val url = "https://api.github.com/search/repositories" +
                "?q=${URLEncoder.encode(q, "UTF-8")}&sort=updated&order=desc&per_page=$PER_PAGE&page=1"
            val body = fetchGetHeaders(url, headers)
            drafts += parseRepos(body)
            politenessDelay()
        }
        // 동일 repo 중복 제거 (쿼리 간 겹침)
        val seen = mutableSetOf<String>()
        val unique = drafts.filter { d ->
            seen.add(d.app.repoFullName ?: d.app.id)
        }
        DebugLogger.i("수집", "GitHub Search 완료 queries=${queries.size} found=${drafts.size} unique=${unique.size}")
        // README 보강: stars 상위만 (API 절약)
        val withReadme = unique.sortedByDescending { it.app.stars ?: 0 }.take(readmeLimit)
        val readmeMap = mutableMapOf<String, String>()
        for (d in withReadme) {
            val repo = d.app.repoFullName ?: continue
            try {
                val raw = fetchGetHeaders(
                    "https://api.github.com/repos/$repo/readme",
                    headers + ("Accept" to "application/vnd.github.raw"),
                )
                cleanReadme(raw)?.let { readmeMap[repo] = it }
            } catch (e: Exception) {
                DebugLogger.d("수집", "README 스킵 $repo: ${e.message}")
            }
            politenessDelay()
        }
        if (readmeMap.isEmpty()) return@runCatching unique
        return@runCatching unique.map { d ->
            val repo = d.app.repoFullName
            val readme = repo?.let { readmeMap[it] } ?: return@map d
            val merged = if (d.app.descriptionSnippet.isNullOrBlank()) readme
            else d.app.descriptionSnippet + "\n\n— README —\n" + readme
            d.copy(app = d.app.copy(descriptionSnippet = merged.take(2000)))
        }
    }

    /** README raw → 살균 + 2000자 절단. T-140: 마크다운 구조 보존 (포털 렌더용).
     *  제거: raw HTML 태그(XSS·번역 오동작 원천 차단), HTML 주석, 과도 개행.
     *  유지: 제목·굵게·코드·링크·목록·인용 (번역 단계에서 코드·URL은 미번역). */
    internal fun cleanReadme(raw: String): String? {
        if (raw.isBlank()) return null
        var t = raw
        t = t.replace(Regex("<!--[\\s\\S]*?-->"), "")
        t = t.replace(Regex("<[^>\\n]+>"), "")
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        t = t.trim().take(2000)
        return t.ifBlank { null }
    }

    internal fun parseRepos(body: String): List<AppDraft> {
        val items = try {
            Json.parseToJsonElement(body).jsonObject["items"]?.jsonArray ?: return emptyList()
        } catch (_: Exception) {
            parseFail("GitHub Search 응답")
        }
        return items.mapNotNull { el ->
            try {
                val o = el.jsonObject
                val fullName = o.str("full_name") ?: return@mapNotNull null
                val owner = o.jsonObject["owner"]?.jsonObject?.str("login") ?: ""
                val name = o.str("name") ?: return@mapNotNull null
                val topics = o["topics"]?.jsonArray?.mapNotNull {
                    if (it is JsonNull) null else it.jsonPrimitive.content
                } ?: emptyList()
                val licenseName = o.jsonObject["license"]?.jsonObject?.str("spdx_id")
                    ?.takeIf { it != "NOASSERTION" }
                val avatar = o.jsonObject["owner"]?.jsonObject?.str("avatar_url")
                buildDraft(
                    name = name,
                    developer = owner.ifBlank { fullName.substringBefore("/") },
                    repoFullName = fullName,
                    homepageUrl = o.str("homepage")?.takeIf { it.isNotBlank() },
                    version = o.str("default_branch")?.let { "$it 최신" },
                    descriptionSnippet = o.str("description"),
                    stars = o.int("stargazers_count"),
                    primaryLanguage = o.str("language"),
                    topics = topics,
                    iconUrl = avatar,
                    forks = o.int("forks_count"),
                    issues = o.int("open_issues_count"),
                    licenseName = licenseName,
                    detailUrl = o.str("html_url") ?: "https://github.com/$fullName",
                    releaseDate = o.str("pushed_at")?.let { parseEpoch(it) },
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        const val PER_PAGE = 30
        val DEFAULT_QUERIES = listOf(
            "topic:macos stars:>20",
            "topic:mac-app",
            "topic:menu-bar",
        )

        fun parseEpoch(iso: String): Long? = try {
            Instant.parse(iso).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
