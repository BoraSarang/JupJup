package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.Plan

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(plans: List<Plan>)

    @Query(
        """SELECT * FROM plans
        WHERE (:network IS NULL OR networkType = :network)
          AND (:carrier IS NULL OR mvnoNetwork = :carrier)
          AND (:tag IS NULL OR tags LIKE '%' || :tag || '%')
          AND (:maxPrice IS NULL OR price <= :maxPrice)
          AND (:onlyNew = 0 OR isNew = 1)""",
    )
    suspend fun getFiltered(
        network: String?,
        carrier: String?,
        tag: String?,
        maxPrice: Int?,
        onlyNew: Int = 0,
    ): List<Plan>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: String): Plan?

    @Query("SELECT * FROM plans WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Plan>

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun countAll(): Int

    @Query("SELECT MAX(collectedAt) FROM plans")
    suspend fun getLatestCollectedAt(): Long?

    @Query("DELETE FROM plans WHERE collectedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("UPDATE plans SET isNew = 1 WHERE id IN (:ids)")
    suspend fun markNew(ids: List<String>)

    @Query("UPDATE plans SET isNew = 0 WHERE isNew = 1 AND collectedAt < :cutoff")
    suspend fun clearStaleNewFlags(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM plans WHERE collectedAt >= :cutoff")
    suspend fun countCollectedSince(cutoff: Long): Int

    @Query(
        """SELECT * FROM plans
        WHERE isNew = 1 AND firstCollectedAt >= :cutoff
        ORDER BY firstCollectedAt DESC LIMIT :limit""",
    )
    suspend fun getNewSince(cutoff: Long, limit: Int): List<Plan>

    /** 통계 집계 전용 프로젝션 — 파싱 대상 컬럼만 조회 (3천건 규모 수 ms) */
    @Query(
        """SELECT id, carrierName, mvnoNetwork, planName, price, dataAmount, voice, sms,
                  networkType, firstCollectedAt, isNew, tags
           FROM plans""",
    )
    suspend fun getStatsProjection(): List<PlanStatsRow>
}

/** 통계 집계용 요금제 프로젝션. entity 복제가 아닌 read-only 경량 뷰 */
data class PlanStatsRow(
    val id: String,
    val carrierName: String,
    val mvnoNetwork: String,
    val planName: String,
    val price: Int,
    val dataAmount: String,
    val voice: String,
    val sms: String,
    val networkType: String,
    val firstCollectedAt: Long,
    val isNew: Boolean,
    val tags: String?,
)
