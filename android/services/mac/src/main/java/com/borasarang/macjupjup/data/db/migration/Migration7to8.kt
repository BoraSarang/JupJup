package com.borasarang.macjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8: news_articles 인기 태그 컬럼 (R50, PLAN_v20).
 * 컬럼 추가만. 기존 행은 NULL → 상세 접근 시 백필, 프론트는 서브태그 폴백.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE news_articles ADD COLUMN tags TEXT")
    }
}
