package com.borasarang.planjupjup.worker

import androidx.work.ListenableWorker
import com.borasarang.common.util.net.NetMeter
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.data.repository.CrawlResult
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.crawler.CrawlerFactory

/**
 * 소스 1건 수집 실행부 — CrawlWorker(즉시)·PipelineWorker(주기) 공용.
 * 락·슬롯·예산은 호출부가 관리한다.
 */
class CrawlRunner(
    private val app: PlanJupJupRuntime,
    private val onStart: suspend (sourceName: String) -> Unit = {},
    private val attempt: () -> Int = { 1 },
    private val maxAttempts: Int = 3,
) {

    suspend fun execute(source: CrawlSource): ListenableWorker.Result {
        val sourceId = source.id
        onStart(source.name)
        app.sourceRepository.markRunning(sourceId)
        val startedAt = System.currentTimeMillis()
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
                        "수집 완료 source=${source.name} found=${drafts.size} " +
                            "new=${saved.created} updated=${saved.updated}",
                    )
                    val newPlans = if (saved.createdIds.isEmpty()) {
                        emptyList()
                    } else {
                        app.database.planDao().getByIds(saved.createdIds)
                    }
                    if (newPlans.isNotEmpty()) {
                        app.notificationService.createNewPlansNotification(newPlans)
                    }
                    app.notificationService.createCrawlCompleteNotification(
                        sourceResults = mapOf(
                            source.name to CrawlResult(
                                sourceName = source.name,
                                found = drafts.size,
                                created = saved.created,
                                updated = saved.updated,
                            ),
                        ),
                        failedSources = emptyList(),
                        newPlans = newPlans,
                    )
                    ListenableWorker.Result.success()
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    fail(source, startedAt, e.message ?: e.javaClass.simpleName, netBefore)
                    retryOrFail()
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(source, startedAt, e.message ?: e.javaClass.simpleName, netBefore)
            retryOrFail()
        }
    }

    private fun retryOrFail(): ListenableWorker.Result {
        return if (attempt() >= maxAttempts) {
            ListenableWorker.Result.failure()
        } else {
            ListenableWorker.Result.retry()
        }
    }

    private suspend fun fail(
        source: CrawlSource,
        startedAt: Long,
        message: String,
        netBefore: com.borasarang.common.util.net.NetUsage,
    ) {
        val net = NetMeter.deltaSince("plan", netBefore)
        app.sourceRepository.logResult(
            sourceId = source.id,
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
        DebugLogger.e("수집", "E-AND-CRAWL-0211", "수집 실패 source=${source.name}: $message")
        checkFailureStreak(source.id, source.name, message)
    }

    private suspend fun checkFailureStreak(sourceId: String, sourceName: String, error: String) {
        try {
            val statuses = app.sourceRepository.getRecentStatuses(sourceId, 5)
            if (!com.borasarang.common.util.CrawlStats.isFailureStreak(statuses)) return
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속 5회 수집 실패 source=$sourceName")
            app.notificationService.createFailureNotification(sourceName, error, 5)
            notifyFailure(sourceName)
        } catch (e: Exception) {
            DebugLogger.e("수집", "E-AND-CRAWL-0204", "연속실패 기록 실패 source=$sourceName: ${e.message}", e)
        }
    }

    private fun notifyFailure(sourceName: String) {
        try {
            val nm = app.context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_FAIL_ID,
                    app.context.getString(com.borasarang.planjupjup.R.string.plan_notif_channel_crawl_fail),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val notification = androidx.core.app.NotificationCompat.Builder(app.context, CHANNEL_FAIL_ID)
                .setContentTitle(app.context.getString(com.borasarang.planjupjup.R.string.plan_notif_crawl_fail_title))
                .setContentText("$sourceName 수집이 5회 연속 실패했습니다")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
            nm.notify(
                Constants.NOTIFICATION_ID_CRAWL_BASE + 900 + sourceName.hashCode() % 100,
                notification,
            )
        } catch (e: Exception) {
            DebugLogger.w("수집", "실패 푸시 표시 실패 source=$sourceName: ${e.message}")
        }
    }

    companion object {
        private const val CHANNEL_FAIL_ID = "planjupjup_crawl_fail"
    }
}
