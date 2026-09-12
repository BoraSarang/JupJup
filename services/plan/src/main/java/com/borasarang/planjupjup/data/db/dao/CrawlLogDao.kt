package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.CrawlLog

@Dao
interface CrawlLogDao {
    @Insert
    suspend fun insert(log: CrawlLog): Long

    @Query("SELECT * FROM crawl_logs ORDER BY id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CrawlLog>

    @Query("SELECT * FROM crawl_logs WHERE sourceId = :sourceId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestBySource(sourceId: String): CrawlLog?

    @Query("SELECT * FROM crawl_logs WHERE sourceId = :sourceId ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentBySource(sourceId: String, limit: Int): List<CrawlLog>

    @Query(
        """DELETE FROM crawl_logs WHERE id NOT IN
           (SELECT id FROM crawl_logs ORDER BY id DESC LIMIT :keep)""",
    )
    suspend fun prune(keep: Int): Int

    /** 통계 추이용: 지정 시각 이후 로그의 시계열 프로젝션 */
    @Query(
        """SELECT startedAt, finishedAt, status, plansFound, plansNew, plansUpdated
           FROM crawl_logs WHERE startedAt >= :since ORDER BY startedAt""",
    )
    suspend fun getLogsSince(since: Long): List<CrawlLogStatsRow>
}

/** 통계 추이용 수집 로그 프로젝션 */
data class CrawlLogStatsRow(
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    val plansFound: Int,
    val plansNew: Int,
    val plansUpdated: Int,
)
