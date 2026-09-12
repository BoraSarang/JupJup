package com.borasarang.macjupjup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.borasarang.macjupjup.MacJupJupRuntime
import com.borasarang.macjupjup.data.repository.CategoryCount
import com.borasarang.macjupjup.data.repository.LicenseCount
import com.borasarang.macjupjup.data.repository.NewAppSummary
import com.borasarang.macjupjup.data.db.entity.NotificationType
import com.borasarang.macjupjup.data.repository.NotificationDetail
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.TimeUtils

/**
 * 오전 9시 일일 요약 알림 워커.
 * 당일 00시 이후 신규 앱 집계를 CRAWL_SUMMARY 알림으로 기록.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLogger.i("알림", "[SUMMARY] 일일 요약 생성 시작")
        val app = MacJupJupRuntime
        return try {
            val startOfToday = TimeUtils.startOfToday()
            val dao = app.database.appDao()
            val newCount = dao.countNewSince(startOfToday)
            val bumps = dao.countVersionBumpsSince(startOfToday)

            val summary = "일일 요약: 신규 ${newCount}건 · 버전업 ${bumps}건"
            val detail = NotificationDetail(
                type = NotificationType.CRAWL_SUMMARY,
                summary = summary,
                totalFound = newCount + bumps,
                newApps = newCount,
                updatedApps = bumps,
                failedCount = 0,
                bySource = emptyList(),
                byCategory = emptyList(),
                byLicense = emptyList(),
                newAppsDetail = emptyList(),
                failedSources = emptyList(),
                startedAt = startOfToday,
                finishedAt = System.currentTimeMillis(),
            )
            app.notificationService.createSummaryNotification(summary, detail)
            DebugLogger.i("알림", "일일 요약 저장 완료 new=$newCount bumps=$bumps")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.e("알림", "E-AND-NOTIF-0701", "일일 요약 생성 실패: ${e.message}", e)
            Result.retry()
        }
    }
}
