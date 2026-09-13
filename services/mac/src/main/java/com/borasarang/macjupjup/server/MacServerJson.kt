package com.borasarang.macjupjup.server

import com.borasarang.common.server.putIfNotNull
import com.borasarang.macjupjup.data.repository.AppListItem
import com.borasarang.macjupjup.data.repository.AppWithSourceList
import com.borasarang.macjupjup.data.repository.SettingsView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 서버 JSON 매퍼 (R4). 순수 함수 — 단위 테스트 가능.
 * 동작 동결: HttpServerService에서 이동만.
 */
internal fun appsJson(
    items: List<AppListItem>,
    total: Int,
    page: Int,
    pageSize: Int,
): String {
    return buildJsonObject {
        put("apps", buildJsonArray {
            items.forEach { item ->
                add(
                    buildJsonObject {
                        appElement(item.app).entries.forEach { (key, value) -> put(key, value) }
                        putIfNotNull("sourceName", item.sourceName)
                        putIfNotNull("sourceUrl", item.sourceUrl)
                    },
                )
            }
        })
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }.toString()
}

internal fun detailJson(item: AppWithSourceList): String {
    return buildJsonObject {
        appElement(item.app).entries.forEach { (key, value) ->
            put(key, value)
        }
        put("sources", buildJsonArray {
            item.sources.forEach { s ->
                add(
                    buildJsonObject {
                        put("sourceName", s.sourceName)
                        s.sourceUrl?.let { put("sourceUrl", it) }
                    },
                )
            }
        })
        put("versions", buildJsonArray {
            item.versions.forEach { v ->
                add(
                    buildJsonObject {
                        put("version", v.version)
                        put("detectedAt", v.detectedAt)
                        v.notesSummary?.let { put("notesSummary", it) }
                        v.sourceUrl?.let { put("sourceUrl", it) }
                    },
                )
            }
        })
    }.toString()
}

/** 목록·상세 공통 앱 필드 (상세 7섹션 매핑 포함) */
internal fun appElement(a: com.borasarang.macjupjup.data.db.entity.App): JsonObject {
    return buildJsonObject {
        put("id", a.id)
        put("platform", a.platform)
        put("name", a.name)
        put("developer", a.developer)
        put("license", a.license)
        put("price", a.price)
        put("currency", a.currency)
        put("category", a.category)
        putIfNotNull("tags", a.tags)
        putIfNotNull("trackId", a.trackId)
        putIfNotNull("repoFullName", a.repoFullName)
        putIfNotNull("homepageUrl", a.homepageUrl)
        putIfNotNull("version", a.version)
        putIfNotNull("prevVersion", a.prevVersion)
        // ⑤ 새로운 기능: 요약 + 원문은 버전 히스토리/출처 링크로
        putIfNotNull("releaseNotesSummary", a.releaseNotesSummary)
        putIfNotNull("releaseNotes", a.releaseNotes)
        putIfNotNull("releaseNotesKo", a.releaseNotesKo)
        putIfNotNull("releaseDate", a.releaseDate)
        // ① 소개 발췌 / ② 스크린샷(CDN 직접 표시)
        putIfNotNull("descriptionSnippet", a.descriptionSnippet)
        putIfNotNull("descriptionKo", a.descriptionKo)
        putIfNotNull("screenshotUrls", a.screenshotUrls)
        putIfNotNull("iconUrl", a.iconUrl)
        // ③ 특징
        putIfNotNull("averageRating", a.averageRating)
        putIfNotNull("ratingCount", a.ratingCount)
        putIfNotNull("stars", a.stars)
        putIfNotNull("primaryLanguage", a.primaryLanguage)
        putIfNotNull("topics", a.topics)
        putIfNotNull("sellerName", a.sellerName)
        putIfNotNull("fileSize", a.fileSize)
        putIfNotNull("minOs", a.minOs)
        putIfNotNull("contentRating", a.contentRating)
        putIfNotNull("forks", a.forks)
        putIfNotNull("issues", a.issues)
        putIfNotNull("licenseName", a.licenseName)
        put("firstSeenAt", a.firstSeenAt)
        put("lastUpdatedAt", a.lastUpdatedAt)
        put("isNew", a.isNew)
    }
}

internal fun settingsJson(s: SettingsView): String {
    return buildJsonObject {
        put("port", s.port)
        put("retentionDays", s.retentionDays)
        put("autoStart", s.autoStart)
        put("watchdogIntervalSec", s.watchdogIntervalSec)
        // 토큰 값은 절대 반환하지 않음 — 설정 여부만
        put("githubTokenSet", s.githubTokenSet)
        put("translateKo", s.translateKo)
        put("notifCrawlComplete", s.notifCrawlComplete)
        put("notifNewApp", s.notifNewApp)
        put("notifFailure", s.notifFailure)
    }.toString()
}
