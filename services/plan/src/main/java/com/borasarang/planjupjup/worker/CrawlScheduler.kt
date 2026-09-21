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
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.TimeUtils
import java.util.concurrent.TimeUnit

/**
 * 수집 스케줄러. 소스별 개별 PeriodicWork + 수동 즉시 수집.
 * 제약: 네트워크 연결 필수 + 배터리 부족 제외 + 저장공간 부족 제외.
 */
class CrawlScheduler(private val context: Context) {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private fun app(): PlanJupJupRuntime = PlanJupJupRuntime

    suspend fun scheduleAll() {
        val sources = app().sourceRepository.getEnabled()
        sources.forEach { scheduleSource(it) }
        DebugLogger.i("스케줄", "주기 수집 등록 count=${sources.size}")
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

    fun scheduleSource(source: CrawlSource) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        // 소스별 독립 PeriodicWork — 병렬 수집. 최소 15분(WorkManager 하한)
        val minutes = source.intervalMinutes
            .coerceAtLeast(com.borasarang.planjupjup.util.TimeUtils.MIN_INTERVAL_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<CrawlWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf(CrawlWorker.KEY_SOURCE_ID to source.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG_CRAWL)
            .addTag(TAG_PREFIX + source.type)
            .build()
        workManager.enqueueUniquePeriodicWork(
            uniqueName(source.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelSource(sourceId: String) {
        workManager.cancelUniqueWork(uniqueName(sourceId))
    }

    fun cancelAll() {
        // 하드코딩 5종 취소는 신규 소스·수동 태그를 놓친다 → 태그 기반 일괄 취소
        workManager.cancelAllWorkByTag(TAG_CRAWL)
        workManager.cancelAllWorkByTag(TAG_MANUAL)
        // 구 태그 잔재 1회 정리 (crawl_*·manual_crawl)
        workManager.cancelAllWorkByTag(LEGACY_TAG_MANUAL)
        DebugLogger.i("스케줄", "수집 예약 일괄 취소")
    }

    /** 수동 즉시 수집. sourceId=null이면 전체 활성 소스.
     *  R1: unique KEEP — 연타해도 소스당 1개만 적재 (무한 워커 폭증 방지).
     *  중복 실행은 CrawlWorker의 SourceLocks가 스킵한다. */
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
        private const val UNIQUE_PREFIX = "crawl_"
        private const val TAG_CRAWL = "planjupjup_crawl"
        private const val TAG_PREFIX = "planjupjup_crawl_"
        private const val TAG_MANUAL = "planjupjup_manual_crawl"
        private const val LEGACY_TAG_MANUAL = "manual_crawl"
        private const val SUMMARY_UNIQUE_NAME = "daily_summary"
        private const val SUMMARY_HOUR = 9

        private fun uniqueName(sourceId: String) = UNIQUE_PREFIX + sourceId
    }
}
