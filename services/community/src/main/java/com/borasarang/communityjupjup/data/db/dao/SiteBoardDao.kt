package com.borasarang.communityjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.communityjupjup.data.db.entity.SiteBoard

@Dao
interface SiteBoardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(boards: List<SiteBoard>)

    @Query("SELECT * FROM site_boards WHERE sourceId = :sourceId ORDER BY boardName")
    suspend fun getBySource(sourceId: String): List<SiteBoard>

    @Query("SELECT * FROM site_boards WHERE sourceId = :sourceId AND enabled = 1 ORDER BY boardName")
    suspend fun getEnabledBySource(sourceId: String): List<SiteBoard>

    @Query("SELECT * FROM site_boards ORDER BY sourceId, boardName")
    suspend fun getAll(): List<SiteBoard>

    @Query("SELECT * FROM site_boards WHERE id = :id")
    suspend fun getById(id: Long): SiteBoard?

    /** 목록 매핑용 일괄 조회 (R35: 매 요청 getAll 전건 스캔 제거) */
    @Query("SELECT * FROM site_boards WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SiteBoard>

    @Query("DELETE FROM site_boards WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """UPDATE site_boards SET boardName = :name, boardUrl = :url,
        categoryId = :categoryId, enabled = :enabled,
        intervalMinutes = :intervalMinutes WHERE id = :id"""
    )
    suspend fun update(
        id: Long,
        name: String,
        url: String,
        categoryId: Int,
        enabled: Boolean,
        intervalMinutes: Int,
    ): Int

    @Query("DELETE FROM site_boards WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String): Int
}
