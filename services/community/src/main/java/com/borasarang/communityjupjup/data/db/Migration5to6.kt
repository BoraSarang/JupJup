package com.borasarang.communityjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: 목록 필터·TTL 정리 경로 인덱스 (R35).
 * LIKE 전방 와일드카드 자체는 인덱스로 개선 불가라 유지 —
 * categoryId·sourceId 복합 조건과 collectedAt 범위 스캔만 절삭.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_posts_cat_src ON posts(categoryId, sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_posts_collected ON posts(collectedAt)")
    }
}
