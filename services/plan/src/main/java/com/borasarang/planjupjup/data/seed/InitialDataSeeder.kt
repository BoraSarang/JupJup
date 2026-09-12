package com.borasarang.planjupjup.data.seed

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger

/**
 * 초기 수집 소스 5개 시드. selectorConfigJson=null = 코드 기본값 사용.
 * liivm은 셀렉터 미검증(STAGED)이므로 enabled=false 로 시드한다.
 */
object InitialDataSeeder {

    suspend fun seedIfEmpty(db: PlanDatabase) {
        if (db.crawlSourceDao().getAll().isNotEmpty()) return
        DebugLogger.i("시드", "초기 수집 소스 시드 시작")
        db.crawlSourceDao().upsertAll(
            listOf(
                CrawlSource(
                    id = Constants.SOURCE_MVNOHUB,
                    name = "알뜰폰허브",
                    type = Constants.TYPE_COMPARE_SITE,
                    baseUrl = "https://www.mvnohub.kr",
                    enabled = true,
                    intervalHours = 6,
                    intervalMinutes = 360,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_MOYO,
                    name = "모요",
                    type = Constants.TYPE_COMPARE_SITE,
                    baseUrl = "https://www.moyoplan.com",
                    enabled = true,
                    intervalHours = 6,
                    intervalMinutes = 360,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_KTMMOBILE,
                    name = "KT엠모바일 공식",
                    type = Constants.TYPE_CARRIER_SITE,
                    baseUrl = "https://www.ktmmobile.com",
                    enabled = true,
                    intervalHours = 12,
                    intervalMinutes = 720,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_LIIVM,
                    name = "LG헬로모바일 공식",
                    type = Constants.TYPE_CARRIER_SITE,
                    baseUrl = "https://www.hellomobile.co.kr",
                    enabled = false,
                    intervalHours = 12,
                    intervalMinutes = 720,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_DISABLED,
                    errorMessage = "셀렉터 미검증(STAGED) — 실기 검증 후 활성화",
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_BRAND_LIST,
                    name = "알뜰폰허브 브랜드목록",
                    type = Constants.TYPE_BRAND_LIST,
                    baseUrl = "https://www.mvnohub.kr",
                    enabled = true,
                    intervalHours = 168,
                    intervalMinutes = 10080,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
            ),
        )
        DebugLogger.i("시드", "초기 수집 소스 시드 완료")
    }
}
