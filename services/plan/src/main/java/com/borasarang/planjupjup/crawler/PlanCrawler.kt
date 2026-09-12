package com.borasarang.planjupjup.crawler

import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping

/** 크롤러 산출물: 요금제 1건 + 출처 매핑 N건 */
data class PlanDraft(
    val plan: Plan,
    val mappings: List<PlanSourceMapping>,
)

interface PlanCrawler {
    val sourceName: String
    /** 실패 시 Result.failure (Worker가 로그+재시도 처리) */
    suspend fun crawl(): Result<List<PlanDraft>>
}
