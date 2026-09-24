package com.borasarang.planjupjup.worker

import com.borasarang.planjupjup.data.db.entity.CrawlSource

/**
 * C1 단일 파이프라인 due 판정·선택 로직 ( 순수 함수, 단위 테스트 대상 ).
 * 타입 공통 하단 15분 (WorkManager 최소 주기).
 */
object PipelineLogic {

    const val MIN_INTERVAL_MINUTES = 15
    const val PIPELINE_INTERVAL_MINUTES = 15
    /** 파이프라인 1회 전체 런타임 예산 — 초과분은 다음 회차로 이월 */
    const val PIPELINE_MAX_RUNTIME_MS = 300_000L
    /** 1회 실행에서 처리할 최대 소스 수 */
    const val MAX_SOURCES_PER_RUN = 8

    fun isDue(source: CrawlSource, now: Long): Boolean {
        if (!source.enabled) return false
        val last = source.lastRunAt ?: return true
        val interval = maxOf(source.intervalMinutes, MIN_INTERVAL_MINUTES)
        return now - last >= interval * 60_000L
    }

    fun selectDue(sources: List<CrawlSource>, now: Long, limit: Int = MAX_SOURCES_PER_RUN): List<CrawlSource> {
        return sources
            .filter { isDue(it, now) }
            .sortedBy { it.lastRunAt ?: 0L }
            .take(limit)
    }
}
