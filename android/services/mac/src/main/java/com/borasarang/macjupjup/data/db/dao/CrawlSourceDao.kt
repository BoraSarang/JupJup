package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.macjupjup.data.db.entity.CrawlSource

@Dao
interface CrawlSourceDao {
    @Query("SELECT * FROM sources ORDER BY name")
    suspend fun getAll(): List<CrawlSource>

    @Query("SELECT * FROM sources WHERE enabled = 1")
    suspend fun getEnabled(): List<CrawlSource>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: String): CrawlSource?

    /** 대시보드 overview용 — 전건 로딩 후 MAX 대신 SQL 집계 */
    @Query("SELECT MAX(lastRunAt) FROM sources")
    suspend fun maxLastRunAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sources: List<CrawlSource>)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE sources SET intervalMinutes = :minutes, intervalHours = :hours WHERE id = :id")
    suspend fun updateInterval(id: String, minutes: Int, hours: Int)

    @Query("UPDATE sources SET lastRunAt = :runAt, lastStatus = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateRun(id: String, runAt: Long, status: String, error: String?)

    /** 제거된 수집처 행 삭제 (Setapp 완전 제거용). 반환 = 삭제 행 수 */
    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
