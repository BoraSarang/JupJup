package com.borasarang.communityjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: site_boards.enabled (게시판별 수집 on/off, 기본 true).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE site_boards ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
    }
}
