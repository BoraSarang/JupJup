package com.borasarang.macjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping

@Dao
interface AppSourceMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<AppSourceMapping>)

    @Query("SELECT * FROM app_sources WHERE appId = :appId")
    suspend fun getByApp(appId: String): List<AppSourceMapping>

    /** 목록 배치용 일괄 조회 (N+1 제거, P1-1) */
    @Query("SELECT * FROM app_sources WHERE appId IN (:appIds)")
    suspend fun getByApps(appIds: List<String>): List<AppSourceMapping>

    /** 제거된 수집처 매핑 일괄 삭제. 반환 = 삭제 행 수 */
    @Query("DELETE FROM app_sources WHERE sourceName = :sourceName")
    suspend fun deleteBySourceName(sourceName: String): Int

    /** 앱 삭제 시 매핑 동반 삭제 (댕글링 방지, P0-5) */
    @Query("DELETE FROM app_sources WHERE appId = :appId")
    suspend fun deleteByApp(appId: String): Int
}
