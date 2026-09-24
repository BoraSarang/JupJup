package com.borasarang.common.util.net

/**
 * 네트워크 예산 상수 (R42b, PLAN_v18).
 * 초과 시 WARN + 로그만 — 수집 차단·실패 처리 아님 (budgets.json 규칙).
 */
object NetBudget {
    /** 일일 WARN 임계 (서비스별 합산 기준) */
    const val DAILY_WARN_BYTES: Long = 200L * 1024L * 1024L

    /** 단일 수집 실행 WARN 임계 */
    const val SINGLE_RUN_WARN_BYTES: Long = 50L * 1024L * 1024L

    fun isDailyOver(dayBytes: Long): Boolean = dayBytes >= DAILY_WARN_BYTES

    fun isSingleRunOver(runBytes: Long): Boolean = runBytes >= SINGLE_RUN_WARN_BYTES
}
