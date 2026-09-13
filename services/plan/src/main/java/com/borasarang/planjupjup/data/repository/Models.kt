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
    val crawlEnabled: Boolean = true,
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

// ---------- 통계 모델 (R4: StatsRepository에서 이동, 동작 무변경) ----------

data class OverviewStats(
    val totalPlans: Int,
    val brandCount: Int,
    val networkCount: Int,
    val newThisWeek: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val unlimitedRatio: Double,
    val g5Ratio: Double,
    val avgDataGb: Double,
    val crawlCountToday: Int,
    val crawlFail24h: Int,
    val lastCollectedAt: Long?,
)

data class BrandStats(
    val brand: String,
    val mvnoNetwork: String,
    val planCount: Int,
    val newCount: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val avgDataGb: Double,
    val g5Count: Int,
    val pricePerGb: Int?,
)

data class NetworkStats(
    val network: String,
    val planCount: Int,
    val avgPrice: Int,
    val minPrice: Int,
    val maxPrice: Int,
    val avgDataGb: Double,
    val g5Ratio: Double,
    val unlimitedRatio: Double,
    val pricePerGb: Int?,
)

/** 가격대/데이터 용량 구간 분포. 버킷은 label + [min, max) */
data class DistributionBucket(
    val label: String,
    val count: Int,
    val min: Int?,
    val max: Int?,
)

object PlanDistribution {
    /** 가격대 버킷 경계 (원). 마지막은 개방 */
    private val PRICE_BUCKETS = listOf(
        0 to 10000, 10000 to 20000, 20000 to 30000,
        30000 to 50000, 50000 to 100000, 100000 to null,
    )

    fun priceBuckets(prices: List<Int>): List<DistributionBucket> {
        val counts = IntArray(PRICE_BUCKETS.size)
        prices.forEach { p ->
            if (p <= 0) return@forEach
            val idx = PRICE_BUCKETS.indexOfFirst { (lo, hi) ->
                p >= lo && (hi == null || p < hi)
            }
            if (idx >= 0) counts[idx]++
        }
        return PRICE_BUCKETS.mapIndexed { i, (lo, hi) ->
            DistributionBucket(
                label = when {
                    lo == 0 && hi != null -> "1만원 미만"
                    hi == null && lo == 100000 -> "10만원 이상"
                    hi != null -> "${lo / 10000}~${hi / 10000}만원"
                    else -> "10만원 이상"
                },
                count = counts[i],
                min = lo,
                max = hi,
            )
        }
    }

    /** 데이터 용량 구간 분포 (GB). 파싱 불가는 별도 버킷 */
    fun dataBuckets(amounts: List<com.borasarang.planjupjup.util.PlanMetrics.DataAmount>): List<DistributionBucket> {
        val buckets = listOf(
            "1GB 이하" to 0..1, "2~5GB" to 2..5, "6~15GB" to 6..15,
            "16~50GB" to 16..50, "51~100GB" to 51..100, "100GB 이상" to 101..Int.MAX_VALUE,
        )
        val counts = IntArray(buckets.size)
        var unparsed = 0
        amounts.forEach { a ->
            if (!a.isParsed || a.dataGb <= 0) { unparsed++; return@forEach }
            val idx = buckets.indexOfFirst { (_, r) -> a.dataGb in r }
            if (idx >= 0) counts[idx]++ else unparsed++
        }
        return buckets.mapIndexed { i, (label, range) ->
            DistributionBucket(label, counts[i], range.first, range.last)
        } + DistributionBucket("파싱 불가", unparsed, null, null)
    }
}

/** 시계열 점 (일별 수집/신규 추이) */
data class TrendPoint(
    val label: String,
    val ts: Long,
    val plansFound: Int,
    val plansNew: Int,
    val plansUpdated: Int,
    val failCount: Int,
)

/** 가성비 랭킹 1행 */
data class ValueRankItem(
    val id: String,
    val carrierName: String,
    val planName: String,
    val price: Int,
    val dataAmount: String,
    val voice: String,
    val sms: String,
    val networkType: String,
    val dataGb: Int,
    val score: Double,
    val scoreLabel: String,
)

/** 수집 건강도 */
data class CollectionHealth(
    val success24h: Int,
    val fail24h: Int,
    val avgDurationSec: Double,
    val sources: List<SourceHealth>,
    val lastCollectedAt: Long?,
)

data class SourceHealth(
    val sourceId: String,
    val sourceName: String,
    val lastStatus: String,
    val lastRunAt: Long?,
    val errorMessage: String?,
    val successRate: Double,
)

enum class InsightType { POSITIVE, WARNING, INFO }

data class Insight(
    val type: InsightType,
    val title: String,
    val text: String,
)