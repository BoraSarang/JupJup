package com.borasarang.macjupjup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.borasarang.common.util.net.NetUtils
import com.borasarang.common.worker.SourceLocks
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.R
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 소스 1건 수집 워커 (수동 즉시수집 전용 — 주기 수집은 PipelineWorker).
 * 성공/실패 모두 crawl_logs 기록 + 소스 상태 갱신. 실패는 Result.retry.
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
        val app = MacJupJupRuntime
        val source = app.sourceRepository.getById(sourceId)
        if (source == null) {
            DebugLogger.e("수집", "E-AND-CRAWL-0201", "소스 없음 id=$sourceId")
            return Result.failure()
        }
        if (!source.enabled) {
            return Result.success()
        }
        if (!NetUtils.isConnected(applicationContext)) {
            DebugLogger.w("수집", "네트워크 끊김 — 연기 source=${source.name} (E-AND-NET-0301)")
            return Result.retry()
        }

        if (!SourceLocks.tryAcquire(sourceId)) {
            DebugLogger.w("수집", "워커 스킵(이미 실행 중) source=${source.name}")
            return Result.success()
        }
        DebugLogger.i("수집", "워커 시작 source=${source.name}")
        try {
            SourceLocks.acquireCrawlSlot()
            try {
                val runner = CrawlRunner(
                    app = app,
                    onStart = { name -> setForeground(createForegroundInfo(name)) },
                    attempt = { runAttemptCount },
                    maxAttempts = MAX_RUN_ATTEMPTS,
                )
                val result = withTimeoutOrNull(Constants.CRAWL_RUNTIME_BUDGET_MS) {
                    runner.execute(source)
                }
                if (result == null) {
                    DebugLogger.w(
                        "수집",
                        "런타임 예산 초과(${Constants.CRAWL_RUNTIME_BUDGET_MS}ms) — 부분 진행 유지, retry source=${source.name}",
                    )
                    withContext(NonCancellable) {
                        app.sourceRepository.clearRunning(sourceId)
                    }
                    return if (runAttemptCount >= MAX_RUN_ATTEMPTS) Result.failure() else Result.retry()
                }
                return result
            } finally {
                SourceLocks.releaseCrawlSlot()
            }
        } catch (e: Exception) {
            DebugLogger.w("수집", "워커 종료(${e.javaClass.simpleName}) source=${source.name}")
            if (e is kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) {
                    app.sourceRepository.clearRunning(sourceId)
                }
            }
            throw e
        } finally {
            SourceLocks.release(sourceId)
        }
    }

    private fun createForegroundInfo(sourceName: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.mac_notif_crawl_running))
            .setContentText(sourceName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE + sourceName.hashCode() % 100,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            else -> ForegroundInfo(
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
                    applicationContext.getString(R.string.mac_notif_channel_crawl),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        private const val MAX_RUN_ATTEMPTS = 3
        private const val CHANNEL_ID = "macjupjup_crawl"
    }
}
