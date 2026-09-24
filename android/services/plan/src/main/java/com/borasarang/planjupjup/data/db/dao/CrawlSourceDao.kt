package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import kotlinx.coroutines.flow.Flow

@Dao
interface CrawlSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sources: List<CrawlSource>)

    @Query("SELECT * FROM sources ORDER BY type, name")
    fun observeAll(): Flow<List<CrawlSource>>

    @Query("SELECT * FROM sources ORDER BY type, name")
    suspend fun getAll(): List<CrawlSource>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY type, name")
    suspend fun getEnabled(): List<CrawlSource>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: String): CrawlSource?

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE sources SET intervalMinutes = :minutes WHERE id = :id")
    suspend fun updateInterval(id: String, minutes: Int)

    @Query(
        """UPDATE sources SET lastRunAt = :lastRunAt, lastStatus = :status,
           errorMessage = :error WHERE id = :id""",
    )
    suspend fun updateRunStatus(id: String, lastRunAt: Long, status: String, error: String?)

    @Query("SELECT COUNT(*) FROM sources WHERE enabled = 1")
    suspend fun countEnabled(): Int
}
