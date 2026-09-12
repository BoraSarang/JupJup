package com.borasarang.macjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4: 조회 인덱스 추가 (마이그레이션, 데이터 보존).
 * Room 자동 생성명과 일치해야 함: index_<table>_<columns>.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_tags` ON `apps` (`tags`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_firstSeenAt` ON `apps` (`firstSeenAt`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_apps_license_category_lastUpdatedAt`" +
                " ON `apps` (`license`, `category`, `lastUpdatedAt`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_sources_sourceName` ON `app_sources` (`sourceName`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_crawl_logs_startedAt_sourceId`" +
                " ON `crawl_logs` (`startedAt`, `sourceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_logs_type_isRead_createdAt`" +
                " ON `notification_logs` (`type`, `isRead`, `createdAt`)",
        )
    }
}
