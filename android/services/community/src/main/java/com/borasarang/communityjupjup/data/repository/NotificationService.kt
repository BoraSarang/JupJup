package com.borasarang.communityjupjup.data.repository

import android.content.Context
import com.borasarang.communityjupjup.R
import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.data.db.entity.NotificationLog
import com.borasarang.communityjupjup.data.db.entity.NotificationType
import com.borasarang.communityjupjup.data.preferences.PreferencesManager
import com.borasarang.communityjupjup.util.category.CommunityCategories
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.communityjupjup.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NotificationRepository(private val db: CommunityDatabase) {

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
    private val db: CommunityDatabase,
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
                put("newPosts", detail.newPosts)
                put("updatedPosts", detail.updatedPosts)
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
                put("newPostsDetail", JsonArray(detail.newPostsDetail.map {
                    buildJsonObject {
                        put("id", it.id)
                        put("title", it.title)
                        it.author?.let { a -> put("author", a) }
                        put("category", it.category)
                        put("sourceName", it.sourceName)
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

    private fun toSummary(p: CommunityPost, sourceName: String) = NewPostSummary(
        id = p.id,
        title = p.title,
        author = p.authorName,
        category = CommunityCategories.nameOf(p.categoryId),
        sourceName = sourceName,
    )

    private fun baseDetail(
        type: String,
        summary: String,
        posts: List<CommunityPost>,
        sourceName: String = "",
        startedAt: Long = 0,
    ) = NotificationDetail(
        type = type,
        summary = summary,
        totalFound = posts.size,
        newPosts = posts.size,
        updatedPosts = 0,
        failedCount = 0,
        bySource = emptyList(),
        byCategory = posts.groupingBy { CommunityCategories.nameOf(it.categoryId) }.eachCount()
            .map { CategoryCount(it.key, it.value) },
        newPostsDetail = posts.take(50).map { toSummary(it, sourceName) },
        failedSources = emptyList(),
        startedAt = startedAt,
        finishedAt = System.currentTimeMillis(),
    )

    /** 수집 완료 알림 */
    suspend fun createCrawlCompleteNotification(
        result: CrawlResult,
        newPosts: List<CommunityPost>,
    ) {
        if (!settings().notifCrawlComplete) return
        // R7: 본문 리소스화 (DB 저장 문구와 동일 출력)
        val summary = appContext.getString(
            R.string.cm_notif_crawl_complete,
            result.sourceName, result.found, result.created, result.updated,
        )
        val detail = baseDetail(
            type = NotificationType.CRAWL_COMPLETE,
            summary = summary,
            posts = newPosts,
            sourceName = result.sourceName,
            startedAt = result.startedAt,
        ).copy(
            totalFound = result.found,
            newPosts = result.created,
            updatedPosts = result.updated,
            bySource = listOf(SourceCount(result.sourceName, result.found)),
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.CRAWL_COMPLETE,
                summary = summary,
                detailJson = detailToJson(detail),
                createdAt = System.currentTimeMillis(),
            )
        )
        DebugLogger.i("알림", "수집 완료 알림 저장 source=${result.sourceName}")
    }

    /** 신규 게시글 발견 알림 */
    suspend fun createNewPostsNotification(newPosts: List<CommunityPost>, sourceName: String) {
        if (newPosts.isEmpty()) return
        if (!settings().notifNewPost) return
        val summary = appContext.resources.getQuantityString(
            R.plurals.cm_notif_new_posts, newPosts.size, newPosts.size,
        )
        db.notificationLogDao().insert(
            NotificationLog(
                type = NotificationType.NEW_POSTS_FOUND,
                summary = summary,
                detailJson = detailToJson(
                    baseDetail(NotificationType.NEW_POSTS_FOUND, summary, newPosts, sourceName)
                ),
                createdAt = System.currentTimeMillis(),
            )
        )
        DebugLogger.i("알림", "신규 게시글 알림 저장 ${newPosts.size}건")
    }

    /** 수집 실패 알림 (연속 실패 등) */
    suspend fun createFailureNotification(sourceName: String, error: String, streak: Int) {
        if (!settings().notifFailure) return
        val summary = appContext.getString(
            R.string.cm_notif_failure, sourceName, streak, error,
        )
        val detail = baseDetail(
            type = NotificationType.CRAWL_FAILED_STREAK,
            summary = summary,
            posts = emptyList(),
        ).copy(
            totalFound = 0, newPosts = 0,
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
