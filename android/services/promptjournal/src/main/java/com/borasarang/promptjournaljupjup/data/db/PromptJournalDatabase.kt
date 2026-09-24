package com.borasarang.promptjournaljupjup.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.borasarang.promptjournaljupjup.data.db.dao.PromptDao
import com.borasarang.promptjournaljupjup.data.db.dao.PromptExecutionDao
import com.borasarang.promptjournaljupjup.data.db.entity.Prompt
import com.borasarang.promptjournaljupjup.data.db.entity.PromptExecution

@Database(
    entities = [Prompt::class, PromptExecution::class],
    version = 2,
    exportSchema = false,
)
abstract class PromptJournalDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun promptExecutionDao(): PromptExecutionDao

    companion object {
        const val DB_NAME = "promptjournal.db"

        /** v1 → v2: Prompt 테이블 생성, prompt_executions에 promptId 컬럼 추가. 기존 데이터는 초기화 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `prompts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `scheduleType` TEXT NOT NULL,
                        `scheduleValue` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `usePreviousResult` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("ALTER TABLE `prompt_executions` ADD COLUMN `promptId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM `prompt_executions`")
            }
        }

        @Volatile
        private var instance: PromptJournalDatabase? = null

        fun getInstance(context: Context, allowDestructive: Boolean = false): PromptJournalDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context, allowDestructive).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context, allowDestructive: Boolean): PromptJournalDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                PromptJournalDatabase::class.java,
                DB_NAME,
            )
            builder.addMigrations(MIGRATION_1_2)
            if (allowDestructive) builder.fallbackToDestructiveMigration(true)
            return builder.build()
        }

        fun getInstanceFallback(context: Context): PromptJournalDatabase {
            return getInstance(context, allowDestructive = true)
        }

        @Synchronized
        fun resetInstance() {
            try {
                instance?.close()
            } catch (e: Exception) {
                com.borasarang.promptjournaljupjup.util.DebugLogger.w("복구", "DB close 실패: ${e.message}")
            }
            instance = null
        }
    }
}