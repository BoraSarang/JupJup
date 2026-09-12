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
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.util.DebugLogger
import java.util.concurrent.TimeUnit

/**
 * 소스별 개별 주기 스케줄 + 즉시 실행.
 * WorkManager 최소 주기 15분 — 그 미만은 15분으로 올림.
 */
class CrawlScheduler(private val context: Context) {

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** 활성 소스 전체를 각자 주기로 예약 (앱 시작 시 1회) */
    suspend fun scheduleAll(db: MacDatabase) {
        val wm = WorkManager.getInstance(context)
        val sources = db.crawlSourceDao().getEnabled()
        for (s in sources) {
            wm.enqueueUniquePeriodicWork(
                "crawl_${s.id}",
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodic(s.id, s.intervalMinutes),
            )
            DebugLogger.i("스케줄", "예약 source=${s.name} ${s.intervalMinutes}분마다")
        }
    }

    /** 단일 소스 재예약 (토글 on·주기 변경 시) */
    fun scheduleSource(source: com.borasarang.macjupjup.data.db.entity.CrawlSource) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "crawl_${source.id}",
            ExistingPeriodicWorkPolicy.REPLACE,
            buildPeriodic(source.id, source.intervalMinutes),
        )
        DebugLogger.i("스케줄", "재예약 source=${source.name} ${source.intervalMinutes}분마다")
    }

    /** 단일 소스 예약 취소 (토글 off 시) */
    fun cancelSource(sourceId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("crawl_$sourceId")
        DebugLogger.i("스케줄", "예약 취소 source=$sourceId")
    }

    private fun buildPeriodic(
        sourceId: String,
        intervalMinutes: Int,
    ): androidx.work.PeriodicWorkRequest {
        val minutes = intervalMinutes.coerceAtLeast(15).toLong()
        return PeriodicWorkRequestBuilder<CrawlWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .setInputData(workDataOf(CrawlWorker.KEY_SOURCE_ID to sourceId))
            .addTag(TAG_CRAWL)
            .build()
    }

    /** 즉시 수집: sourceId null이면 전체 활성 소스 */
    suspend fun triggerImmediate(db: MacDatabase, sourceId: String?) {
        val wm = WorkManager.getInstance(context)
        val ids = if (sourceId.isNullOrBlank()) {
            db.crawlSourceDao().getEnabled().map { it.id }
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
            wm.enqueueUniqueWork(
                "crawl_once_$id",
                // P0-2: REPLACE는 실행 중 워커까지 취소해 수집이 증발하므로 KEEP.
                // 중복 실행은 CrawlWorker의 SourceLocks가 스킵한다.
                ExistingWorkPolicy.KEEP,
                req,
            )
        }
        DebugLogger.i("수동수집", "즉시 수집 예약 ${ids.size}건")
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_CRAWL)
    }

    /** 오전 9시 일일 요약 예약 (24h 주기, KEEP — 1회성이던 문제 수정) */
    fun scheduleDailySummary() {
        val delayMs = com.borasarang.macjupjup.util.TimeUtils.millisUntilNextHour(9)
        val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG_SUMMARY)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            // 기존 1회성 예약과 별도 이름 (충돌 방지, 구 예약은 1회 실행 후 소멸)
            "daily_summary_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
        DebugLogger.i("스케줄", "일일 요약 예약 24h 주기 (${delayMs / 3600000}시간 후 첫 실행)")
    }

    /** 번역 워커 3시간 주기 예약 (T-150: 적체 해소용 단축, 수집과 독립 생명주기) */
    fun scheduleTranslate() {
        val req = PeriodicWorkRequestBuilder<TranslateWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints())
            .addTag(TAG_TRANSLATE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "translate_ko",
            // T-150: REPLACE — 기존 설치분의 6h 예약을 3h로 교체 (KEEP이면 구 주기 유지됨)
            ExistingPeriodicWorkPolicy.REPLACE,
            req,
        )
        DebugLogger.i("스케줄", "번역 워커 예약 3시간마다")
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
