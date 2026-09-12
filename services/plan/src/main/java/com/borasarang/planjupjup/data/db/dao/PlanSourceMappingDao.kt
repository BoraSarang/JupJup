package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping

@Dao
interface PlanSourceMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<PlanSourceMapping>)

    @Query("SELECT * FROM plan_sources WHERE planId = :planId")
    suspend fun getByPlanId(planId: String): List<PlanSourceMapping>

    @Query("DELETE FROM plan_sources WHERE planId NOT IN (SELECT id FROM plans)")
    suspend fun deleteOrphans(): Int
}
