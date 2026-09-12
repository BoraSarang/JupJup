package com.borasarang.macjupjup.crawler.itunes

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.AppSourceMappingHelper
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * iTunes 이름 대조 매칭 (US, 키 불필요, 주 1회).
 * trackId·repo가 없는 앱을 search로 대조해 trackId를 부여.
 * 엄격 규칙: 이름 정규화 완전일치 + 개발사명 포함 일치. 미달은 그대로 둠.
 * 매칭 후 다음 lookup 폴링부터 전체 상세 자동 보완.
 */
class ITunesNameMatcher(
    source: CrawlSource,
    private val repoProvider: suspend () -> List<App>,
    private val maxApps: Int = 100,
) : BaseCrawler(source) {

    /** Room 기반 편의 생성자 */
    constructor(
        source: CrawlSource,
        db: MacDatabase,
        maxApps: Int = 100,
    ) : this(source, { db.appDao().getAppsWithoutIds(maxApps) }, maxApps)

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val targets = repoProvider().take(maxApps)
        val drafts = mutableListOf<AppDraft>()
        var matched = 0
        for (app in targets) {
            try {
                val url = itunesSearchUrl(app.name)
                val body = fetchGet(url)
                val results = parseSearch(body)
                val hit = results.firstOrNull { isStrictMatch(app.name, app.developer, it) }
                    ?: singleExactFallback(app.name, app.developer, results)
                if (hit != null) {
                    val now = System.currentTimeMillis()
                    drafts += AppDraft(
                        app.copy(trackId = hit.trackId, lastUpdatedAt = now, isNew = false),
                        listOf(
                            AppSourceMappingHelper.mapping(
                                appId = app.id,
                                sourceName = source.name,
                                sourceUrl = hit.trackViewUrl,
                                now = now,
                            ),
                        ),
                    )
                    matched++
                    DebugLogger.i("대조", "매칭 성공: ${app.name} → trackId=${hit.trackId}")
                }
            } catch (e: Exception) {
                DebugLogger.d("대조", "대조 스킵 ${app.name}: ${e.message}")
            }
            politenessDelay()
        }
        DebugLogger.i("대조", "이름 대조 완료 checked=${targets.size} matched=$matched")
        drafts
    }

    data class SearchHit(
        val trackId: Long,
        val trackName: String,
        val seller: String,
        val trackViewUrl: String,
    )

    internal fun parseSearch(body: String): List<SearchHit> {        val arr = try {
            Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        } catch (_: Exception) {
            parseFail("iTunes Search")
        }
        return arr.mapNotNull { el ->
            try {
                val o = el.jsonObject
                val trackId = o["trackId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return@mapNotNull null
                SearchHit(
                    trackId = trackId,
                    trackName = o.str("trackName") ?: return@mapNotNull null,
                    seller = o.str("sellerName") ?: o.str("artistName") ?: "",
                    trackViewUrl = o.str("trackViewUrl") ?: "https://apps.apple.com/us/app/id$trackId",
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        /** MergeUtils 단일 진실에 위임 (R1-11) */
        fun normalize(s: String): String =
            com.borasarang.macjupjup.util.MergeUtils.normalizeName(s)

        /** 수집원 표기 개발사 — 실제 개발사 아님 (PH·HN·MMB 제거 후 잔재 행 매칭용으로 유지).
         *  신규 수집은 6종이므로 해당 표기는 더 이상 생성되지 않음. */
        fun isPlaceholderDeveloper(developer: String): Boolean {
            val dev = normalize(developer)
            return dev == "setapp" || dev.startsWith("hn") || dev == "producthunt" ||
                dev == "macmenubar"
        }

        /** 엄격 매칭: 이름 정규화 완전일치 + 개발사 포함 일치(양방향) */
        fun isStrictMatch(appName: String, appDeveloper: String, hit: SearchHit): Boolean {
            if (normalize(appName) != normalize(hit.trackName)) return false
            val dev = normalize(appDeveloper)
            val seller = normalize(hit.seller)
            if (dev.isBlank() || seller.isBlank()) return false
            // 수집원 표기는 개발사로 취급하지 않음 → 단일 후보 폴백으로 위임
            if (isPlaceholderDeveloper(appDeveloper)) return false
            return seller.contains(dev) || dev.contains(seller)
        }

        /**
         * 단일 후보 폴백: 개발사가 수집원 표기이고 검색 결과가 정확히 1건이며
         * 이름이 정규화 완전일치일 때만 매칭. 그 외는 null.
         */
        fun singleExactFallback(
            appName: String,
            appDeveloper: String,
            results: List<SearchHit>,
        ): SearchHit? {
            if (!isPlaceholderDeveloper(appDeveloper)) return null
            if (results.size != 1) return null
            val hit = results[0]
            if (normalize(appName) != normalize(hit.trackName)) return null
            return hit
        }
    }
}
