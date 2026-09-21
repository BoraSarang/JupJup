package com.borasarang.communityjupjup.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 시간 포맷 유틸 (맥줍줍 전용: 월간 주기·절대시각·소요시간 포함).
 * 네트워크 부분(NetUtils)은 R2부터 `com.borasarang.common.util.NetUtils`로 이관됨.
 */

object TimeUtils {
    const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun formatRelative(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "없음"
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 0) return "방금 전"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 1) return "방금 전"
        if (minutes < 60) return "${minutes}분 전"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "${hours}시간 전"
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days < 30) return "${days}일 전"
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(java.util.Date(epochMillis))
    }

    /** T-090: 절대 시각 (yyyy-MM-dd HH:mm, 기기 로컬 타임존) */
    fun formatAbsolute(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "없음"
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                .format(java.util.Date(epochMillis))
        } catch (_: Exception) {
            "없음"
        }
    }

    /** T-090: 소요 시간 (초→"N초"/"N분 N초") */
    fun formatDuration(startedAt: Long?, finishedAt: Long?): String? {
        if (startedAt == null || finishedAt == null || startedAt <= 0 || finishedAt < startedAt) {
            return null
        }
        val secs = (finishedAt - startedAt) / 1000
        return if (secs < 60) "소요 ${secs}초" else "소요 ${secs / 60}분 ${secs % 60}초"
    }
    /** 수집 주기 선택지(분). WorkManager 최소 주기 15분. 월간(43200) 포함 */
    val INTERVAL_OPTIONS_MINUTES = listOf(60, 360, 720, 1440, 10080, 43200)
    const val MIN_INTERVAL_MINUTES = 15

    /** 오늘 00:00 (기기 로컬 타임존) */
    fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 다음 지정 시각(0~23시)까지 남은 밀리초 — 일일 요약 워커 첫 실행 지연용 */
    fun millisUntilNextHour(hour: Int): Long {
        val now = java.util.Calendar.getInstance()
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (!after(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    /** 60→"1시간마다", 1440→"24시간마다", 10080→"주 1회", 43200→"월 1회" */
    fun formatInterval(minutes: Int): String {
        return when {
            minutes <= 0 -> "미설정"
            minutes < 60 -> "${minutes}분마다"
            minutes == 43200 -> "월 1회"
            minutes == 10080 -> "주 1회"
            minutes == 1440 -> "24시간마다"
            minutes % 1440 == 0 -> "${minutes / 1440}일마다"
            minutes % 60 == 0 -> "${minutes / 60}시간마다"
            else -> "${minutes}분마다"
        }
    }
}
