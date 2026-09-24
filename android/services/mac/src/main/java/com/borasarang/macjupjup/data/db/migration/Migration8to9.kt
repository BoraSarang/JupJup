package com.borasarang.macjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 → v9: apps 지원언어 컬럼 (CSV, 예: en,ko,ja).
 * 컬럼 추가만. 기존 행은 NULL → 재수집 시 백필, 프론트는 ko 포함 시 한국어 배지 노출.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE apps ADD COLUMN supportedLanguages TEXT")
    }
}
