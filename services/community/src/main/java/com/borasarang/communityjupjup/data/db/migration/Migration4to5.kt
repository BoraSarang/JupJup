package com.borasarang.communityjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: posts.imageUrls (본문 이미지 목록 JSON 배열).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE posts ADD COLUMN imageUrls TEXT NOT NULL DEFAULT '[]'")
    }
}
