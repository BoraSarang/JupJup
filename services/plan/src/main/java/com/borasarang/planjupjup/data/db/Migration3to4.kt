package com.borasarang.planjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4: 알림 로그 테이블 추가
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE notification_logs (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                summary TEXT NOT NULL,
                detailJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                isRead INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX idx_notification_type ON notification_logs(type)")
        db.execSQL("CREATE INDEX idx_notification_created ON notification_logs(createdAt)")
        db.execSQL("CREATE INDEX idx_notification_read ON notification_logs(isRead)")
    }
}