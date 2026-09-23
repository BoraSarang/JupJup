package com.borasarang.macjupjup.crawler.itunes

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.AppSourceMappingHelper
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.AppleCategoryMap
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * iTunes Lookup 버전 폴링 (US, 키 불필요, 분당 ~20회).
 * trackId 200개 묶음 1회 호출. 버전 변경 → bump draft (prevVersion·노트 기록).
 * 상세 미보유 앱은 스크린샷·평점·소개·노트도 함께 보완.
 */
class ITunesLookupPoller(
    source: CrawlSource,
    private val repoProvider: suspend () -> List<App>,
    private val batchSize: Int = 200,
    private val maxApps: Int = 400,
) : BaseCrawler(source) {

    /** Room 기반 편의 생성자 */
    constructor(
        source: CrawlSource,
        db: MacDatabase,
        batchSize: Int = 200,
        maxApps: Int = 400,
    ) : this(source, { db.appDao().getAppsWithTrackId(maxApps) }, batchSize, maxApps)

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val targets = repoProvider().take(maxApps)
        val drafts = mutableListOf<AppDraft>()
        var bumped = 0
        var enriched = 0
        for (chunk in targets.chunked(batchSize)) {
            val ids = chunk.mapNotNull { it.trackId }.joinToString(",")
            if (ids.isBlank()) continue
            try {
                val body = fetchGet(itunesLookupUrl(ids))
                val results = parseLookup(body)
                val byId = chunk.associateBy { it.trackId }
                for (r in results) {
                    val app = byId[r.trackId] ?: continue
                    val now = System.currentTimeMillis()
                    // T-132: 공백 차이 버전 오판 방지 (정규화 비교)
                    val versionChanged = r.version != null && app.version != null &&
                        !com.borasarang.macjupjup.util.MergeUtils.sameVersion(r.version, app.version)
                    val needsEnrich = app.screenshotUrls.isNullOrBlank() && !r.screenshotUrls.isNullOrEmpty() ||
                        app.averageRating == null && r.averageRating != null ||
                        app.descriptionSnippet.isNullOrBlank() && !r.description.isNullOrBlank() ||
                        app.longDescription.isNullOrBlank() && !r.description.isNullOrBlank() ||
                        app.iconUrl.isNullOrBlank() && r.artwork != null ||
                        app.supportedLanguages.isNullOrBlank() && !r.supportedLanguages.isNullOrBlank()
                    if (versionChanged || app.version == null && r.version != null || needsEnrich) {
                        if (versionChanged) bumped++ else enriched++
                        val appleDesc = r.description
                        val updated = app.copy(
                            version = r.version ?: app.version,
                            prevVersion = if (versionChanged) app.version else app.prevVersion,
                            releaseNotesSummary = r.releaseNotes
                                ?.take(com.borasarang.macjupjup.util.Constants.MAX_SUMMARY_LEN)
                                ?: app.releaseNotesSummary,
                            releaseNotes = r.releaseNotes
                                ?.take(com.borasarang.macjupjup.util.Constants.RELEASE_NOTES_MAX)
                                ?: app.releaseNotes,
                            releaseDate = r.releaseDate ?: app.releaseDate,
                            // 짧은 소개: 기존이 짧으면 Apple 전문에서 재추출, 전문: 긴 쪽
                            descriptionSnippet = when {
                                app.descriptionSnippet.isNullOrBlank() ->
                                    appleDesc?.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN)
                                appleDesc != null && (app.descriptionSnippet?.length ?: 0) <
                                    com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN &&
                                    appleDesc.length > (app.descriptionSnippet?.length ?: 0) ->
                                    appleDesc.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN)
                                else -> app.descriptionSnippet
                            },
                            longDescription = longerText(
                                app.longDescription,
                                appleDesc?.take(com.borasarang.macjupjup.util.Constants.APP_BODY_MAX),
                            ),
                            screenshotUrls = app.screenshotUrls
                                ?: r.screenshotUrls?.take(10)?.joinToString("\n"),
                            averageRating = app.averageRating ?: r.averageRating,
                            ratingCount = app.ratingCount ?: r.ratingCount,
                            iconUrl = app.iconUrl ?: r.artwork,
                            sellerName = app.sellerName ?: r.seller,
                            fileSize = app.fileSize ?: r.fileSize,
                            minOs = app.minOs ?: r.minOs,
                            contentRating = app.contentRating ?: r.contentRating,
                            supportedLanguages = app.supportedLanguages ?: r.supportedLanguages,
                            category = if (app.sourceId == com.borasarang.macjupjup.util.Constants.SOURCE_CHART_RSS) {
                                app.category
                            } else {
                                r.appleCategory?.let { AppleCategoryMap.map(it) } ?: app.category
                            },
                            // 버전 bump만 "업데이트" — 순수 enrich(스크린샷·평점 보완)는 lastUpdatedAt 보존 (최신순 오염 방지)
                            lastUpdatedAt = if (versionChanged || (app.version == null && r.version != null)) {
                                now
                            } else {
                                app.lastUpdatedAt
                            },
                            isNew = false,
                        )
                        drafts += AppDraft(
                            updated,
                            listOf(
                                AppSourceMappingHelper.mapping(
                                    appId = app.id,
                                    sourceName = source.name,
                                    sourceUrl = r.trackViewUrl
                                        ?: "https://apps.apple.com/us/app/id${r.trackId}",
                                    now = now,
                                ),
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                DebugLogger.w("수집", "lookup 배치 실패: ${e.message}")
            }
            politenessDelay()
        }
        DebugLogger.i("수집", "iTunes Lookup 완료 checked=${targets.size} bumped=$bumped enriched=$enriched")
        drafts
    }

    private fun longerText(a: String?, b: String?): String? {
        if (a.isNullOrBlank()) return b
        if (b.isNullOrBlank()) return a
        return if (b.length > a.length) b else a
    }

    data class LookupResult(
        val trackId: Long,
        val version: String?,
        val releaseNotes: String?,
        val releaseDate: Long?,
        val description: String?,
        val screenshotUrls: List<String>?,
        val averageRating: Double?,
        val ratingCount: Int?,
        val appleCategory: String?,
        val trackViewUrl: String?,
        val artwork: String?,
        val seller: String?,
        val fileSize: Long?,
        val minOs: String?,
        val contentRating: String?,
        /** 지원언어 ISO CSV (languageCodesISO2A 배열 → "en,ko") */
        val supportedLanguages: String?,
    )

    internal fun parseLookup(body: String): List<LookupResult> {
        val arr = try {
            Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        } catch (_: Exception) {
            parseFail("iTunes Lookup")
        }
        return arr.mapNotNull { el ->
            try {
                val o = el.jsonObject
                val trackId = o["trackId"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: return@mapNotNull null
                val shots = o["screenshotUrls"]?.jsonArray?.mapNotNull {
                    if (it is JsonNull) null else it.jsonPrimitive.content
                }
                val langs = o["languageCodesISO2A"]?.let { arrEl ->
                    try {
                        arrEl.jsonArray.mapNotNull { item ->
                            if (item is JsonNull) null else item.jsonPrimitive.content
                        }.filter { it.isNotBlank() }.ifEmpty { null }
                    } catch (_: Exception) {
                        null
                    }
                }
                LookupResult(
                    trackId = trackId,
                    version = o.str("version"),
                    releaseNotes = o.str("releaseNotes"),
                    releaseDate = o.str("currentVersionReleaseDate")?.let {
                        com.borasarang.macjupjup.crawler.chart.ChartRssCrawler.parseDate(it)
                    },
                    description = o.str("description"),
                    screenshotUrls = shots?.ifEmpty { null },
                    averageRating = o["averageUserRating"]?.jsonPrimitive?.content?.toDoubleOrNull(),
                    ratingCount = o["userRatingCount"]?.jsonPrimitive?.content?.toIntOrNull(),
                    appleCategory = o["primaryGenreName"]?.jsonPrimitive?.content,
                    trackViewUrl = o.str("trackViewUrl"),
                    artwork = o.str("artworkUrl512") ?: o.str("artworkUrl100"),
                    seller = o.str("sellerName"),
                    fileSize = o.str("fileSizeBytes")?.toLongOrNull(),
                    minOs = o.str("minimumOsVersion"),
                    contentRating = o.str("trackContentRating"),
                    supportedLanguages = langs?.joinToString(","),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
