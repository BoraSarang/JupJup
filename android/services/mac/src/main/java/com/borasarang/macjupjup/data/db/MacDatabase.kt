package com.borasarang.macjupjup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.borasarang.macjupjup.data.db.dao.AppDao
import com.borasarang.macjupjup.data.db.dao.AppSourceMappingDao
import com.borasarang.macjupjup.data.db.dao.CommunityPostDao
import com.borasarang.macjupjup.data.db.dao.CrawlLogDao
import com.borasarang.macjupjup.data.db.dao.CrawlSourceDao
import com.borasarang.macjupjup.data.db.dao.NewsArticleDao
import com.borasarang.macjupjup.data.db.dao.NotificationLogDao
import com.borasarang.macjupjup.data.db.dao.VersionHistoryDao
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping
import com.borasarang.macjupjup.data.db.entity.CommunityPost
import com.borasarang.macjupjup.data.db.entity.CrawlLog
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import com.borasarang.macjupjup.data.db.entity.NotificationLog
import com.borasarang.macjupjup.data.db.entity.VersionHistory
import com.borasarang.macjupjup.data.db.migration.MIGRATION_10_11
import com.borasarang.macjupjup.data.db.migration.MIGRATION_1_2
import com.borasarang.macjupjup.data.db.migration.MIGRATION_2_3
import com.borasarang.macjupjup.data.db.migration.MIGRATION_3_4
import com.borasarang.macjupjup.data.db.migration.MIGRATION_4_5
import com.borasarang.macjupjup.data.db.migration.MIGRATION_5_6
import com.borasarang.macjupjup.data.db.migration.MIGRATION_6_7
import com.borasarang.macjupjup.data.db.migration.MIGRATION_7_8
import com.borasarang.macjupjup.data.db.migration.MIGRATION_8_9
import com.borasarang.macjupjup.data.db.migration.MIGRATION_9_10
import com.borasarang.macjupjup.util.DebugLogger

@Database(
    entities = [
        App::class, AppSourceMapping::class, CrawlSource::class, VersionHistory::class,
        CrawlLog::class, NotificationLog::class, NewsArticle::class, NewsAppRelation::class,
        CommunityPost::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class MacDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun appSourceMappingDao(): AppSourceMappingDao
    abstract fun crawlSourceDao(): CrawlSourceDao
    abstract fun versionHistoryDao(): VersionHistoryDao
    abstract fun crawlLogDao(): CrawlLogDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun communityPostDao(): CommunityPostDao

    companion object {
        const val DB_NAME = "macjupjup.db"

        @Volatile
        private var instance: MacDatabase? = null

        /** 단일 진입점 (P0-7: 이중 생성 레이스 제거) */
        fun getInstance(context: Context, allowDestructive: Boolean = false): MacDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context, allowDestructive).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context, allowDestructive: Boolean): MacDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                MacDatabase::class.java,
                DB_NAME,
            ).addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11,
            )
            if (allowDestructive) builder.fallbackToDestructiveMigration(true)
            return builder.build()
        }

        /** 마이그레이션 실패 등 DB 열기 불가 → 재생성 폴백 (앱 벽돌 방지).
         *  호출 전 원본 백업 권장. 하위 호환 유지용 별칭. */
        fun getInstanceFallback(context: Context): MacDatabase {
            return getInstance(context, allowDestructive = true)
        }

        /** 테스트·복구용 인스턴스 초기화 */
        @Synchronized
        fun resetInstance() {
            try {
                instance?.close()
            } catch (e: Exception) {
                // R6: 복구 경로 close 실패 기록 (무시하고 초기화 계속)
                com.borasarang.macjupjup.util.DebugLogger.w("복구", "DB close 실패: ${e.message}")
            }
            instance = null
        }
    }
}
