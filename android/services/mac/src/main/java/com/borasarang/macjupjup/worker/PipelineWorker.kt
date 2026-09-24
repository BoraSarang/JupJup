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
 * C1 단일 파이프라인 워커 — 소스별 PeriodicWork 대체.
 * 15분 주기 1회 실행, due 소스를 순차 처리 (전체 예산 5분, 회당 최대 8건).
 * 즉시 수집(triggerImmediate)은 기존 CrawlWorker를 그대로 사용한다.
 */
class PipelineWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = MacJupJupRuntime
        if (!NetUtils.isConnected(applicationContext)) {
            DebugLogger.w("파이프라인", "네트워크 끊김 — 연기 (E-AND-NET-0301)")
            return Result.retry()
        }
        val settings = try {
            app.preferences.getSettings()
        } catch (e: Exception) {
            DebugLogger.e("파이프라인", "E-AND-DB-0404", "설정 조회 실패: ${e.message}", e)
            return Result.retry()
        }
        if (!settings.crawlEnabled) {
            DebugLogger.i("파이프라인", "수집 일시정지 — 스킵")
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val sources = app.database.crawlSourceDao().getEnabled()
        val due = PipelineLogic.selectDue(sources, now)
        if (due.isEmpty()) {
            DebugLogger.i("파이프라인", "due 소스 없음 (활성 ${sources.size})")
            return Result.success()
        }

        DebugLogger.i("파이프라인", "수집 파이프라인 시작 due=${due.size} (활성 ${sources.size})")
        setForeground(createForegroundInfo("수집 준비 중…"))

        val deadline = now + PipelineLogic.PIPELINE_MAX_RUNTIME_MS
        var processed = 0
        var succeeded = 0
        var failed = 0
        var budgetExceeded = false

        for ((index, source) in due.withIndex()) {
            if (isStopped) {
                DebugLogger.w("파이프라인", "워커 중단 — 잔여 ${due.size - processed}건 다음 회차")
                break
            }
            if (System.currentTimeMillis() >= deadline) {
                budgetExceeded = true
                DebugLogger.w("파이프라인", "전체 예산 초과 — 잔여 ${due.size - processed}건 다음 회차")
                break
            }

            if (!SourceLocks.tryAcquire(source.id)) {
                DebugLogger.w("파이프라인", "스킵(이미 실행 중) source=${source.name}")
                continue
            }
            try {
                // 파이프라인은 순차 처리 — 슬롯 점유로 즉시수집과 상한 공유
                SourceLocks.acquireCrawlSlot()
                try {
                    val label = "${source.name} (${index + 1}/${due.size})"
                    setForeground(createForegroundInfo(label))
                    val runner = CrawlRunner(
                        app = app,
                        onStart = { name ->
                            DebugLogger.i("파이프라인", "수집 시작 source=$name")
                        },
                        attempt = { 1 },
                        maxAttempts = 1,
                    )
                    val result = withTimeoutOrNull(Constants.CRAWL_RUNTIME_BUDGET_MS) {
                        runner.execute(source)
                    }
                    when {
                        result == null -> {
                            DebugLogger.w(
                                "파이프라인",
                                "런타임 예산 초과(${Constants.CRAWL_RUNTIME_BUDGET_MS}ms) — clearRunning source=${source.name}",
                            )
                            withContext(NonCancellable) {
                                app.sourceRepository.clearRunning(source.id)
                            }
                            failed++
                        }
                        result == androidx.work.ListenableWorker.Result.success() -> {
                            succeeded++
                        }
                        else -> {
                            failed++
                        }
                    }
                } finally {
                    SourceLocks.releaseCrawlSlot()
                }
            } catch (e: Exception) {
                DebugLogger.w("파이프라인", "소스 종료(${e.javaClass.simpleName}) source=${source.name}")
                if (e is kotlinx.coroutines.CancellationException) {
                    withContext(NonCancellable) {
                        app.sourceRepository.clearRunning(source.id)
                    }
                    throw e
                }
                failed++
            } finally {
                SourceLocks.release(source.id)
            }
            processed++
        }

        DebugLogger.i(
            "파이프라인",
            "수집 파이프라인 완료 processed=$processed ok=$succeeded fail=$failed " +
                "due=${due.size} budgetExceeded=$budgetExceeded",
        )
        return Result.success()
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.mac_notif_crawl_running))
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
            else -> ForegroundInfo(
                Constants.NOTIFICATION_ID_CRAWL_BASE,
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
        const val UNIQUE_NAME = "crawl_pipeline"
        const val TAG = "macjupjup_pipeline"
        private const val CHANNEL_ID = "macjupjup_crawl"
    }
}
