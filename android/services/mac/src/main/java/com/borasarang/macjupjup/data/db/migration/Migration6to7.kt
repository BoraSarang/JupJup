package com.borasarang.macjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v6 → v7: crawl_logs 네트워크 바이트 컬럼 (R42b, PLAN_v18).
 * 컬럼 추가만. 기존 행은 0 → 이후 수집분부터 기록.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE crawl_logs ADD COLUMN rxBytes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE crawl_logs ADD COLUMN txBytes INTEGER NOT NULL DEFAULT 0")
    }
}
