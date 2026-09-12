package com.borasarang.planjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: 최초 수집 시각 컬럼 추가 + 기존 행 백필 (firstCollectedAt = collectedAt).
 * S22 실기기 v1 DB(432건) 보존이 목적 — destructive 금지.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plans ADD COLUMN firstCollectedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE plans SET firstCollectedAt = collectedAt WHERE firstCollectedAt = 0")
    }
}
