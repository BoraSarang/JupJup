package com.borasarang.planjupjup.data.repository

import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.CarrierBrand
import com.borasarang.planjupjup.data.db.entity.CrawlLog
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SourceRepository(private val db: PlanDatabase) {

    fun observeAll(): Flow<List<SourceStatus>> =
        db.crawlSourceDao().observeAll().map { list -> list.map { it.toStatus() } }

    /** 소스 목록 + 소스별 최근 로그 결합 */
    fun observeListItems(): Flow<List<SourceListItem>> =
        db.crawlSourceDao().observeAll().map { list ->
            list.map { source ->
                SourceListItem(
                    status = source.toStatus(),
                    latestLog = db.crawlLogDao().getLatestBySource(source.id)?.toRecent(),
                )
            }
        }

    suspend fun getAll(): List<CrawlSource> = db.crawlSourceDao().getAll()

    suspend fun getEnabled(): List<CrawlSource> = db.crawlSourceDao().getEnabled()

    suspend fun getById(id: String): CrawlSource? = db.crawlSourceDao().getById(id)

    suspend fun toggleEnabled(id: String): Boolean {
        val source = db.crawlSourceDao().getById(id) ?: return false
        val next = !source.enabled
        db.crawlSourceDao().setEnabled(id, next)
        DebugLogger.i("소스관리", "소스 토글 id=$id enabled=$next")
        return next
    }

    /** 수집 주기 변경(분). 허용값 외는 무시하고 false 반환 */
    suspend fun setIntervalMinutes(id: String, minutes: Int): Boolean {
        if (minutes !in com.borasarang.planjupjup.util.TimeUtils.INTERVAL_OPTIONS_MINUTES) {
            DebugLogger.w("소스관리", "허용되지 않은 주기: $minutes")
            return false
        }
        val source = db.crawlSourceDao().getById(id) ?: return false
        db.crawlSourceDao().updateInterval(id, minutes)
        DebugLogger.i("소스관리", "주기 변경 id=$id ${minutes}분")
        return source.enabled
    }

    suspend fun updateRunStatus(id: String, status: String, error: String?) {
        db.crawlSourceDao().updateRunStatus(id, System.currentTimeMillis(), status, error)
    }

    suspend fun markRunning(id: String) {
        db.crawlSourceDao().updateRunStatus(id, System.currentTimeMillis(), Constants.STATUS_RUNNING, null)
    }

    suspend fun logResult(
        sourceId: String,
        sourceName: String,
        startedAt: Long,
        status: String,
        found: Int,
        created: Int,
        updated: Int,
        error: String?,
    ) {
        db.crawlLogDao().insert(
            CrawlLog(
                sourceId = sourceId,
                sourceName = sourceName,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                status = status,
                plansFound = found,
                plansNew = created,
                plansUpdated = updated,
                errorMessage = error,
            ),
        )
        updateRunStatus(sourceId, status, error)
    }

    suspend fun getRecentLogs(limit: Int = 50): List<RecentLog> =
        db.crawlLogDao().getRecent(limit).map { it.toRecent() }

    /** 연속 실패 판정용: 최신순 상태 목록 */
    suspend fun getRecentStatuses(sourceId: String, limit: Int): List<String> =
        db.crawlLogDao().getRecentBySource(sourceId, limit).map { it.status }

    suspend fun saveBrands(brands: List<CarrierBrand>) {
        db.carrierBrandDao().upsertAll(brands)
        DebugLogger.i("브랜드", "통신사 브랜드 저장 count=${brands.size}")
    }

    fun observeBrands(): Flow<List<BrandInfo>> =
        db.carrierBrandDao().observeActive().map { list -> list.map { it.toInfo() } }

    suspend fun countBrands(): Int = db.carrierBrandDao().countActive()
}
