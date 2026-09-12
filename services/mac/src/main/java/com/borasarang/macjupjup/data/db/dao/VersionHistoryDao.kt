package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.macjupjup.data.db.entity.VersionHistory

@Dao
interface VersionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: VersionHistory): Long

    /** 배치 저장용 일괄 삽입 (P1-1) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<VersionHistory>): List<Long>

    /** 배치 저장용 최신 이력 일괄 조회 (P1-1, 호출 측에서 앱별 최신 선택) */
    @Query("SELECT * FROM version_history WHERE appId IN (:appIds) ORDER BY detectedAt DESC")
    suspend fun getByApps(appIds: List<String>): List<VersionHistory>

    @Query("SELECT * FROM version_history WHERE appId = :appId ORDER BY detectedAt DESC")
    suspend fun getByApp(appId: String): List<VersionHistory>

    @Query("SELECT * FROM version_history WHERE appId = :appId ORDER BY detectedAt DESC LIMIT 1")
    suspend fun getLatest(appId: String): VersionHistory?

    @Query("DELETE FROM version_history WHERE detectedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    /** 앱 삭제 시 버전 이력 동반 삭제 (고아 방지, P0-4/5) */
    @Query("DELETE FROM version_history WHERE appId = :appId")
    suspend fun deleteByApp(appId: String): Int
}
