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
    /** 실행당 README 보강 상한 — 전문 기본 (레이트 여유 시 상향) */
    private val readmeLimit: Int = 90,
    /** DB에서 이미 README 보유 id — 전문 없는 초안을 우선 README 보강 (AppStorrent와 동일 패턴) */
    private val loadBodyIds: suspend (List<String>) -> Set<String> = { emptySet() },
    /** 이번 검색에 없는 DB 빈 longDescription 행 (JupJup-zxa 주기 백필) */
    private val loadMissingReadme: suspend (excludeIds: List<String>, limit: Int) -> List<AppDraft> = { _, _ -> emptyList() },
    /** 10건마다 체크포인트 — 저장 전 취소로 진행량 유실 방지 */
    private val onCheckpoint: suspend (List<AppDraft>) -> Unit = {},
) : BaseCrawler(source) {

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
        // 미인증 60req/h — 검색 3회 소모 후 실질 한도 ~57건. 토큰 없으면 상한 제한
        val effectiveLimit = if (token.isBlank()) {
            readmeLimit.coerceAtMost(UNAUTH_README_LIMIT)
        } else {
            readmeLimit
        }
        // 이번 검색에 없는 DB 빈 longDescription 행 합류 (sort=updated 미포함 고아 보강, JupJup-zxa)
        val missing = try {
            loadMissingReadme(unique.map { it.app.id }, effectiveLimit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLogger.w("수집", "GitHub README 백필 대상 조회 스킵: ${e.message}")
            emptyList()
        }
        val uniqueIds = unique.map { it.app.id }.toSet()
        val missingDeduped = missing.filter { it.app.id !in uniqueIds }
        if (missingDeduped.isNotEmpty()) {
            DebugLogger.i("수집", "GitHub README 백필 대상 ${missingDeduped.size}건 (검색 미포함)")
        }
        // README 전문 보강 — 검색분은 DB 전문 없는 id 우선 (stars 동순위 세컨더리)
        val bodyIds = try {
            loadBodyIds(unique.map { it.app.id })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLogger.w("수집", "GitHub 본문 id 조회 스킵: ${e.message}")
            emptySet()
        }
        // 백필(오래된 빈 행 lastUpdatedAt ASC 회전) 선행 + 검색분
        val prioritizedMissing = missingDeduped.sortedBy { it.app.lastUpdatedAt }
        val missingIds = prioritizedMissing.map { it.app.id }.toSet()
        val prioritizedUnique = prioritizeForReadme(unique, bodyIds)
            .filter { it.app.id !in missingIds }
        val prioritized = prioritizedMissing + prioritizedUnique
        val withReadme = prioritized.take(effectiveLimit)
        val readmeMap = mutableMapOf<String, String>()
        var consecutive403 = 0
        var processed = 0
        // 체크포인트: 이번 실행에서 새로 보강된 draft 버퍼 (10건마다 flush)
        val checkpointPending = mutableListOf<AppDraft>()
        for (d in withReadme) {
            val repo = d.app.repoFullName ?: continue
            try {
                val raw = fetchGetHeaders(
                    "https://api.github.com/repos/$repo/readme",
                    headers + ("Accept" to "application/vnd.github.raw"),
                )
                consecutive403 = 0
                cleanReadme(raw)?.let { readmeMap[repo] = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (checkpointPending.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        onCheckpoint(checkpointPending.toList())
                    }
                    checkpointPending.clear()
                }
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("code=403") || msg.contains("code=429")) {
                    consecutive403++
                    DebugLogger.w("수집", "README 레이트리밋 $repo ($consecutive403): $msg")
                    if (consecutive403 >= RATE_LIMIT_ABORT) {
                        DebugLogger.w("수집", "GitHub README 레이트리밋 연속 ${consecutive403}회 — 이번 실행 중단 (보강 ${readmeMap.size}건 유지)")
                        break
                    }
                } else {
                    DebugLogger.d("수집", "README 스킵 $repo: $msg")
                }
            }
            processed++
            // 성공 보강분이 있으면 pending에 적재 → 10건마다 체크포인트
            val enriched = readmeMap[repo]?.let { enrichDraftWithReadme(d, it) }
            if (enriched != null) {
                checkpointPending += enriched
                if (checkpointPending.size >= CHECKPOINT_EVERY) {
                    onCheckpoint(checkpointPending.toList())
                    checkpointPending.clear()
                }
            }
            politenessDelay()
        }
        // 잔여 체크포인트
        if (checkpointPending.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                onCheckpoint(checkpointPending.toList())
            }
            checkpointPending.clear()
        }
        DebugLogger.i(
            "수집",
            "[FEATURE] GitHub README 보강 readmeMap=${readmeMap.size} processed=$processed " +
                "limit=$effectiveLimit token=${token.isNotBlank()} missingTried=${missingDeduped.size}",
        )
        if (readmeMap.isEmpty()) return unique
        // 검색 결과: 전건 반환(README 있으면 주입). 백필분: README 성공분만 저장 대상에 포함
        val uniqueOut = unique.map { d ->
            val repo = d.app.repoFullName
            val readme = repo?.let { readmeMap[it] } ?: return@map d
            enrichDraftWithReadme(d, readme)
        }
        val missingOut = missingDeduped.mapNotNull { d ->
            val repo = d.app.repoFullName ?: return@mapNotNull null
            val readme = readmeMap[repo] ?: return@mapNotNull null
            enrichDraftWithReadme(d, readme)
        }
        return uniqueOut + missingOut
    }

    /**
     * DB에서 본문 보유 id 기준 — 본문 없는 초안 선행 (같은 head 반복 방지).
     * 동순위는 stars 내림차순.
     */
    internal fun prioritizeForReadme(
        drafts: List<AppDraft>,
        dbHasBodyIds: Set<String>,
    ): List<AppDraft> =
        drafts.sortedWith(
            compareBy<AppDraft> { d ->
                val hasBody = dbHasBodyIds.contains(d.app.id) ||
                    !d.app.longDescription.isNullOrBlank()
                if (hasBody) 1 else 0
            }.thenByDescending { it.app.stars ?: 0 },
        )

    /** README 주입 — snippet 배지/H1 방어 포함 */
    private fun enrichDraftWithReadme(d: AppDraft, readme: String): AppDraft {
        val apiDesc = d.app.descriptionSnippet?.takeIf { it.isNotBlank() }
        val firstLine = readme.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        val short = apiDesc
            ?: firstLine?.takeIf { !isBadgeOrHeading(it) }
            ?: readme.lineSequence().mapNotNull { l ->
                l.trim().takeIf { it.isNotBlank() && !isBadgeOrHeading(it) }
            }.firstOrNull()
            ?: readme.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN)
        val body = readme.take(com.borasarang.macjupjup.util.Constants.APP_BODY_MAX)
        return d.copy(
            app = d.app.copy(
                descriptionSnippet = short.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN),
                longDescription = body,
            ),
        )
    }

    private fun enrichWithReadme(drafts: List<AppDraft>, readmeMap: Map<String, String>): List<AppDraft> =
        drafts.map { d ->
            val readme = d.app.repoFullName?.let { readmeMap[it] } ?: d
            if (readme === d) d else enrichDraftWithReadme(d, readmeMap[d.app.repoFullName!!]!!)
        }

    /** README 첫 줄이 배지/헤딩/HTML/URL이면 snippet으로 부적합 */
    internal fun isBadgeOrHeading(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return true
        if (t.startsWith("#")) return true
        if (t.startsWith("[!") || t.startsWith("![")) return true
        if (t.startsWith("<")) return true
        if (t.startsWith("http://") || t.startsWith("https://")) return true
        if (t.startsWith("---") || t.startsWith("***") || t.startsWith("===")) return true
        return false
    }

    /** README raw → 살균 + 전문 절단. T-140: 마크다운 구조 보존 (포털 렌더용).
     *  제거: raw HTML 태그(XSS·번역 오동작 원천 차단), HTML 주석, 과도 개행.
     *  유지: 제목·굵게·코드·링크·목록·인용 (번역 단계에서 코드·URL은 미번역). */
    internal fun cleanReadme(raw: String): String? {
        if (raw.isBlank()) return null
        var t = raw
        t = t.replace(Regex("<!--[\\s\\S]*?-->"), "")
        t = t.replace(Regex("<[^>\\n]+>"), "")
        t = t.replace(Regex("[ \\t]+"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        t = t.trim().take(com.borasarang.macjupjup.util.Constants.APP_BODY_MAX)
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
        /** 10건마다 README 보강 체크포인트 (AppStorrent와 동일) */
        const val CHECKPOINT_EVERY = 10
        /** 레이트리밋(403/429) 연속 허용 — 초과 시 이번 실행 중단 */
        const val RATE_LIMIT_ABORT = 3
        /** 미인증 README 보강 상한 (검색 3회 제외, 60req/h 실질 한도) */
        const val UNAUTH_README_LIMIT = 30
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
