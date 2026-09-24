package com.borasarang.macjupjup.crawler

import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping

/** 크롤러 산출물: 앱 1건 + 출처 매핑 N건 */
data class AppDraft(
    val app: App,
    val mappings: List<AppSourceMapping>,
)

interface AppCrawler {
    val sourceName: String
    /** 실패 시 Result.failure (Worker가 로그+재시도 처리) */
    suspend fun crawl(): Result<List<AppDraft>>
}
