package com.borasarang.communityjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borasarang.communityjupjup.data.db.entity.CrawlLog

@Dao
interface CrawlLogDao {
    @Insert
    suspend fun insert(log: CrawlLog): Long

    @Query("UPDATE crawl_logs SET finishedAt = :finishedAt, status = :status, plansFound = :found, plansNew = :newCount, plansUpdated = :updated, errorMessage = :error WHERE id = :id")
    suspend fun finish(id: Long, finishedAt: Long, status: String, found: Int, newCount: Int, updated: Int, error: String?)

    @Query("SELECT * FROM crawl_logs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CrawlLog>

    @Query("SELECT * FROM crawl_logs WHERE sourceId = :sourceId ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestBySource(sourceId: String): CrawlLog?

    @Query("SELECT * FROM crawl_logs WHERE sourceId = :sourceId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentBySource(sourceId: String, limit: Int): List<CrawlLog>

    /** 소스관리 N+1 제거용 배치 조회 (R5, 윈도우 상한) */
    @Query("SELECT * FROM crawl_logs WHERE sourceId IN (:sourceIds) AND startedAt >= :since ORDER BY startedAt DESC")
    suspend fun getRecentBySourceIds(sourceIds: List<String>, since: Long): List<CrawlLog>

    @Query("DELETE FROM crawl_logs WHERE startedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    /** 제거된 수집처 로그 일괄 삭제. 반환 = 삭제 행 수 */
    @Query("DELETE FROM crawl_logs WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String): Int

    /** 일별·소스별 수집량 집계 (로컬 타임존 날짜). 통계 그래프용 */
    @Query(
        """SELECT date(startedAt/1000,'unixepoch','localtime') AS day, sourceId, sourceName,
        SUM(plansFound) AS found, SUM(plansNew) AS newCount, SUM(plansUpdated) AS updated,
        COUNT(*) AS runs, SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
        SUM(rxBytes) AS rxBytes, SUM(txBytes) AS txBytes
        FROM crawl_logs WHERE startedAt >= :since GROUP BY day, sourceId ORDER BY day ASC"""
    )
    suspend fun collectByDay(since: Long): List<DaySourceCollect>
}

/** 일별·소스별 수집 집계 행 (SUM은 Long으로 수신) */
data class DaySourceCollect(
    val day: String,
    val sourceId: String,
    val sourceName: String,
    val found: Long,
    val newCount: Long,
    val updated: Long,
    val runs: Long,
    val failed: Long,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
)
