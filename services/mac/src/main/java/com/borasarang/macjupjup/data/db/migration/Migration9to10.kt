package com.borasarang.macjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v9 → v10: community_posts 신규 테이블 (PLAN_v23 커뮤니티 메뉴).
 * community(3040) 서비스 posts와 별개 — 맥 포털 전용.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `community_posts` (
                `id` TEXT NOT NULL,
                `sourceId` TEXT NOT NULL,
                `sourceName` TEXT NOT NULL,
                `main` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `summary` TEXT,
                `authorName` TEXT,
                `originalUrl` TEXT NOT NULL,
                `thumbnailUrl` TEXT,
                `commentCount` INTEGER,
                `viewCount` INTEGER,
                `contentHtml` TEXT,
                `publishedAt` INTEGER NOT NULL,
                `collectedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )""",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_community_posts_originalUrl` " +
                "ON `community_posts` (`originalUrl`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_community_posts_main_publishedAt` " +
                "ON `community_posts` (`main`, `publishedAt`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_community_posts_main_sourceId` " +
                "ON `community_posts` (`main`, `sourceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_community_posts_publishedAt` " +
                "ON `community_posts` (`publishedAt`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_community_posts_collectedAt` " +
                "ON `community_posts` (`collectedAt`)",
        )
    }
}
