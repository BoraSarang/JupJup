package com.borasarang.communityjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4: site_boards.intervalMinutes (보드별 수집 주기, 기본 30).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE site_boards ADD COLUMN intervalMinutes INTEGER NOT NULL DEFAULT 30")
    }
}
