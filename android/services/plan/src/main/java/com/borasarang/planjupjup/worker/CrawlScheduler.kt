package com.borasarang.planjupjup.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.TimeUtils
import java.util.concurrent.TimeUnit

/**
 * 수집 스케줄러 — C1 단일 파이프라인.
 * 주기 수집은 15분 1회 PipelineWorker, 수동 즉시수집은 CrawlWorker(one-time).
 * 구 소스별 PeriodicWork는 scheduleAll 시 일괄 폐기.
 */
class CrawlScheduler(private val context: Context) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private fun app(): PlanJupJupRuntime = PlanJupJupRuntime

    private fun crawlConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    suspend fun scheduleAll() {
        // 레거시 소스별 주기 워커 일괄 폐기 (C1 마이그레이션)
        val allSources = app().sourceRepository.getAll()
        allSources.forEach { workManager.cancelUniqueWork("crawl_${it.id}") }
        workManager.cancelAllWorkByTag(TAG_CRAWL)

        val request = PeriodicWorkRequestBuilder<PipelineWorker>(
            PipelineLogic.PIPELINE_INTERVAL_MINUTES.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(crawlConstraints())
            .addTag(TAG_CRAWL)
            .addTag(PipelineWorker.TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PipelineWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
        DebugLogger.i(
            "스케줄",
            "파이프라인 예약 ${PipelineLogic.PIPELINE_INTERVAL_MINUTES}분 주기 (레거시 ${allSources.size}건 폐기)",
        )
    }

    /** 오전 9시 일일 요약 알림 — 24시간 주기 + 다음 9시까지 첫 실행 지연 */
    fun scheduleDailySummary() {
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(TimeUtils.millisUntilNextHour(SUMMARY_HOUR), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SUMMARY_UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        DebugLogger.i("스케줄", "일일 요약 예약 (9시, 주기 24시간)")
    }

    /** 소스 단위 재예약 — C1에서는 파이프라인이 due를 판정하므로 no-op (레거시 호환) */
    fun scheduleSource(source: com.borasarang.planjupjup.data.db.entity.CrawlSource) {
        workManager.cancelUniqueWork("crawl_${source.id}")
        DebugLogger.i("스케줄", "C1: 소스별 주기 폐기 source=${source.name} (파이프라인 due 판정)")
    }

    fun cancelSource(sourceId: String) {
        workManager.cancelUniqueWork("crawl_$sourceId")
        workManager.cancelUniqueWork("crawl_once_$sourceId")
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_CRAWL)
        workManager.cancelAllWorkByTag(TAG_MANUAL)
        workManager.cancelAllWorkByTag(PipelineWorker.TAG)
        workManager.cancelUniqueWork(PipelineWorker.UNIQUE_NAME)
        workManager.cancelAllWorkByTag(LEGACY_TAG_MANUAL)
        DebugLogger.i("스케줄", "수집 예약 일괄 취소")
    }

    /** 수동 즉시 수집. sourceId=null이면 전체 활성 소스. */
    suspend fun triggerImmediate(sourceId: String?) {
        val targets = if (sourceId.isNullOrBlank()) {
            app().sourceRepository.getEnabled()
        } else {
            app().sourceRepository.getById(sourceId)?.let { listOf(it) } ?: emptyList()
        }
        targets.filter { it.enabled }.forEachIndexed { index, source ->
            val request = OneTimeWorkRequestBuilder<CrawlWorker>()
                .setInputData(workDataOf(CrawlWorker.KEY_SOURCE_ID to source.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInitialDelay((index * 20).toLong(), TimeUnit.SECONDS)
                .addTag(TAG_CRAWL)
                .addTag(TAG_MANUAL)
                .build()
            workManager.enqueueUniqueWork(
                "crawl_once_${source.id}",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
        DebugLogger.i("수동수집", "즉시 수집 예약 count=${targets.size} sourceId=$sourceId")
    }

    companion object {
        private const val TAG_CRAWL = "planjupjup_crawl"
        private const val TAG_PREFIX = "planjupjup_crawl_"
        private const val TAG_MANUAL = "planjupjup_manual_crawl"
        private const val LEGACY_TAG_MANUAL = "manual_crawl"
        private const val SUMMARY_UNIQUE_NAME = "daily_summary"
        private const val SUMMARY_HOUR = 9
    }
}
