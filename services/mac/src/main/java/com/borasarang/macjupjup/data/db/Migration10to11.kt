package com.borasarang.macjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v10 → v11: 앱 전문 컬럼 분리 (본문 전체화 풀패치).
 * 기존 descriptionSnippet(발췌·본문 혼재) → 짧은 소개 유지.
 * longDescription: README·스토어 전문. 데이터는 재수집으로 채움(마이그레이션 데이터 이관 없음).
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `apps` ADD COLUMN `longDescription` TEXT")
        db.execSQL("ALTER TABLE `apps` ADD COLUMN `longDescriptionKo` TEXT")
    }
}
