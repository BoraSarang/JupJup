package com.borasarang.planjupjup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.R
import com.borasarang.planjupjup.crawler.CrawlerFactory
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.repository.CrawlResult
import com.borasarang.planjupjup.data.repository.NotificationService
import com.borasarang.planjupjup.data.repository.NotificationType
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.common.util.NetMeter
import com.borasarang.common.util.NetUtils
import com.borasarang.common.worker.SourceLocks

/**
 * 소스 1건 수집 워커. 장시간 실행 대비 setForegroundAsync 사용.
 * 성공/실패 모두 crawl_logs 기록 + 소스 상태 갱신. 실패는 Result.retry (지수 백오프).
 */
class CrawlWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID)
        if (sourceId.isNullOrBlank()) {
            return Result.failure()
        }
        val app = PlanJupJupRuntime
        val source = app.sourceRepository.getById(sourceId)
        if (source == null) {
            DebugLogger.e("수집", "E-AND-CRAWL-0211", "소스 없음 id=$sourceId")
            return Result.failure()
        }
        if (!source.enabled) {
            return Result.success()
        }
        if (!NetUtils.isConnected(applicationContext)) {
            DebugLogger.w("수집", "네트워크 끊김 — 연기 source=${source.name} (E-AND-NET-0301)")
            return Result.retry()
        }

        // R1: 동일 소스 중복 실행 방지 (주기+즉시 겹침 시 스킵)
        if (!SourceLocks.tryAcquire(sourceId)) {
            DebugLogger.w("수집", "워커 스킵(이미 실행 중) source=${source.name}")
            return Result.success()
        }
        DebugLogger.i("수집", "워커 시작 source=${source.name}")
        val startedAt = System.currentTimeMillis()
        try {
            return runCrawl(app, sourceId, source, startedAt)
        } finally {
            SourceLocks.release(sourceId)
        }
    }

    private suspend fun runCrawl(
        app: PlanJupJupRuntime,
        sourceId: String,
        source: com.borasarang.planjupjup.data.db.entity.CrawlSource,
        startedAt: Long,
    ): Result {
        app.sourceRepository.markRunning(sourceId)
        setForeground(createForegroundInfo(source.name))
        val netBefore = NetMeter.snapshotFor("plan")

        return try {
            val crawler = CrawlerFactory(app.database).create(source)
            val outcome = crawler.crawl()
            outcome.fold(
                onSuccess = { drafts ->
                    val plans = drafts.map { it.plan }
                    val mappings = drafts.flatMap { it.mappings }
                    val saved = app.planRepository.saveCrawlResults(plans, mappings)
                    val net = NetMeter.deltaSince("plan", netBefore)
                    app.sourceRepository.logResult(
                        sourceId = sourceId,
                        sourceName = source.name,
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
                        "워커 완료 source=${source.name} found=${drafts.size} " +
                            "new=${saved.created} updated=${saved.updated}",
                    )
                    // 알림 생성 — 신규 요금제는 실제 생성된 id 기준 조회
                    val newPlans = if (saved.createdIds.isEmpty()) {
                        emptyList()
                    } else {
                        app.database.planDao().getByIds(saved.createdIds)
                    }
                    if (newPlans.isNotEmpty()) {
                        app.notificationService.createNewPlansNotification(newPlans)
                    }
                    app.notificationService.createCrawlCompleteNotification(
                        sourceResults = mapOf(source.name to CrawlResult(
                            sourceName = source.name,
                            found = drafts.size,
                            created = saved.created,
                            updated = saved.updated,
                        )),
                        failedSources = emptyList(),
                        newPlans = newPlans,
                    )
                    Result.success()
                },
                onFailure = { e ->
                    val message = e.message ?: e.javaClass.simpleName
                    val net = NetMeter.deltaSince("plan", netBefore)
                    app.sourceRepository.logResult(
                        sourceId = sourceId,
                        sourceName = source.name,
                        startedAt = startedAt,
                        status = Constants.STATUS_FAILED,
                        found = 0,
                        created = 0,
                        updated = 0,
                        error = message,
                        rxBytes = net.rxBytes,
                        txBytes = net.txBytes,
                    )
                    DebugLogger.e("수집", "E-AND-CRAWL-0211", "워커 실패 source=${source.name}: $message", e)
                    checkFailureStreak(app, sourceId, source.name, message)
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            val net = NetMeter.deltaSince("plan", netBefore)
            app.sourceRepository.logResult(
                sourceId = sourceId,
                sourceName = source.name,
                startedAt = startedAt,
                status = Constants.STATUS_FAILED,
                found = 0,
                created = 0,
                updated = 0,
                error = e.message,
                rxBytes = net.rxBytes,
                txBytes = net.txBytes,
            )
            DebugLogger.e("수집", "E-AND-CRAWL-0211", "워커 예외 source=${source.name}: ${e.message}", e)
            checkFailureStreak(app, sourceId, source.name, e.message ?: e.javaClass.simpleName)
            Result.retry()
        }
    }

    /** 5연속 실패 시 DB 알림 저장 + 사용자 푸시 (E-AND-CRAWL-0204) */
    private suspend fun checkFailureStreak(
        app: PlanJupJupRuntime,
        sourceId: String,
        sourceName: String,
        error: String,
    ) {
        try {
            val statuses = app.sourceRepository.getRecentStatuses(sourceId, 5)
            if (!com.borasarang.common.util.CrawlStats.isFailureStreak(statuses)) return
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속 5회 수집 실패 source=$sourceName")
            app.notificationService.createFailureNotification(sourceName, error, 5)
            notifyFailure(sourceName)
        } catch (e: Exception) {
            // R6: 실패 기록 자체가 삼켜지면 원인 추적 불가
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속실패 기록 실패 source=$sourceName: ${e.message}", e)
        }
    }

    private fun notifyFailure(sourceName: String) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FAIL_ID,
                    // R6: 채널명 리소스화
                    applicationContext.getString(R.string.plan_notif_channel_crawl_fail),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_FAIL_ID)
                // R6: 푸시 제목 리소스화 (본문은 변수 포함이라 유지)
                .setContentTitle(applicationContext.getString(R.string.plan_notif_crawl_fail_title))
                .setContentText("$sourceName 수집이 5회 연속 실패했습니다")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
            nm.notify(
                Constants.NOTIFICATION_ID_CRAWL_BASE + 900 + sourceName.hashCode() % 100,
                notification,
            )
        } catch (e: Exception) {
            // R6: 푸시 표시 실패 기록 (DB 알림은 위에서 저장됨)
            DebugLogger.w("수집", "실패 푸시 표시 실패 source=$sourceName: ${e.message}")
        }
    }

    private fun createForegroundInfo(sourceName: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.plan_notif_crawl_running))
            .setContentText(sourceName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
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
                    // R6: 채널명 리소스화
                    applicationContext.getString(R.string.plan_notif_channel_crawl),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        private const val CHANNEL_ID = "planjupjup_crawl"
        private const val CHANNEL_FAIL_ID = "planjupjup_crawl_fail"
    }
}
