package com.borasarang.macjupjup.data.repository

import android.content.Context
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.NotificationLog
import com.borasarang.macjupjup.data.db.entity.NotificationType
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.preferences.PreferencesManager
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepository(private val db: MacDatabase) {

    suspend fun getPaged(
        type: String?,
        isRead: Boolean?,
        page: Int,
        pageSize: Int,
    ): List<NotificationLog> {
        val size = pageSize.coerceIn(1, 100)
        return db.notificationLogDao().listFiltered(
            type = type?.takeIf { it.isNotBlank() },
            isRead = isRead,
            limit = size,
            offset = (page - 1) * size,
        )
    }

    suspend fun count(type: String?, isRead: Boolean?): Int =
        db.notificationLogDao().countFiltered(
            type = type?.takeIf { it.isNotBlank() },
            isRead = isRead,
        )

    suspend fun getById(id: Long): NotificationLog? = db.notificationLogDao().getById(id)

    suspend fun countUnread(): Int = db.notificationLogDao().unreadCount()

    suspend fun markAsRead(id: Long): Int = db.notificationLogDao().markRead(id)

    suspend fun markAllAsRead(): Int = db.notificationLogDao().markAllRead()

    suspend fun delete(id: Long): Int = db.notificationLogDao().delete(id)

    suspend fun deleteOlderThan(cutoff: Long): Int = db.notificationLogDao().deleteOlderThan(cutoff)
}

