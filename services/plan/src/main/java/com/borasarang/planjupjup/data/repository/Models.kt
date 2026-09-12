package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.entity.CarrierBrand
import com.borasarang.planjupjup.data.db.entity.CrawlLog
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.data.db.entity.NotificationLog
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping
import com.borasarang.planjupjup.util.Constants
import kotlinx.coroutines.flow.Flow

/** 요금제 + 출처 묶음 (상세 API 응답용) */
data class PlanWithSources(
    val plan: Plan,
    val sources: List<PlanSourceMapping>,
)

/** 홈 통계 */
data class PlanStats(
    val totalPlans: Int,
    val activeSources: Int,
    val lastCollectedAt: Long?,
)

/** 목록 조회 필터 */
data class PlanFilter(
    val network: String? = null,
    val carrier: String? = null,
    val minDataGb: Int? = null,
    val maxPrice: Int? = null,
    val tag: String? = null,
    /** price_asc / price_desc / data_desc / newest */
    val sort: String = "price_asc",
    val page: Int = 1,
    val pageSize: Int = 50,
)

data class PagedPlans(
    val plans: List<PlanWithSources>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
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
)

fun CrawlSource.toStatus() = SourceStatus(
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
)

data class SettingsData(
    val port: Int,
    val retentionDays: Int,
    val autoStart: Boolean,
    val watchdogIntervalSec: Int,
    val notifCrawlComplete: Boolean = true,
    val notifNewPlan: Boolean = true,
    val notifFailure: Boolean = true,
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

/** 소스 목록 1행: 상태 + 최근 로그(마지막 수집일·수집 통계) */
data class SourceListItem(
    val status: SourceStatus,
    val latestLog: RecentLog?,
)

data class BrandInfo(
    val id: String,
    val name: String,
    val networkType: String,
    val homepageUrl: String?,
)

fun CarrierBrand.toInfo() = BrandInfo(
    id = id,
    name = name,
    networkType = networkType,
    homepageUrl = homepageUrl,
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

data class NotificationListResponse(
    val notifications: List<NotificationItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val unreadCount: Int,
)
data class NotificationDetailResponse(
    val notification: NotificationItem,
    val detail: NotificationDetail,
)

data class NotificationDetail(
    val type: String,
    val summary: String,
    val totalFound: Int,
    val newPlans: Int,
    val updatedPlans: Int,
    val failedCount: Int,
    val bySource: List<SourceCount>,
    val byBrand: List<BrandCount>,
    val byNetwork: List<NetworkCount>,
    val newPlansDetail: List<NewPlanSummary>,
    val failedSources: List<FailedSource>,
    val startedAt: Long,
    val finishedAt: Long,
)

data class SourceCount(
    val sourceName: String,
    val count: Int,
)

data class BrandCount(
    val brand: String,
    val count: Int,
)

data class NetworkCount(
    val network: String,
    val count: Int,
)

data class NewPlanSummary(
    val id: String,
    val carrierName: String,
    val planName: String,
    val price: Int,
    val dataAmount: String,
    val networkType: String,
)

data class FailedSource(
    val sourceId: String,
    val sourceName: String,
    val error: String,
)