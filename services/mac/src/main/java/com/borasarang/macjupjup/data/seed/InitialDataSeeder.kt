package com.borasarang.macjupjup.data.seed

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger

/**
 * 초기 수집 소스 6개 시드 (Setapp·PH·HN·MMB 제거됨). selectorConfigJson=null = 코드 기본값 사용.
 * 제거된 소스 잔재는 Application 시작 시 purge (행·매핑·고아앱·로그 삭제).
 * DB v1 기존 기기는 없는 소스만 추가(seedMissing).
 */
object InitialDataSeeder {

    private fun seeds() = listOf(
                CrawlSource(
                    id = Constants.SOURCE_GITHUB_SEARCH,
                    name = "GitHub 신규 저장소",
                    type = Constants.TYPE_GITHUB_SEARCH,
                    baseUrl = "https://api.github.com",
                    enabled = true,
                    intervalHours = 6,
                    intervalMinutes = 360,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_GITHUB_RELEASES,
                    name = "GitHub 릴리즈 추적",
                    type = Constants.TYPE_GITHUB_RELEASES,
                    baseUrl = "https://api.github.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_CHART_RSS,
                    name = "Mac 차트 RSS",
                    type = Constants.TYPE_CHART_RSS,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_ITUNES_LOOKUP,
                    name = "iTunes 버전 폴링",
                    type = Constants.TYPE_ITUNES_LOOKUP,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_NAME_MATCH,
                    name = "iTunes 이름 대조",
                    type = Constants.TYPE_NAME_MATCH,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 168,
                    intervalMinutes = 10080,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
                CrawlSource(
                    id = Constants.SOURCE_MAS_DISCOVERY,
                    name = "MAS 키워드 발견",
                    type = Constants.TYPE_MAS_DISCOVERY,
                    baseUrl = "https://itunes.apple.com",
                    enabled = true,
                    intervalHours = 24,
                    intervalMinutes = 1440,
                    lastRunAt = null,
                    lastStatus = Constants.STATUS_NEVER_RUN,
                    errorMessage = null,
                    selectorConfigJson = null,
                ),
            )

    suspend fun seedIfEmpty(db: MacDatabase) {
        if (db.crawlSourceDao().getAll().isNotEmpty()) {
            seedMissing(db)
            return
        }
        DebugLogger.i("시드", "초기 수집 소스 시드 시작")
        db.crawlSourceDao().upsertAll(seeds())
        DebugLogger.i("시드", "초기 수집 소스 시드 완료")
    }

    /** 기존 DB에 없는 신규 소스만 추가 (v1→v1.1 name_match 등) */
    suspend fun seedMissing(db: MacDatabase) {
        val dao = db.crawlSourceDao()
        val missing = seeds().filter { dao.getById(it.id) == null }
        if (missing.isEmpty()) return
        DebugLogger.i("시드", "신규 소스 추가: ${missing.map { it.id }}")
        dao.upsertAll(missing)
    }
}
