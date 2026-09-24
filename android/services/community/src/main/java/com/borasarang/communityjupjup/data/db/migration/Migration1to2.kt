package com.borasarang.communityjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: canonicalUrl 중복 방지 컬럼 + UNIQUE 인덱스.
 * - 기존 행은 path-only로 백필 (클리앙·루리웹 식별자가 path에 있음)
 * - 중복은 최초 수집 행만 유지
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE posts ADD COLUMN canonicalUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """UPDATE posts SET canonicalUrl =
            CASE WHEN instr(originalUrl, '?') > 0
            THEN substr(originalUrl, 1, instr(originalUrl, '?') - 1)
            ELSE originalUrl END"""
        )
        db.execSQL(
            "DELETE FROM posts WHERE id NOT IN (SELECT MIN(id) FROM posts GROUP BY canonicalUrl)"
        )
        db.execSQL("CREATE UNIQUE INDEX index_posts_canonicalUrl ON posts(canonicalUrl)")
    }
}
