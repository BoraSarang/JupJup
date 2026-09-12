package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.NotificationLog
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.preferences.PreferencesManager
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepository(private val db: PlanDatabase) {

    suspend fun insert(log: NotificationLog): Long = db.notificationLogDao().insert(log)

    suspend fun getById(id: Long): NotificationLog? = db.notificationLogDao().getById(id)

    suspend fun getPaged(
        type: String?,
        isRead: Boolean?,
        page: Int,
        pageSize: Int,
    ): List<NotificationLog> {
        val offset = (page - 1) * pageSize
        return db.notificationLogDao().getPaged(
            type = type?.takeIf { it.isNotBlank() },
            isRead = isRead,
            limit = pageSize.coerceIn(1, 100),
            offset = offset,
        )
    }

    suspend fun count(type: String?, isRead: Boolean?): Int =
        db.notificationLogDao().count(
            type = type?.takeIf { it.isNotBlank() },
            isRead = isRead,
        )

    suspend fun countUnread(): Int = db.notificationLogDao().countUnread()

    suspend fun markAsRead(id: Long): Int = db.notificationLogDao().markAsRead(id)

    suspend fun markAllAsRead(): Int = db.notificationLogDao().markAllAsRead()

    suspend fun delete(id: Long): Int = db.notificationLogDao().delete(id)

    suspend fun deleteOlderThan(cutoff: Long): Int = db.notificationLogDao().deleteOlderThan(cutoff)

    suspend fun deleteAll(): Int = db.notificationLogDao().deleteAll()
}

