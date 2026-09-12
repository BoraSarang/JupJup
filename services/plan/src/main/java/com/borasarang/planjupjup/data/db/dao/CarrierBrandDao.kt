package com.borasarang.planjupjup.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.borasarang.planjupjup.data.db.entity.CarrierBrand
import kotlinx.coroutines.flow.Flow

@Dao
interface CarrierBrandDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(brands: List<CarrierBrand>)

    @Query("SELECT * FROM carrier_brands WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<CarrierBrand>>

    @Query("SELECT * FROM carrier_brands WHERE isActive = 1 ORDER BY sortOrder, name")
    suspend fun getActive(): List<CarrierBrand>

    @Query("SELECT COUNT(*) FROM carrier_brands WHERE isActive = 1")
    suspend fun countActive(): Int
}
