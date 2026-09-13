package com.borasarang.planjupjup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.planjupjup.PlanJupJupRuntime
import com.borasarang.planjupjup.data.repository.BrandCount
import com.borasarang.planjupjup.data.repository.NetworkCount
import com.borasarang.planjupjup.data.repository.NewPlanSummary
import com.borasarang.planjupjup.data.repository.NotificationDetail
import com.borasarang.planjupjup.data.repository.NotificationType
import com.borasarang.planjupjup.util.DebugLogger
import com.borasarang.planjupjup.util.TimeUtils

/**
 * 오전 9시 일일 요약 알림 워커.
 * 전일/오늘 수집·신규 집계를 CRAWL_SUMMARY 알림으로 기록.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLogger.i("알림", "[SUMMARY] 일일 요약 생성 시작")
        val app = PlanJupJupRuntime
        return try {
            val startOfToday = TimeUtils.startOfToday()
            val summaryData = app.planRepository.getDailySummary(startOfToday)
            val newToday = summaryData.newPlans
            val updatedToday = (summaryData.collectedToday - newToday.size).coerceAtLeast(0)

            // R7: 본문 리소스화 (DB 저장 문구와 동일 출력)
            val updatedSuffix = if (updatedToday > 0) {
                applicationContext.getString(
                    com.borasarang.planjupjup.R.string.plan_summary_daily_updated, updatedToday,
                )
            } else {
                ""
            }
            val summary = applicationContext.getString(
                com.borasarang.planjupjup.R.string.plan_summary_daily,
                summaryData.collectedToday, newToday.size, updatedSuffix,
            )
            val detail = NotificationDetail(
                type = NotificationType.CRAWL_SUMMARY,
                summary = summary,
                totalFound = summaryData.collectedToday,
                newPlans = newToday.size,
                updatedPlans = updatedToday,
                failedCount = 0,
                bySource = emptyList(),
                byBrand = newToday.groupingBy { it.carrierName }
                    .eachCount().map { BrandCount(it.key, it.value) },
                byNetwork = newToday.groupingBy { it.networkType }
                    .eachCount().map { NetworkCount(it.key, it.value) },
                newPlansDetail = newToday.map {
                    NewPlanSummary(
                        id = it.id,
                        carrierName = it.carrierName,
                        planName = it.planName,
                        price = it.price,
                        dataAmount = it.dataAmount,
                        networkType = it.networkType,
                    )
                },
                failedSources = emptyList(),
                startedAt = startOfToday,
                finishedAt = System.currentTimeMillis(),
            )

            app.notificationService.createSummaryNotification(summary, detail)
            DebugLogger.i("알림", "일일 요약 저장 완료 collected=${summaryData.collectedToday} new=${newToday.size}")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.e("알림", "E-AND-NOTIF-0701", "일일 요약 생성 실패: ${e.message}", e)
            Result.retry()
        }
    }
}