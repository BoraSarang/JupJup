package com.borasarang.communityjupjup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.borasarang.communityjupjup.data.db.dao.CommunityPostDao
import com.borasarang.communityjupjup.data.db.dao.CrawlLogDao
import com.borasarang.communityjupjup.data.db.dao.CrawlSourceDao
import com.borasarang.communityjupjup.data.db.dao.NotificationLogDao
import com.borasarang.communityjupjup.data.db.dao.SiteBoardDao
import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.data.db.entity.CrawlLog
import com.borasarang.communityjupjup.data.db.entity.CrawlSource
import com.borasarang.communityjupjup.data.db.entity.NotificationLog
import com.borasarang.communityjupjup.data.db.entity.SiteBoard
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_1_2
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_2_3
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_3_4
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_4_5
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_5_6
import com.borasarang.communityjupjup.data.db.migration.MIGRATION_6_7

@Database(
    entities = [CommunityPost::class, SiteBoard::class, CrawlSource::class, CrawlLog::class, NotificationLog::class],
    version = 7,
    exportSchema = false,
)
abstract class CommunityDatabase : RoomDatabase() {
    abstract fun postDao(): CommunityPostDao
    abstract fun siteBoardDao(): SiteBoardDao
    abstract fun crawlSourceDao(): CrawlSourceDao
    abstract fun crawlLogDao(): CrawlLogDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        const val DB_NAME = "communityjupjup.db"

        @Volatile
        private var instance: CommunityDatabase? = null

        /** 단일 진입점 (P0-7: 이중 생성 레이스 제거) */
        fun getInstance(context: Context, allowDestructive: Boolean = false): CommunityDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context, allowDestructive).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context, allowDestructive: Boolean): CommunityDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                CommunityDatabase::class.java,
                DB_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            if (allowDestructive) builder.fallbackToDestructiveMigration(true)
            return builder.build()
        }

        /** 마이그레이션 실패 등 DB 열기 불가 → 재생성 폴백 (앱 벽돌 방지) */
        fun getInstanceFallback(context: Context): CommunityDatabase {
            return getInstance(context, allowDestructive = true)
        }

        /** 테스트·복구용 인스턴스 초기화 */
        @Synchronized
        fun resetInstance() {
            try {
                instance?.close()
            } catch (e: Exception) {
                com.borasarang.communityjupjup.util.DebugLogger.w("복구", "DB close 실패: ${e.message}")
            }
            instance = null
        }
    }
}
