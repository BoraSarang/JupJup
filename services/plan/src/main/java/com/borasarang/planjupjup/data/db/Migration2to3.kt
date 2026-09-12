package com.borasarang.planjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: 분 단위 수집 주기 컬럼 추가 + 기존 시간 단위에서 변환.
 * intervalHours 컬럼은 유지(하위 호환)하되 코드 기준값은 intervalMinutes.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sources ADD COLUMN intervalMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE sources SET intervalMinutes = intervalHours * 60 WHERE intervalMinutes = 0")
    }
}
