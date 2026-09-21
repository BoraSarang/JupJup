package com.borasarang.communityjupjup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.communityjupjup.CommunityJupJupRuntime
import com.borasarang.communityjupjup.data.db.entity.NotificationType
import com.borasarang.communityjupjup.data.repository.CategoryCount
import com.borasarang.communityjupjup.data.repository.NotificationDetail
import com.borasarang.communityjupjup.util.CommunityCategories
import com.borasarang.communityjupjup.util.DebugLogger
import com.borasarang.communityjupjup.util.TimeUtils

/**
 * 오전 9시 일일 요약 알림 워커.
 * 당일 00시 이후 신규 게시글 집계를 CRAWL_SUMMARY 알림으로 기록.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLogger.i("알림", "[SUMMARY] 일일 요약 생성 시작")
        val app = CommunityJupJupRuntime
        return try {
            val startOfToday = TimeUtils.startOfToday()
            val newCount = app.database.postDao().countNewSince(startOfToday)

            // R7: 본문 리소스화 (DB 저장 문구와 동일 출력)
            val summary = applicationContext.getString(
                com.borasarang.communityjupjup.R.string.cm_summary_daily, newCount,
            )
            val byCategory = app.database.postDao().countByCategory()
                .map { CategoryCount(CommunityCategories.nameOf(it.categoryId), it.cnt) }
            val detail = NotificationDetail(
                type = NotificationType.CRAWL_SUMMARY,
                summary = summary,
                totalFound = newCount,
                newPosts = newCount,
                updatedPosts = 0,
                failedCount = 0,
                bySource = emptyList(),
                byCategory = byCategory,
                newPostsDetail = emptyList(),
                failedSources = emptyList(),
                startedAt = startOfToday,
                finishedAt = System.currentTimeMillis(),
            )
            app.notificationService.createSummaryNotification(summary, detail)
            DebugLogger.i("알림", "일일 요약 저장 완료 new=$newCount")
            // 미상세 백필 (전체 상한 25, 오래된 순 — 보드 워커는 자기 보드만 처리)
            var remaining = DAILY_BACKFILL_TOTAL
            var backfilled = 0
            val sources = app.database.crawlSourceDao().getEnabled()
            for (s in sources) {
                if (remaining <= 0) break
                try {
                    val crawler = com.borasarang.communityjupjup.crawler.BoardCrawler(s, app.database)
                    val config = com.borasarang.communityjupjup.crawler.SelectorConfig
                        .parse(s.selectorConfigJson)
                    val done = crawler.backfill(config, remaining)
                    backfilled += done
                    remaining -= done
                } catch (e: Exception) {
                    DebugLogger.w("알림", "백필 스킵 source=${s.name}: ${e.message}")
                }
            }
            DebugLogger.i("알림", "일일 백필 완료 ${backfilled}건")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.e("알림", "E-AND-NOTIF-0701", "일일 요약 생성 실패: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val DAILY_BACKFILL_TOTAL = 25
    }
}
