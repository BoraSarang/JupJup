package com.borasarang.macjupjup.worker

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants

/**
 * C1 단일 파이프라인 due 판정·선택 로직 ( 순수 함수, 단위 테스트 대상 ).
 * 타입별 하단 주기: 뉴스≥30분, 커뮤니티≥60분, 그 외≥15분.
 */
object PipelineLogic {

    const val MIN_INTERVAL_MINUTES = 15
    const val PIPELINE_INTERVAL_MINUTES = 15
    /** 파이프라인 1회 전체 런타임 예산 — 초과분은 다음 회차로 이월 */
    const val PIPELINE_MAX_RUNTIME_MS = 300_000L
    /** 1회 실행에서 처리할 최대 소스 수 (스파이크 분산) */
    const val MAX_SOURCES_PER_RUN = 8

    fun floorMinutes(type: String?): Int = when (type) {
        Constants.TYPE_NEWS_RSS -> Constants.NEWS_INTERVAL_MINUTES
        Constants.TYPE_COMMUNITY_BOARD -> Constants.COMMUNITY_INTERVAL_MINUTES
        else -> MIN_INTERVAL_MINUTES
    }

    fun isDue(source: CrawlSource, now: Long): Boolean {
        if (!source.enabled) return false
        val last = source.lastRunAt ?: return true
        val interval = maxOf(source.intervalMinutes, floorMinutes(source.type))
        return now - last >= interval * 60_000L
    }

    /** due 소스: 오래된 것부터, 상한 [limit]건 */
    fun selectDue(sources: List<CrawlSource>, now: Long, limit: Int = MAX_SOURCES_PER_RUN): List<CrawlSource> {
        return sources
            .filter { isDue(it, now) }
            .sortedBy { it.lastRunAt ?: 0L }
            .take(limit)
    }
}
