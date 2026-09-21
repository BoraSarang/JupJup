package com.borasarang.communityjupjup.worker

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
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.communityjupjup.data.db.entity.SiteBoard
import com.borasarang.communityjupjup.util.DebugLogger
import java.util.concurrent.TimeUnit
/**
 * 보드별 개별 주기 스케줄 + 즉시 실행 (R27: 소스 단위 → 보드 단위).
 * WorkManager 최소 주기 15분 — 그 미만은 15분으로 올림.
 * 허용 주기: 15/30/60/120 (포털 게시판 관리에서 선택).
 */
class CrawlScheduler(private val context: Context) {

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** 활성 보드 전체를 각자 주기로 예약 (앱 시작 시 1회) */
    suspend fun scheduleAll() {
        val wm = WorkManager.getInstance(context)
        val db = CommunityJupJupRuntime.database
        val sources = db.crawlSourceDao().getAll().associateBy { it.id }
        for (b in db.siteBoardDao().getAll()) {
            if (!b.enabled || sources[b.sourceId]?.enabled != true) continue
            wm.enqueueUniquePeriodicWork(
                periodicName(b.id),
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodic(b.id, b.intervalMinutes),
            )
            DebugLogger.i("스케줄", "예약 board=${b.boardName} ${b.intervalMinutes}분마다")
        }
    }

    /** 단일 보드 재예약 (on·주기 변경 시) */
    fun scheduleBoard(board: SiteBoard) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicName(board.id),
            ExistingPeriodicWorkPolicy.REPLACE,
            buildPeriodic(board.id, board.intervalMinutes),
        )
        DebugLogger.i("스케줄", "재예약 board=${board.boardName} ${board.intervalMinutes}분마다")
    }

    /** 단일 소스 재예약 (토글 on 시: 소속 보드 전체) */
    suspend fun scheduleSource(sourceId: String) {
        val db = CommunityJupJupRuntime.database
        for (b in db.siteBoardDao().getBySource(sourceId)) {
            if (b.enabled) scheduleBoard(b)
        }
    }

    /** 단일 보드 예약 취소 */
    fun cancelBoard(boardId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(boardId))
        WorkManager.getInstance(context).cancelUniqueWork(onceName(boardId))
        DebugLogger.i("스케줄", "예약 취소 board=$boardId")
    }

    /** 단일 소스 예약 취소 (토글 off 시: 소속 보드 전체 + 구 소스 작업) */
    suspend fun cancelSource(sourceId: String) {
        val wm = WorkManager.getInstance(context)
        // 구 소스 단위 작업명 잔재 정리 (v4 이전)
        wm.cancelUniqueWork("crawl_$sourceId")
        wm.cancelUniqueWork("crawl_once_$sourceId")
        val db = CommunityJupJupRuntime.database
        for (b in db.siteBoardDao().getBySource(sourceId)) {
            cancelBoard(b.id)
        }
    }

    private fun buildPeriodic(
        boardId: Long,
        intervalMinutes: Int,
    ): androidx.work.PeriodicWorkRequest {
        val minutes = intervalMinutes.coerceAtLeast(15).toLong()
        return PeriodicWorkRequestBuilder<CrawlWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .setInputData(workDataOf(CrawlWorker.KEY_BOARD_ID to boardId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG_CRAWL)
            .build()
    }

    /** 즉시 수집: sourceId null이면 전체 활성 보드 */
    suspend fun triggerImmediate(sourceId: String?) {
        val wm = WorkManager.getInstance(context)
        val db = CommunityJupJupRuntime.database
        val sources = db.crawlSourceDao().getAll().associateBy { it.id }
        val boards = if (sourceId.isNullOrBlank()) {
            db.siteBoardDao().getAll()
        } else {
            db.siteBoardDao().getBySource(sourceId)
        }.filter { it.enabled && sources[it.sourceId]?.enabled == true }
        boards.forEachIndexed { index, b ->
            val req = OneTimeWorkRequestBuilder<CrawlWorker>()
                .setConstraints(constraints())
                .setInputData(workDataOf(CrawlWorker.KEY_BOARD_ID to b.id))
                .setInitialDelay((index * 20).toLong(), TimeUnit.SECONDS)
                .addTag(TAG_CRAWL)
                .build()
            wm.enqueueUniqueWork(
                onceName(b.id),
                // P0-2: REPLACE는 실행 중 워커까지 취소해 수집이 증발하므로 KEEP.
                // 중복 실행은 CrawlWorker의 SourceLocks가 스킵한다.
                ExistingWorkPolicy.KEEP,
                req,
            )
        }
        DebugLogger.i("수동수집", "즉시 수집 예약 ${boards.size}건")
    }

    /** 단일 보드 즉시 수집 (신규 보드 추가 직후) */
    suspend fun triggerBoard(boardId: Long) {
        val db = CommunityJupJupRuntime.database
        val board = db.siteBoardDao().getById(boardId) ?: return
        val source = db.crawlSourceDao().getById(board.sourceId) ?: return
        if (!board.enabled || !source.enabled) return
        val req = OneTimeWorkRequestBuilder<CrawlWorker>()
            .setConstraints(constraints())
            .setInputData(workDataOf(CrawlWorker.KEY_BOARD_ID to board.id))
            .addTag(TAG_CRAWL)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            onceName(board.id),
            ExistingWorkPolicy.KEEP,
            req,
        )
        DebugLogger.i("수동수집", "보드 즉시 수집 예약 board=${board.boardName}")
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_CRAWL)
    }

    /** 오전 9시 일일 요약 예약 (24h 주기, KEEP) */
    fun scheduleDailySummary() {
        val delayMs = com.borasarang.communityjupjup.util.TimeUtils.millisUntilNextHour(9)
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

    companion object {
        private const val TAG_CRAWL = "communityjupjup_crawl"
        private const val TAG_SUMMARY = "communityjupjup_summary"

        /** 허용 주기 (포털 선택지) */
        val ALLOWED_INTERVALS = setOf(15, 30, 60, 120)

        fun periodicName(boardId: Long) = "crawl_board_$boardId"
        fun onceName(boardId: Long) = "crawl_once_board_$boardId"
    }
}
