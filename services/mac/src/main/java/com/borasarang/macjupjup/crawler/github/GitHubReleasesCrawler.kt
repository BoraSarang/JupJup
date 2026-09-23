package com.borasarang.macjupjup.crawler.github

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.AppSourceMappingHelper
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * DB에 repoFullName이 있는 앱의 릴리즈 추적.
 * `/repos/{o}/{r}/releases?per_page=3` 최신 태그 vs 저장 version 비교.
 * 다르면 버전 bump draft 반환 (AppRepository가 version_history 기록).
 */
class GitHubReleasesCrawler(
    source: CrawlSource,
    private val repoProvider: suspend () -> List<com.borasarang.macjupjup.data.db.entity.App>,
    private val token: String = "",
    private val maxRepos: Int = 30,
) : BaseCrawler(source) {

    /** Room 기반 편의 생성자 */
    constructor(
        source: CrawlSource,
        db: MacDatabase,
        token: String = "",
        maxRepos: Int = 30,
    ) : this(source, { db.appDao().getReposForReleaseCheck(maxRepos) }, token, maxRepos)

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val headers = githubHeaders(token)
        val targets = repoProvider().take(maxRepos)
        val drafts = mutableListOf<AppDraft>()
        var bumped = 0
        for (app in targets) {
            val repo = app.repoFullName ?: continue
            try {
                val body = fetchGetHeaders(
                    "https://api.github.com/repos/$repo/releases?per_page=3",
                    headers,
                )
                val latest = parseLatestRelease(body) ?: continue
                if (latest.tag != null && !com.borasarang.macjupjup.util.MergeUtils.sameVersion(latest.tag, app.version)) {
                    val now = System.currentTimeMillis()
                    val notes = cleanNotes(latest.notes)
                    val updated = app.copy(
                        version = latest.tag,
                        prevVersion = app.version,
                        releaseNotesSummary = notes?.take(com.borasarang.macjupjup.util.Constants.MAX_SUMMARY_LEN),
                        releaseNotes = notes?.take(com.borasarang.macjupjup.util.Constants.RELEASE_NOTES_MAX)
                            ?: app.releaseNotes,
                        lastUpdatedAt = now,
                        isNew = false,
                    )
                    drafts += AppDraft(
                        updated,
                        listOf(
                            AppSourceMappingHelper.mapping(
                                appId = app.id,
                                sourceName = source.name,
                                sourceUrl = latest.url ?: "https://github.com/$repo/releases",
                                now = now,
                            ),
                        ),
                    )
                    bumped++
                }
            } catch (e: Exception) {
                // 404(릴리즈 없음) 등은 해당 repo만 스킵 — 전체 실패 아님
                DebugLogger.d("수집", "릴리즈 조회 스킵 $repo: ${e.message}")
            }
            politenessDelay()
        }
        DebugLogger.i("수집", "GitHub Releases 완료 checked=${targets.size} bumped=$bumped")
        drafts
    }

    data class LatestRelease(val tag: String?, val notes: String?, val url: String?)

    /** 릴리즈노트 살균 (T-143): 날 HTML 태그·주석 제거, 마크다운 구조 유지, 과도 개행 정리 */
    internal fun cleanNotes(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var t = raw
        t = t.replace(Regex("<!--[\\s\\S]*?-->"), "")
        t = t.replace(Regex("</?[a-zA-Z][^>\\n]*>"), "")
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        t = t.trim()
        return t.ifBlank { null }
    }

    internal fun parseLatestRelease(body: String): LatestRelease? {
        val arr = try {
            Json.parseToJsonElement(body).jsonArray
        } catch (_: Exception) {
            parseFail("GitHub Releases 응답")
        }
        if (arr.isEmpty()) return null
        val o = arr[0].jsonObject
        if (o.str("draft") == "true") return null
        return LatestRelease(
            tag = o.str("tag_name"),
            notes = o.str("body"),
            url = o.str("html_url"),
        )
    }
}
