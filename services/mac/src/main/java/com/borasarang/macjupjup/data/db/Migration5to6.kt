package com.borasarang.macjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: 뉴스 한글 제목·요약 컬럼 (R41, TranslateWorker).
 * 컬럼 추가만이라 인덱스명 규칙과 무관. 기존 행은 NULL → 워커가 순차 번역.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE news_articles ADD COLUMN titleKo TEXT")
        db.execSQL("ALTER TABLE news_articles ADD COLUMN summaryKo TEXT")
    }
}
