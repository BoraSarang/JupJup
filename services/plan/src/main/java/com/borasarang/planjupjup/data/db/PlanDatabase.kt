package com.borasarang.planjupjup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.borasarang.planjupjup.data.db.dao.CarrierBrandDao
import com.borasarang.planjupjup.data.db.dao.CrawlLogDao
import com.borasarang.planjupjup.data.db.dao.CrawlSourceDao
import com.borasarang.planjupjup.data.db.dao.NotificationLogDao
import com.borasarang.planjupjup.data.db.dao.PlanDao
import com.borasarang.planjupjup.data.db.dao.PlanSourceMappingDao
import com.borasarang.planjupjup.data.db.entity.CarrierBrand
import com.borasarang.planjupjup.data.db.entity.CrawlLog
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.data.db.entity.NotificationLog
import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.db.entity.PlanSourceMapping

@Database(
    entities = [Plan::class, PlanSourceMapping::class, CrawlSource::class, CarrierBrand::class, CrawlLog::class, NotificationLog::class],
    version = 4,
    exportSchema = false,
)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun planSourceMappingDao(): PlanSourceMappingDao
    abstract fun crawlSourceDao(): CrawlSourceDao
    abstract fun carrierBrandDao(): CarrierBrandDao
    abstract fun crawlLogDao(): CrawlLogDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var instance: PlanDatabase? = null

        fun getInstance(context: Context): PlanDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanDatabase::class.java,
                    "planjupjup.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false).build().also { instance = it }
            }
        }

        /** 마이그레이션 실패 시 최후 수단 (데이터 손실 감수, 앱 벽돌 방지) */
        fun getInstanceFallback(context: Context): PlanDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanDatabase::class.java,
                    "planjupjup.db",
                ).fallbackToDestructiveMigration(true).build().also { instance = it }
            }
        }
    }
}
