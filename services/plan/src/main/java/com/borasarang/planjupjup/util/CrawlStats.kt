package com.borasarang.planjupjup.util

/** 수집 실패 연속 기록 판정 (순수 로직, 단위 테스트 대상) */
object CrawlStats {
    const val FAILURE_STREAK_THRESHOLD = 5

    /** 최신순 상태 목록이 threshold 연속 FAILED이면 true */
    fun isFailureStreak(statusesDesc: List<String>, threshold: Int = FAILURE_STREAK_THRESHOLD): Boolean {
        if (statusesDesc.size < threshold) return false
        return statusesDesc.take(threshold).all { it == Constants.STATUS_FAILED }
    }
}
