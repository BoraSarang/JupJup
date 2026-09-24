package com.borasarang.macjupjup.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * 수집 스케줄 — C1 단일 파이프라인.
 * 주기 수집은 15분 1회 PipelineWorker, 수동 즉시수집은 CrawlWorker(one-time).
 * 구 소스별 PeriodicWork는 scheduleAll 시 일괄 폐기.
 */
class CrawlScheduler(private val context: Context) {

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * 단일 파이프라인 예약 (앱 시작·수집 재개 시 1회).
     * 구 소스별 PeriodicWork(crawl_{id})를 전부 취소하고 파이프라인만 유지.
     */
    suspend fun scheduleAll() {
        val wm = WorkManager.getInstance(context)
        // 레거시 소스별 주기 워커 일괄 폐기 (C1 마이그레이션)
        val allSources = MacJupJupRuntime.database.crawlSourceDao().getAll()
        for (s in allSources) {
            wm.cancelUniqueWork("crawl_${s.id}")
        }
        wm.cancelAllWorkByTag(TAG_CRAWL)

        val offsetMin = 0L
        val request = PeriodicWorkRequestBuilder<PipelineWorker>(
            PipelineLogic.PIPELINE_INTERVAL_MINUTES.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints())
            .setInitialDelay(offsetMin, TimeUnit.MINUTES)
            .addTag(TAG_CRAWL)
            .addTag(PipelineWorker.TAG)
            .build()
        wm.enqueueUniquePeriodicWork(
            PipelineWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
        DebugLogger.i(
            "스케줄",
            "파이프라인 예약 ${PipelineLogic.PIPELINE_INTERVAL_MINUTES}분 주기 (레거시 ${allSources.size}건 폐기)",
        )
    }

    /** 소스 단위 재예약 — C1에서는 파이프라인이 due를 판정하므로 no-op (레거시 호환) */
    fun scheduleSource(source: com.borasarang.macjupjup.data.db.entity.CrawlSource) {
        WorkManager.getInstance(context).cancelUniqueWork("crawl_${source.id}")
        DebugLogger.i("스케줄", "C1: 소스별 주기 폐기 source=${source.name} (파이프라인 due 판정)")
    }

    /** 단일 소스 예약 취소 (토글 off·소스 제거 시) */
    fun cancelSource(sourceId: String) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("crawl_$sourceId")
        wm.cancelUniqueWork("crawl_once_$sourceId")
        DebugLogger.i("스케줄", "예약 취소 source=$sourceId")
    }

    /** 즉시 수집: sourceId null이면 전체 활성 소스 */
    suspend fun triggerImmediate(sourceId: String?) {
        val wm = WorkManager.getInstance(context)
        val ids = if (sourceId.isNullOrBlank()) {
            MacJupJupRuntime.database.crawlSourceDao().getEnabled().map { it.id }
        } else {
            listOf(sourceId)
        }
        ids.forEachIndexed { index, id ->
            val req = OneTimeWorkRequestBuilder<CrawlWorker>()
                .setConstraints(constraints())
                .setInputData(workDataOf(CrawlWorker.KEY_SOURCE_ID to id))
                .setInitialDelay((index * 20).toLong(), TimeUnit.SECONDS)
                .addTag(TAG_CRAWL)
                .build()
            wm.cancelUniqueWork("crawl_once_$id")
            wm.enqueueUniqueWork(
                "crawl_once_$id",
                ExistingWorkPolicy.REPLACE,
                req,
            )
        }
        DebugLogger.i("수동수집", "즉시 수집 예약 ${ids.size}건 (replace)")
    }

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        wm.cancelAllWorkByTag(TAG_CRAWL)
        wm.cancelAllWorkByTag(PipelineWorker.TAG)
        wm.cancelUniqueWork(PipelineWorker.UNIQUE_NAME)
    }

    /** 오전 9시 일일 요약 예약 (24h 주기) */
    fun scheduleDailySummary() {
        val delayMs = com.borasarang.macjupjup.util.TimeUtils.millisUntilNextHour(9)
        val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG_SUMMARY)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_summary_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
        DebugLogger.i("스케줄", "일일 요약 예약 24h 주기 (${delayMs / 3600000}시간 후 첫 실행)")
    }

    /** 번역 워커 6시간 주기 예약 */
    fun scheduleTranslate() {
        val req = PeriodicWorkRequestBuilder<TranslateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints())
            .addTag(TAG_TRANSLATE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "translate_ko",
            ExistingPeriodicWorkPolicy.REPLACE,
            req,
        )
        DebugLogger.i("스케줄", "번역 워커 예약 6시간마다")
    }

    /** 번역 즉시 실행 (포털·설정에서 수동) */
    fun triggerTranslateNow() {
        val req = OneTimeWorkRequestBuilder<TranslateWorker>()
            .setConstraints(constraints())
            .addTag(TAG_TRANSLATE)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "translate_once",
            ExistingWorkPolicy.REPLACE,
            req,
        )
        DebugLogger.i("번역", "번역 즉시 실행 예약")
    }

    companion object {
        private const val TAG_CRAWL = "macjupjup_crawl"
        private const val TAG_SUMMARY = "macjupjup_summary"
        private const val TAG_TRANSLATE = "macjupjup_translate"
    }
}
