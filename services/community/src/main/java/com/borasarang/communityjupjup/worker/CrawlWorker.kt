package com.borasarang.communityjupjup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.communityjupjup.R
import com.borasarang.communityjupjup.crawler.CrawlerFactory
import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.data.db.entity.SiteBoard
import com.borasarang.communityjupjup.util.Constants
import com.borasarang.common.util.CrawlStats
import com.borasarang.common.util.NetMeter
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.util.takeSafe
import com.borasarang.common.util.NetUtils
import com.borasarang.common.worker.SourceLocks

/**
 * 보드 1건 수집 워커 (R27: 소스 단위 → 보드 단위).
 * 성공/실패 모두 crawl_logs 기록 + 소스 상태 갱신. 실패는 Result.retry.
 */
class CrawlWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val boardId = inputData.getLong(KEY_BOARD_ID, -1L)
        if (boardId < 0) {
            return Result.failure()
        }
        val app = CommunityJupJupRuntime
        val board = app.database.siteBoardDao().getById(boardId)
        if (board == null) {
            DebugLogger.e("수집", "E-AND-CRAWL-0201", "보드 없음 id=$boardId")
            return Result.failure()
        }
        val source = app.sourceRepository.getById(board.sourceId)
        if (source == null) {
            DebugLogger.e("수집", "E-AND-CRAWL-0201", "소스 없음 id=${board.sourceId}")
            return Result.failure()
        }
        if (!board.enabled || !source.enabled) {
            return Result.success()
        }
        if (!NetUtils.isConnected(applicationContext)) {
            DebugLogger.w("수집", "네트워크 끊김 — 연기 board=${board.boardName} (E-AND-NET-0301)")
            return Result.retry()
        }

        // P0-2: 동일 보드 중복 실행 방지 (주기+즉시 겹침 시 스킵).
        val lockKey = "board:$boardId"
        if (!SourceLocks.tryAcquire(lockKey)) {
            DebugLogger.w("수집", "워커 스킵(이미 실행 중) board=${board.boardName}")
            return Result.success()
        }
        DebugLogger.i("수집", "워커 시작 board=${board.boardName}")
        try {
            return runCrawl(app, board, source)
        } catch (e: Exception) {
            DebugLogger.w("수집", "워커 종료(${e.javaClass.simpleName}) board=${board.boardName}")
            throw e
        } finally {
            SourceLocks.release(lockKey)
        }
    }

    private suspend fun runCrawl(
        app: CommunityJupJupRuntime,
        board: SiteBoard,
        source: com.borasarang.communityjupjup.data.db.entity.CrawlSource,
    ): Result {
        val displayName = "${source.name} › ${board.boardName}"
        app.sourceRepository.markRunning(source.id)
        setForeground(createForegroundInfo(displayName))
        val startedAt = System.currentTimeMillis()
        val netBefore = NetMeter.snapshotFor("community")

        return try {
            val crawler = CrawlerFactory(app.database).create(source)
            val outcome = crawler.crawlSingle(board)
            outcome.fold(
                onSuccess = { drafts ->
                    val now = System.currentTimeMillis()
                    val posts = drafts.map {
                        CommunityPost(
                            sourceId = source.id,
                            boardId = it.boardId,
                            categoryId = it.categoryId,
                            originalPostId = it.originalPostId,
                            title = it.title,
                            summary = it.summary?.takeSafe(Constants.MAX_SUMMARY_LEN),
                            authorName = it.authorName,
                            originalUrl = it.originalUrl,
                            canonicalUrl = it.canonicalUrl,
                            thumbnailUrl = it.thumbnailUrl,
                            imageUrls = com.borasarang.communityjupjup.crawler.encodeImageUrls(it.imageUrls),
                            viewCount = it.viewCount,
                            likeCount = it.likeCount,
                            commentCount = it.commentCount,
                            mallName = it.mallName,
                            salePrice = it.salePrice,
                            originalPrice = it.originalPrice,
                            discountRate = it.discountRate,
                            isSoldOut = it.isSoldOut,
                            dealStatus = it.dealStatus,
                            dealLocation = it.dealLocation,
                            publishedAt = it.publishedAt,
                            collectedAt = now,
                        )
                    }
                    val saved = app.communityRepository.savePosts(posts)
                    val net = NetMeter.deltaSince("community", netBefore)
                    app.sourceRepository.logResult(
                        sourceId = source.id,
                        sourceName = displayName,
                        startedAt = startedAt,
                        status = Constants.STATUS_SUCCESS,
                        found = drafts.size,
                        created = saved.created,
                        updated = saved.updated,
                        error = null,
                        rxBytes = net.rxBytes,
                        txBytes = net.txBytes,
                    )
                    DebugLogger.i(
                        "수집",
                        "워커 완료 board=$displayName found=${drafts.size} " +
                            "new=${saved.created} updated=${saved.updated}",
                    )
                    val newPosts = if (saved.createdIds.isEmpty()) {
                        emptyList()
                    } else {
                        // 단건 N회 → IN 배치 1회 (최대 50건)
                        app.database.postDao().getByIds(saved.createdIds.take(50))
                    }
                    if (newPosts.isNotEmpty()) {
                        app.notificationService.createNewPostsNotification(newPosts, displayName)
                    }
                    app.notificationService.createCrawlCompleteNotification(
                        com.borasarang.communityjupjup.data.repository.CrawlResult(
                            sourceName = displayName,
                            found = drafts.size,
                            created = saved.created,
                            updated = saved.updated,
                            startedAt = startedAt,
                        ),
                        newPosts,
                    )
                    Result.success()
                },
                onFailure = { e ->
                    val net = NetMeter.deltaSince("community", netBefore)
                    fail(app, source.id, displayName, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("community", netBefore)
            fail(app, source.id, displayName, startedAt, e.message ?: e.javaClass.simpleName, net.rxBytes, net.txBytes)
            Result.retry()
        }
    }

    private suspend fun fail(
        app: CommunityJupJupRuntime,
        sourceId: String,
        displayName: String,
        startedAt: Long,
        message: String,
        rxBytes: Long = 0L,
        txBytes: Long = 0L,
    ) {
        app.sourceRepository.logResult(
            sourceId = sourceId,
            sourceName = displayName,
            startedAt = startedAt,
            status = Constants.STATUS_FAILED,
            found = 0,
            created = 0,
            updated = 0,
            error = message,
            rxBytes = rxBytes,
            txBytes = txBytes,
        )
        DebugLogger.e("수집", "E-AND-CRAWL-0201", "워커 실패 board=$displayName: $message")
        checkFailureStreak(app, sourceId, displayName, message)
    }

    /** 5연속 실패 시 DB 알림 저장 (E-AND-CRAWL-0204) */
    private suspend fun checkFailureStreak(
        app: CommunityJupJupRuntime,
        sourceId: String,
        displayName: String,
        error: String,
    ) {
        try {
            val statuses = app.sourceRepository.getRecentStatuses(sourceId, 5)
            if (!CrawlStats.isFailureStreak(statuses)) return
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속 5회 수집 실패 board=$displayName")
            app.notificationService.createFailureNotification(displayName, error, 5)
        } catch (e: Exception) {
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속실패 기록 실패 board=$displayName: ${e.message}", e)
        }
    }

    private fun createForegroundInfo(displayName: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.cm_notif_crawl_running))
            .setContentText(displayName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + displayName.hashCode() % 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + displayName.hashCode() % 100,
                notification,
            )
        }
    }

    private fun ensureChannel() {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.cm_notif_channel_crawl),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_BOARD_ID = "boardId"
        private const val CHANNEL_ID = "communityjupjup_crawl"
    }
}
