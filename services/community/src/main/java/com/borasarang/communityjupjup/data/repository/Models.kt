package com.borasarang.communityjupjup.data.repository

import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.data.db.entity.CrawlLog
import com.borasarang.communityjupjup.data.db.entity.CrawlSource
import com.borasarang.communityjupjup.data.db.entity.NotificationLog
import com.borasarang.communityjupjup.data.db.entity.SiteBoard

/** 게시글 + 출처/보드 묶음 (상세 API 응답용) */
data class PostWithSourceList(
    val post: CommunityPost,
    val source: CrawlSource?,
    val board: SiteBoard?,
)

/** 홈 통계 */
data class CommunityStats(
    val totalPosts: Int,
    val activeSources: Int,
    val lastCollectedAt: Long?,
)

/** 목록 조회 필터 */
data class PostFilter(
    /** 통합 카테고리 1~10, null이면 전체 */
    val categoryId: Int? = null,
    val sourceId: String? = null,
    /** 복수 언론사 (비어 있으면 sourceId 단일 조건 사용) */
    val sourceIds: Set<String> = emptySet(),
    val q: String? = null,
    /** latest / popular */
    val sort: String = "latest",
    val page: Int = 1,
    val pageSize: Int = 50,
)

data class PagedPosts(
    val posts: List<PostListItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

/** 목록용 게시글 + 출처/카테고리 표시명 */
data class PostListItem(
    val post: CommunityPost,
    val sourceName: String?,
    val boardName: String?,
    val categoryName: String?,
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
    val domain: String,
    val baseUrl: String,
    val enabled: Boolean,
    val intervalHours: Int,
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    val lastStatus: String,
    val errorMessage: String?,
    val postCount: Int = 0,
)

fun CrawlSource.toStatus(postCount: Int = 0) = SourceStatus(
    id = id,
    name = name,
    type = type,
    domain = domain,
    baseUrl = baseUrl,
    enabled = enabled,
    intervalHours = intervalHours,
    intervalMinutes = intervalMinutes,
    lastRunAt = lastRunAt,
    lastStatus = lastStatus,
    errorMessage = errorMessage,
    postCount = postCount,
)

data class SettingsData(
    val port: Int,
    val retentionDays: Int,
    val autoStart: Boolean,
    val watchdogIntervalSec: Int,
    val notifCrawlComplete: Boolean = true,
    val notifNewPost: Boolean = true,
    val notifFailure: Boolean = true,
    val crawlEnabled: Boolean = true,
)

/** 설정 조회 응답 (민감값 없음) */
data class SettingsView(
    val port: Int,
    val retentionDays: Int,
    val autoStart: Boolean,
    val watchdogIntervalSec: Int,
    val notifCrawlComplete: Boolean,
    val notifNewPost: Boolean,
    val notifFailure: Boolean,
)

fun SettingsData.toView() = SettingsView(
    port = port,
    retentionDays = retentionDays,
    autoStart = autoStart,
    watchdogIntervalSec = watchdogIntervalSec,
    notifCrawlComplete = notifCrawlComplete,
    notifNewPost = notifNewPost,
    notifFailure = notifFailure,
)

data class RecentLog(
    val id: Long,
    val sourceName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val postsFound: Int,
    val postsNew: Int,
    val postsUpdated: Int,
    val errorMessage: String?,
)

fun CrawlLog.toRecent() = RecentLog(
    id = id,
    sourceName = sourceName,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status,
    postsFound = plansFound,
    postsNew = plansNew,
    postsUpdated = plansUpdated,
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
    val newPosts: Int,
    val updatedPosts: Int,
    val failedCount: Int,
    val bySource: List<SourceCount>,
    val byCategory: List<CategoryCount>,
    val newPostsDetail: List<NewPostSummary>,
    val failedSources: List<FailedSource>,
    val startedAt: Long,
    val finishedAt: Long,
)

data class SourceCount(val sourceName: String, val count: Int, val sourceId: String = "")
data class CategoryCount(val category: String, val count: Int)

data class NewPostSummary(
    val id: Long,
    val title: String,
    val author: String?,
    val category: String,
    val sourceName: String,
)

data class FailedSource(val sourceId: String, val sourceName: String, val error: String)
