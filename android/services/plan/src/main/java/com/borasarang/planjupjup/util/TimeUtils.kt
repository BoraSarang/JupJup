package com.borasarang.planjupjup.util

import com.borasarang.common.util.BaseTimeUtils
import java.util.Locale

/**
 * 시간 포맷 유틸 (요금줍줍 전용: 가격 포맷·30분 주기 옵션 포함).
 * 공용 포맷은 common BaseTimeUtils (R38).
 */
object TimeUtils : BaseTimeUtils() {
    fun formatPrice(won: Int?): String {
        if (won == null) return "-"
        return String.format(Locale.KOREA, "%,d원", won)
    }

    /** 수집 주기 선택지(분). WorkManager 최소 주기 15분 */
    val INTERVAL_OPTIONS_MINUTES = listOf(30, 60, 360, 720, 1440, 10080)

    /** 30→"30분마다", 60→"1시간마다", 1440→"24시간마다", 10080→"주 1회" (월간 옵션 없음) */
    override fun formatInterval(minutes: Int): String {
        return when {
            minutes <= 0 -> "미설정"
            minutes < 60 -> "${minutes}분마다"
            minutes == 10080 -> "주 1회"
            minutes == 1440 -> "24시간마다"
            minutes % 1440 == 0 -> "${minutes / 1440}일마다"
            minutes % 60 == 0 -> "${minutes / 60}시간마다"
            else -> "${minutes}분마다"
        }
    }
}
