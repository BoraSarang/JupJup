package com.borasarang.common.util

/** 수집 실패 연속 여부 판정 (R40: 3벌 승격). 순수 로직. */
object CrawlStats {
    /** 최근 N개 상태가 전부 실패이면 true */
    fun isFailureStreak(
        statuses: List<String>,
        streak: Int = 5,
        failedStatus: String = "FAILED",
    ): Boolean {
        if (statuses.size < streak) return false
        return statuses.take(streak).all { it == failedStatus }
    }
}
