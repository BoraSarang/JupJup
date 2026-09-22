package com.borasarang.macjupjup.data.repository

import androidx.room.withTransaction
import com.borasarang.macjupjup.data.db.MacDatabase

/** 소스 상태 조회·토글·수집 결과 기록 */
class SourceRepository(private val db: MacDatabase) {

    suspend fun getById(id: String) = db.crawlSourceDao().getById(id)

    suspend fun list(): List<SourceStatus> {
        return db.crawlSourceDao().getAll().map { it.toStatus() }
    }

    /** 해당 소스 enabled 반전. 성공 시 새 상태 반환, 없음 null */
    suspend fun toggle(id: String): Boolean? {
        val source = db.crawlSourceDao().getById(id) ?: return null
        val next = !source.enabled
        db.crawlSourceDao().setEnabled(id, next)
        return next
    }

    /** 명시적 on/off 설정 */
    suspend fun toggleEnabled(id: String, enabled: Boolean) {
        db.crawlSourceDao().setEnabled(id, enabled)
    }

    /** 수집 주기 변경. 성공 시 true */
    suspend fun setIntervalMinutes(id: String, minutes: Int): Boolean {
        val source = db.crawlSourceDao().getById(id) ?: return false
        if (minutes < 15) return false
        db.crawlSourceDao().updateInterval(id, minutes, minutes / 60)
        return true
    }

    /** 소스 목록 + 최근 로그 묶음 (소스관리 화면용, R5: 행마다 조회 → IN 배치 1회) */
    suspend fun getListItems(): List<SourceListItem> {
        val sources = db.crawlSourceDao().getAll()
        if (sources.isEmpty()) return emptyList()
        val since = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        val latestBySource = db.crawlLogDao().getRecentBySourceIds(sources.map { it.id }, since)
            .groupBy { it.sourceId }
            .mapValues { (_, logs) -> logs.maxBy { it.startedAt } }
        return sources.map { s ->
            val latest = latestBySource[s.id]?.toRecent()
            SourceListItem(status = s.toStatus(), latestLog = latest)
        }
    }

    /** 최근 N개 수집 상태 (실패 연속 판정용) */
    suspend fun getRecentStatuses(sourceId: String, limit: Int): List<String> {
        return db.crawlLogDao().recentBySource(sourceId, limit).map { it.status }
    }

    suspend fun markRunStart(id: String) {
        markRunning(id)
    }

    suspend fun markRunning(id: String) {
        db.crawlSourceDao().updateRun(id, System.currentTimeMillis(), "RUNNING", null)
    }

    suspend fun markRunEnd(id: String, success: Boolean, error: String? = null) {
        val status = if (success) "SUCCESS" else "FAILED"
        db.crawlSourceDao().updateRun(id, System.currentTimeMillis(), status, error)
    }

    /** 수집 결과 기록: crawl_logs insert + 소스 상태 갱신 (R5: 원자화) */
    suspend fun logResult(
        sourceId: String,
        sourceName: String,
        startedAt: Long,
        status: String,
        found: Int,
        created: Int,
        updated: Int,
        error: String?,
        rxBytes: Long = 0L,
        txBytes: Long = 0L,
    ) {
        db.withTransaction {
            db.crawlLogDao().insert(
                com.borasarang.macjupjup.data.db.entity.CrawlLog(
                    sourceId = sourceId,
                    sourceName = sourceName,
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis(),
                    status = status,
                    plansFound = found,
                    plansNew = created,
                    plansUpdated = updated,
                    errorMessage = error,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                )
            )
            markRunEnd(sourceId, status == "SUCCESS", error)
        }
    }

    suspend fun recentLogs(limit: Int = 50) =
        db.crawlLogDao().recent(limit).map { it.toRecent() }

    /** 수집처 완전 제거 (행 삭제). 반환 = 삭제 행 수 */
    suspend fun removeSource(id: String): Int = db.crawlSourceDao().deleteById(id)
}
