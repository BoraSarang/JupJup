package com.borasarang.common.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 시간 포맷 공용 베이스. 각 서비스는 주기 옵션·전용 포맷만 달리해 상속한다 (R38):
 * mac/community는 그대로, plan은 formatPrice 추가 + formatInterval 재정의.
 * 네트워크 부분(NetUtils)은 R2부터 이관됨.
 */
open class BaseTimeUtils {
    /** companion 상속이 안 되므로 인스턴스 멤버로 둔다 (호출부 `TimeUtils.X` 유지) */
    val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    val MIN_INTERVAL_MINUTES = 15

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

    /** 절대 시각 (yyyy-MM-dd HH:mm, 기기 로컬 타임존) */
    fun formatAbsolute(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "없음"
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                .format(java.util.Date(epochMillis))
        } catch (_: Exception) {
            "없음"
        }
    }

    /** 소요 시간 (초→"N초"/"N분 N초") */
    fun formatDuration(startedAt: Long?, finishedAt: Long?): String? {
        if (startedAt == null || finishedAt == null || startedAt <= 0 || finishedAt < startedAt) {
            return null
        }
        val secs = (finishedAt - startedAt) / 1000
        return if (secs < 60) "소요 ${secs}초" else "소요 ${secs / 60}분 ${secs % 60}초"
    }

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

    /**
     * HH:mm 형식까지의 지연 밀리초. 이미 지났으면 다음 날 같은 시각.
     * 파싱 실패 시 [fallbackMillis] (기본 1시간).
     */
    fun millisUntilTime(timeValue: String, fallbackMillis: Long = java.util.concurrent.TimeUnit.HOURS.toMillis(1)): Long {
        return try {
            val parts = timeValue.split(":")
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            target.timeInMillis - now.timeInMillis
        } catch (_: Exception) {
            fallbackMillis
        }
    }

    /** 60→"1시간마다", 1440→"24시간마다", 10080→"주 1회", 43200→"월 1회" (plan은 재정의) */
    open fun formatInterval(minutes: Int): String {
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
