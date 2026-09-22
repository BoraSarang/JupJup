package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.dao.AppWithSources
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.CrawlLog
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.data.db.entity.NotificationLog
import com.borasarang.macjupjup.data.db.entity.VersionHistory

/** 앱 + 출처 묶음 (상세 API 응답용) */
data class AppWithSourceList(
    val app: App,
    val sources: List<com.borasarang.macjupjup.data.db.entity.AppSourceMapping>,
    val versions: List<VersionHistory>,
)

fun AppWithSources.toModel(versions: List<VersionHistory>) = AppWithSourceList(
    app = app,
    sources = sources,
    versions = versions,
)

/** 홈 통계 */
data class AppStats(
    val totalApps: Int,
    val activeSources: Int,
    val lastCollectedAt: Long?,
)

/** 트렌드 대시보드 집계 */
data class TrendStats(
    val byCategory: Map<String, Int>,
    val byLicense: Map<String, Int>,
    /** 수집처별 앱 수 (대표 sourceId 기준, 개수 내림차순) */
    val bySource: List<SourceCount>,
    val newLast7d: Int,
    val updatedLast7d: Int,
    val versionBumpsLast7d: Int,
    val aiTagCount: Int,
    val menuBarTagCount: Int,
)

/** 목록 조회 필터 */
data class AppFilter(
    val license: String? = null,
    val category: String? = null,
    val tag: String? = null,
    val q: String? = null,
    /** newest / updated / stars / rating */
    val sort: String = "newest",
    val page: Int = 1,
    val pageSize: Int = 50,
    /** true면 버전업(isNew=0 AND version 있음)만 */
    val bumped: Boolean = false,
    /** true면 실제 버전 변경 증거(prevVersion 있음)만 — Watchlist 업데이트 탭 (T-132) */
    val updatedOnly: Boolean = false,
    /** true면 신규(firstSeen 최근, isNew=1)만 — 앱 스토어 "신규 등록" 칩 */
    val newOnly: Boolean = false,
    /** 비어 있으면 전체 (대표 sourceId 기준) */
    val sourceIds: Set<String> = emptySet(),
)

data class PagedApps(
    val apps: List<AppListItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

/** 목록용 앱 + 대표 출처 1건 */
data class AppListItem(
    val app: App,
    val sourceName: String?,
    val sourceUrl: String?,
)

/** 일별 수집량 (그래프용) */
data class DayCollect(
    val day: String,
    val found: Long,
    val newCount: Long,
    val updated: Long,
    val runs: Long,
    val bySource: List<SourceCollect>,
)

/** 일자별 소스 수집량 */
data class SourceCollect(
    val sourceId: String,
    val sourceName: String,
    val found: Long,
    val newCount: Long,
    val updated: Long,
)

data class SourceStatus(
    val id: String,
    val name: String,
    val type: String,
    val baseUrl: String,
    val enabled: Boolean,
    val intervalHours: Int,
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    val lastStatus: String,
    val errorMessage: String?,
    val appCount: Int = 0,
)

fun CrawlSource.toStatus(appCount: Int = 0) = SourceStatus(
    id = id,
    name = name,
    type = type,
    baseUrl = baseUrl,
    enabled = enabled,
    intervalHours = intervalHours,
    intervalMinutes = intervalMinutes,
    lastRunAt = lastRunAt,
    lastStatus = lastStatus,
    errorMessage = errorMessage,
    appCount = appCount,
)

data class SettingsData(
    val port: Int,
    val retentionDays: Int,
    val autoStart: Boolean,
    val watchdogIntervalSec: Int,
    val githubToken: String = "",
    val translateKo: Boolean = true,
    val notifCrawlComplete: Boolean = true,
    val notifNewApp: Boolean = true,
    val notifFailure: Boolean = true,
    val crawlEnabled: Boolean = true,
)

/** 토큰 설정 여부만 노출 (값은 절대 외부 반환 금지) */
data class SettingsView(
    val port: Int,
    val retentionDays: Int,
    val autoStart: Boolean,
    val watchdogIntervalSec: Int,
    val githubTokenSet: Boolean,
    val translateKo: Boolean,
    val notifCrawlComplete: Boolean,
    val notifNewApp: Boolean,
    val notifFailure: Boolean,
)

fun SettingsData.toView() = SettingsView(
    port = port,
    retentionDays = retentionDays,
    autoStart = autoStart,
    watchdogIntervalSec = watchdogIntervalSec,
    githubTokenSet = githubToken.isNotBlank(),
    translateKo = translateKo,
    notifCrawlComplete = notifCrawlComplete,
    notifNewApp = notifNewApp,
    notifFailure = notifFailure,
)

data class RecentLog(
    val id: Long,
    val sourceName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val plansFound: Int,
    val plansNew: Int,
    val plansUpdated: Int,
    val errorMessage: String?,
)

fun CrawlLog.toRecent() = RecentLog(
    id = id,
    sourceName = sourceName,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status,
    plansFound = plansFound,
    plansNew = plansNew,
    plansUpdated = plansUpdated,
    errorMessage = errorMessage,
)

/** 소스 목록 1행: 상태 + 최근 로그 */
data class SourceListItem(
    val status: SourceStatus,
    val latestLog: RecentLog?,
)

/** 알림 리스트 아이템 */
data class NotificationItem(
    val id: Long,
    val type: String,
    val summary: String,
    val detailJson: String,
    val createdAt: Long,
    val isRead: Boolean,
)

fun NotificationLog.toItem() = NotificationItem(
    id = id,
    type = type,
    summary = summary,
    detailJson = detailJson,
    createdAt = createdAt,
    isRead = isRead,
)

/** 알림 상세 (포털·앱 공용) */
data class NotificationDetail(
    val type: String,
    val summary: String,
    val totalFound: Int,
    val newApps: Int,
    val updatedApps: Int,
    val failedCount: Int,
    val bySource: List<SourceCount>,
    val byCategory: List<CategoryCount>,
    val byLicense: List<LicenseCount>,
    val newAppsDetail: List<NewAppSummary>,
    val failedSources: List<FailedSource>,
    val startedAt: Long,
    val finishedAt: Long,
)

data class SourceCount(val sourceName: String, val count: Int, val sourceId: String = "")
data class CategoryCount(val category: String, val count: Int)
data class LicenseCount(val license: String, val count: Int)

data class NewAppSummary(
    val id: String,
    val name: String,
    val developer: String,
    val license: String,
    val version: String?,
)

data class FailedSource(val sourceId: String, val sourceName: String, val error: String)