class NotificationService(
    private val appContext: Context,
    private val db: MacDatabase,
    private val preferences: PreferencesManager,
) {
    private val repo = NotificationRepository(db)

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

    private fun detailToJson(detail: NotificationDetail): String {
        return Json.encodeToString(
            buildJsonObject {
                put("type", detail.type)
                put("summary", detail.summary)
                put("totalFound", detail.totalFound)
                put("newApps", detail.newApps)
                put("updatedApps", detail.updatedApps)
                put("failedCount", detail.failedCount)
                put("bySource", JsonArray(detail.bySource.map {
                    buildJsonObject {
                        put("sourceName", it.sourceName)
                        put("count", it.count)
                    }
                }))
                put("byCategory", JsonArray(detail.byCategory.map {
                    buildJsonObject {
                        put("category", it.category)
                        put("count", it.count)
                    }
                }))
                put("byLicense", JsonArray(detail.byLicense.map {
                    buildJsonObject {
                        put("license", it.license)
                        put("count", it.count)
                    }
                }))
                put("newAppsDetail", JsonArray(detail.newAppsDetail.map {
                    buildJsonObject {
                        put("id", it.id)
                        put("name", it.name)
                        put("developer", it.developer)
                        put("license", it.license)
                        it.version?.let { v -> put("version", v) }
                    }
                }))
                put("newNewsDetail", JsonArray(detail.newNewsDetail.map {
                    buildJsonObject {
                        put("id", it.id)
                        put("title", it.title)
                        put("sourceName", it.sourceName)
                        put("main", it.main)
                    }
                }))
                put("failedSources", JsonArray(detail.failedSources.map {
                    buildJsonObject {
                        put("sourceId", it.sourceId)
                        put("sourceName", it.sourceName)
                        put("error", it.error)
                    }
                }))
                put("startedAt", detail.startedAt)
                put("finishedAt", detail.finishedAt)
            },
        )
    }

    private fun toSummary(a: App) = NewAppSummary(
        id = a.id, name = a.name, developer = a.developer,
        license = a.license, version = a.version,
    )

    private fun baseDetail(
        type: String,
        summary: String,
        apps: List<App>,
        startedAt: Long = 0,
    ) = NotificationDetail(
        type = type,
        summary = summary,
        totalFound = apps.size,
        newApps = apps.size,
        updatedApps = 0,
        failedCount = 0,
        bySource = emptyList(),
        byCategory = apps.groupingBy { it.category }.eachCount()
            .map { CategoryCount(it.key, it.value) },
        byLicense = apps.groupingBy { it.license }.eachCount()
            .map { LicenseCount(it.key, it.value) },
        newAppsDetail = apps.take(50).map { toSummary(it) },
        failedSources = emptyList(),
        startedAt = startedAt,
        finishedAt = System.currentTimeMillis(),
    )

    /** 수집 완료 알림 */
    suspend fun createCrawlCompleteNotification(
        result: CrawlResult,
        newApps: List<App>,
    ) {
        if (!settings().notifCrawlComplete) return
        // R7: 본문 리소스화 (DB 저장 문구와 동일 출력)
        val summary = appContext.getString(
            R.string.mac_notif_crawl_complete,
            result.sourceName, result.found, result.created, result.updated,
        )
        val detail = baseDetail(
            type = NotificationType.CRAWL_COMPLETE,
            summary = summary,
            apps = newApps,
            startedAt = result.startedAt,
        ).copy(
            totalFound = result.found,
            newApps = result.created,
            updatedApps = result.updated,
            bySource = listOf(SourceCount(result.sourceName, result.found)),
        )
        repo.let {
            db.notificationLogDao().insert(
                NotificationLog(
                    type = NotificationType.CRAWL_COMPLETE,
                    summary = summary,
                    detailJson = detailToJson(detail),
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
        DebugLogger.i("알림", "수집 완료 알림 저장 source=${result.sourceName}")
    }

    /** 신규 앱 발견 알림 */
    suspend fun createNewAppsNotification(newApps: List<App>) {
        if (newApps.isEmpty()) return
        if (!settings().notifNewApp) return
        val summary = appContext.resources.getQuantityString(
            R.plurals.mac_notif_new_apps, newApps.size, newApps.size,
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.NEW_APPS_FOUND,
                summary = summary,
                detailJson = detailToJson(
                    baseDetail(NotificationType.NEW_APPS_FOUND, summary, newApps)
                ),
                createdAt = System.currentTimeMillis(),
            )
        )
        DebugLogger.i("알림", "신규 앱 알림 저장 ${newApps.size}건")
    }

    /** 신규 뉴스 발견 알림 (R39: 뉴스 수집 경로에 알림이 전혀 없어 추가) */
    suspend fun createNewNewsNotification(news: List<com.borasarang.macjupjup.data.db.entity.NewsArticle>) {
        if (news.isEmpty()) return
        if (!settings().notifNews) return
        val summary = appContext.resources.getQuantityString(
            R.plurals.mac_notif_new_news, news.size, news.size,
        )
        val detail = baseDetail(NotificationType.NEWS_FOUND, summary, emptyList()).copy(
            totalFound = news.size,
            newApps = news.size,
            bySource = news.groupingBy { it.sourceName }.eachCount()
                .map { SourceCount(it.key, it.value) },
            newNewsDetail = news.take(50).map {
                NewsNewsSummary(id = it.id, title = it.title, sourceName = it.sourceName, main = it.main)
            },
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.NEWS_FOUND,
                summary = summary,
                detailJson = detailToJson(detail),
                createdAt = System.currentTimeMillis(),
            )
        )
        DebugLogger.i("알림", "신규 뉴스 알림 저장 ${news.size}건")
    }

    /** 버전 업데이트 알림 */
    suspend fun createVersionBumpNotification(updated: List<App>) {
        if (updated.isEmpty()) return
        if (!settings().notifNewApp) return
        val summary = appContext.resources.getQuantityString(
            R.plurals.mac_notif_version_bump, updated.size, updated.size,
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.VERSION_BUMPED,
                summary = summary,
                detailJson = detailToJson(
                    baseDetail(NotificationType.VERSION_BUMPED, summary, updated)
                ),
                createdAt = System.currentTimeMillis(),
            )
        )
        DebugLogger.i("알림", "버전 업데이트 알림 저장 ${updated.size}건")
    }

    /** 수집 실패 알림 (연속 실패 등) */
    suspend fun createFailureNotification(sourceName: String, error: String, streak: Int) {
        if (!settings().notifFailure) return
        val summary = appContext.getString(
            R.string.mac_notif_failure, sourceName, streak, error,
        )
        val detail = baseDetail(
            type = NotificationType.CRAWL_FAILED_STREAK,
            summary = summary,
            apps = emptyList(),
        ).copy(
            totalFound = 0, newApps = 0,
            failedCount = 1,
            failedSources = listOf(FailedSource("unknown", sourceName, error)),
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.CRAWL_FAILED_STREAK,
                summary = summary,
                detailJson = detailToJson(detail),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** 일일 요약 알림 */
    suspend fun createSummaryNotification(summary: String, detail: NotificationDetail) {
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.CRAWL_SUMMARY,
                summary = summary,
                detailJson = detailToJson(detail),
                createdAt = System.currentTimeMillis(),
            )
        )
    }
}

data class CrawlResult(
    val sourceName: String,
    val found: Int,
    val created: Int,
    val updated: Int,
    val startedAt: Long = 0,
)
