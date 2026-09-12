package com.borasarang.macjupjup.util

/** 수집 실패 연속 여부 판정 */
object CrawlStats {
    /** 최근 N개 상태가 전부 FAILED이면 true */
    fun isFailureStreak(statuses: List<String>, streak: Int = 5): Boolean {
        if (statuses.size < streak) return false
        return statuses.take(streak).all { it == Constants.STATUS_FAILED }
    }
}