class NotificationService(
    private val db: PlanDatabase,
    private val planRepository: PlanRepository,
    private val sourceRepository: SourceRepository,
    private val preferences: PreferencesManager,
) {
    private val repo = NotificationRepository(db)
    private val json = Json { ignoreUnknownKeys = true }

    private fun detailToJson(detail: NotificationDetail): String {
        return Json.encodeToString(
            buildJsonObject {
                put("type", detail.type)
                put("summary", detail.summary)
                put("totalFound", detail.totalFound)
                put("newPlans", detail.newPlans)
                put("updatedPlans", detail.updatedPlans)
                put("failedCount", detail.failedCount)
                put(
                    "bySource",
                    JsonArray(detail.bySource.map {
                        buildJsonObject {
                            put("sourceName", it.sourceName)
                            put("count", it.count)
                        }
                    }),
                )
                put(
                    "byBrand",
                    JsonArray(detail.byBrand.map {
                        buildJsonObject {
                            put("brand", it.brand)
                            put("count", it.count)
                        }
                    }),
                )
                put(
                    "byNetwork",
                    JsonArray(detail.byNetwork.map {
                        buildJsonObject {
                            put("network", it.network)
                            put("count", it.count)
                        }
                    }),
                )
                put(
                    "newPlansDetail",
                    JsonArray(detail.newPlansDetail.map {
                        buildJsonObject {
                            put("id", it.id)
                            put("carrierName", it.carrierName)
                            put("planName", it.planName)
                            put("price", it.price)
                            put("dataAmount", it.dataAmount)
                            put("networkType", it.networkType)
                        }
                    }),
                )
                put(
                    "failedSources",
                    JsonArray(detail.failedSources.map {
                        buildJsonObject {
                            put("sourceId", it.sourceId)
                            put("sourceName", it.sourceName)
                            put("error", it.error)
                        }
                    }),
                )
                put("startedAt", detail.startedAt)
                put("finishedAt", detail.finishedAt)
            },
        )
    }

    private suspend fun settings(): SettingsData {
        return try {
            preferences.getSettings()
        } catch (_: Exception) {
            SettingsData(
                port = Constants.DEFAULT_PORT,
                retentionDays = Constants.DEFAULT_RETENTION_DAYS,
                autoStart = Constants.DEFAULT_AUTO_START,
                watchdogIntervalSec = Constants.DEFAULT_WATCHDOG_INTERVAL_SEC,
            )
        }
    }

    /** 수집 완료 알림 생성 */
    suspend fun createCrawlCompleteNotification(
        sourceResults: Map<String, CrawlResult>,
        failedSources: List<FailedSourceInfo>,
        newPlans: List<Plan>,
    ) {
        if (!settings().notifCrawlComplete) return
        val summary = buildCrawlSummary(sourceResults, failedSources)
        val detail = buildDetailJson(sourceResults, failedSources, newPlans)

        val log = NotificationLog(
            type = NotificationType.CRAWL_COMPLETE,
            summary = summary,
            detailJson = detailToJson(detail),
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(log)
    }

    /** 신규 요금제 발견 알림 생성 */
    suspend fun createNewPlansNotification(newPlans: List<Plan>) {
        if (newPlans.isEmpty()) return
        if (!settings().notifNewPlan) return

        val summary = "오늘 ${newPlans.size}개의 새로운 요금제가 발견되었습니다"
        val detail = buildNewPlansDetail(newPlans)

        val log = NotificationLog(
            type = NotificationType.NEW_PLANS_FOUND,
            summary = summary,
            detailJson = detailToJson(detail),
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(log)
    }

    /** 수집 실패 알림 (5회 연속 실패 등) */
    suspend fun createFailureNotification(sourceName: String, error: String, streak: Int) {
        if (!settings().notifFailure) return
        val summary = "$sourceName 수집이 ${streak}회 연속 실패했습니다: $error"
        val detail = buildFailureDetail(sourceName, error, streak)

        val log = NotificationLog(
            type = NotificationType.CRAWL_FAILED_STREAK,
            summary = summary,
            detailJson = detailToJson(detail),
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(log)
    }

    /** 일일/주간 요약 알림 */
    suspend fun createSummaryNotification(summary: String, detail: NotificationDetail) {
        val log = NotificationLog(
            type = NotificationType.CRAWL_SUMMARY,
            summary = summary,
            detailJson = detailToJson(detail),
            createdAt = System.currentTimeMillis(),
        )
        repo.insert(log)
    }

    private fun buildCrawlSummary(
        sourceResults: Map<String, CrawlResult>,
        failedSources: List<FailedSourceInfo>,
    ): String {
        val totalFound = sourceResults.values.sumOf { it.found }
        val totalNew = sourceResults.values.sumOf { it.created }
        val totalUpdated = sourceResults.values.sumOf { it.updated }
        val failedCount = failedSources.size
        return "수집 완료: 전체 ${totalFound}건, 신규 ${totalNew}건, 갱신 ${totalUpdated}건${if (failedCount > 0) ", 실패 ${failedCount}건" else ""}"
    }

    private fun buildDetailJson(
        sourceResults: Map<String, CrawlResult>,
        failedSources: List<FailedSourceInfo>,
        newPlans: List<Plan>,
    ): NotificationDetail {
        val bySource = sourceResults.map { (_, r) -> SourceCount(r.sourceName, r.found) }
        val byBrand = groupByBrand(newPlans).map { BrandCount(it.key, it.value) }
        val byNetwork = groupByNetwork(newPlans).map { NetworkCount(it.key, it.value) }
        val newPlansDetail = newPlans.map { toNewPlanSummary(it) }
        val failedSourcesList = failedSources.map { FailedSource(it.sourceId, it.sourceName, it.error) }

        return NotificationDetail(
            type = NotificationType.CRAWL_COMPLETE,
            summary = buildCrawlSummary(sourceResults, failedSources),
            totalFound = sourceResults.values.sumOf { it.found },
            newPlans = sourceResults.values.sumOf { it.created },
            updatedPlans = sourceResults.values.sumOf { it.updated },
            failedCount = failedSources.size,
            bySource = bySource,
            byBrand = byBrand,
            byNetwork = byNetwork,
            newPlansDetail = newPlansDetail,
            failedSources = failedSourcesList,
            startedAt = 0,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private fun buildNewPlansDetail(newPlans: List<Plan>): NotificationDetail {
        val byBrand = groupByBrand(newPlans)
        val byNetwork = groupByNetwork(newPlans)
        val newPlansDetail = newPlans.map { toNewPlanSummary(it) }

        return NotificationDetail(
            type = NotificationType.NEW_PLANS_FOUND,
            summary = "오늘 ${newPlans.size}개의 새로운 요금제가 발견되었습니다",
            totalFound = newPlans.size,
            newPlans = newPlans.size,
            updatedPlans = 0,
            failedCount = 0,
            bySource = emptyList(),
            byBrand = groupByBrand(newPlans).map { BrandCount(it.key, it.value) },
            byNetwork = groupByNetwork(newPlans).map { NetworkCount(it.key, it.value) },
            newPlansDetail = newPlans.map { toNewPlanSummary(it) },
            failedSources = emptyList(),
            startedAt = 0,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private fun buildFailureDetail(sourceName: String, error: String, streak: Int): NotificationDetail {
        return NotificationDetail(
            type = NotificationType.CRAWL_FAILED_STREAK,
            summary = "$sourceName 수집이 ${streak}회 연속 실패했습니다: $error",
            totalFound = 0,
            newPlans = 0,
            updatedPlans = 0,
            failedCount = 1,
            bySource = emptyList(),
            byBrand = emptyList(),
            byNetwork = emptyList(),
            newPlansDetail = emptyList(),
            failedSources = listOf(FailedSource("unknown", sourceName, error)),
            startedAt = 0,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private fun toNewPlanSummary(plan: Plan): NewPlanSummary = NewPlanSummary(
        id = plan.id,
        carrierName = plan.carrierName,
        planName = plan.planName,
        price = plan.price,
        dataAmount = plan.dataAmount,
        networkType = plan.networkType,
    )

    private fun groupByBrand(plans: List<Plan>): Map<String, Int> =
        plans.groupBy { it.carrierName }.mapValues { (_, v) -> v.size }

    private fun groupByNetwork(plans: List<Plan>): Map<String, Int> =
        plans.groupBy { it.mvnoNetwork }.mapValues { (_, v) -> v.size }
}

data class CrawlResult(
    val sourceName: String,
    val found: Int,
    val created: Int,
    val updated: Int,
)

data class FailedSourceInfo(
    val sourceId: String,
    val sourceName: String,
    val error: String,
)

object NotificationType {
    const val CRAWL_COMPLETE = "CRAWL_COMPLETE"
    const val CRAWL_FAILED = "CRAWL_FAILED"
    const val NEW_PLANS_FOUND = "NEW_PLANS_FOUND"
    const val NEW_PLAN_DETAIL = "NEW_PLAN_DETAIL"
    const val CRAWL_SUMMARY = "CRAWL_SUMMARY"
    const val CRAWL_FAILED_STREAK = "CRAWL_FAILED_STREAK"
}