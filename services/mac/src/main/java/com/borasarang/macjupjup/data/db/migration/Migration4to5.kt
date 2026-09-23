package com.borasarang.macjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: 뉴스 테이블 추가 (R32 PLAN_v17, 데이터 보존).
 * Room 자동 생성명과 일치해야 함: index_<table>_<columns>.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `news_articles` (
            `id` TEXT NOT NULL,
            `sourceId` TEXT NOT NULL,
            `sourceName` TEXT NOT NULL,
            `main` TEXT NOT NULL,
            `sub` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `summary` TEXT,
            `contentHtml` TEXT,
            `originalUrl` TEXT NOT NULL,
            `thumbnailUrl` TEXT,
            `publishedAt` INTEGER NOT NULL,
            `collectedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`))""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `news_app_relation` (
            `newsId` TEXT NOT NULL,
            `appId` TEXT NOT NULL,
            PRIMARY KEY(`newsId`, `appId`))""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_news_articles_originalUrl` ON `news_articles` (`originalUrl`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_main_sub_publishedAt` ON `news_articles` (`main`, `sub`, `publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_articles_publishedAt` ON `news_articles` (`publishedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_app_relation_appId` ON `news_app_relation` (`appId`)")
    }
}
