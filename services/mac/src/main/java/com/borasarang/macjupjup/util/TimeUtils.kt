package com.borasarang.macjupjup.util

import com.borasarang.common.util.BaseTimeUtils

/**
 * 시간 포맷 유틸 (맥줍줍 전용: 월간 주기 옵션).
 * 공용 포맷은 common BaseTimeUtils (R38).
 */
object TimeUtils : BaseTimeUtils() {
    /** 수집 주기 선택지(분). WorkManager 최소 주기 15분. 월간(43200) 포함 */
    val INTERVAL_OPTIONS_MINUTES = listOf(60, 360, 720, 1440, 10080, 43200)
}
