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
import com.borasarang.planjupjup.data.db.migration.MIGRATION_1_2
import com.borasarang.planjupjup.data.db.migration.MIGRATION_2_3
import com.borasarang.planjupjup.data.db.migration.MIGRATION_3_4
import com.borasarang.planjupjup.data.db.migration.MIGRATION_4_5
import com.borasarang.planjupjup.data.db.migration.MIGRATION_5_6

@Database(
    entities = [Plan::class, PlanSourceMapping::class, CrawlSource::class, CarrierBrand::class, CrawlLog::class, NotificationLog::class],
    version = 6,
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
        const val DB_NAME = "planjupjup.db"

        @Volatile
        private var instance: PlanDatabase? = null

        /** 단일 진입점 (R1: 이중 생성 레이스 제거 + 폴백 데드코드 수정) */
        fun getInstance(context: Context, allowDestructive: Boolean = false): PlanDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context, allowDestructive).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context, allowDestructive: Boolean): PlanDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                PlanDatabase::class.java,
                DB_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            if (allowDestructive) builder.fallbackToDestructiveMigration(true)
            return builder.build()
        }

        /** 마이그레이션 실패 시 최후 수단 (데이터 손실 감수, 앱 벽돌 방지).
         *  호출 전 원본 백업 권장. 하위 호환 유지용 별칭. */
        fun getInstanceFallback(context: Context): PlanDatabase {
            resetInstance()
            return getInstance(context, allowDestructive = true)
        }

        /** 테스트·복구용 인스턴스 초기화 */
        @Synchronized
        fun resetInstance() {
            try {
                instance?.close()
            } catch (e: Exception) {
                // R6: 복구 경로 close 실패 기록 (무시하고 초기화 계속)
                com.borasarang.planjupjup.util.DebugLogger.w("복구", "DB close 실패: ${e.message}")
            }
            instance = null
        }
    }
}
