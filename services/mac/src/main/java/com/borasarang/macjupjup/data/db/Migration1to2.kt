package com.borasarang.macjupjup.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: 정보 보강 컬럼 11종 추가 (아이콘·전체설명·한글·스토어 메타·GitHub 신호).
 * 전부 nullable → 기존 행 영향 없음.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE apps ADD COLUMN iconUrl TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN descriptionKo TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN releaseNotes TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN releaseNotesKo TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN sellerName TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN fileSize INTEGER")
        db.execSQL("ALTER TABLE apps ADD COLUMN minOs TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN contentRating TEXT")
        db.execSQL("ALTER TABLE apps ADD COLUMN forks INTEGER")
        db.execSQL("ALTER TABLE apps ADD COLUMN issues INTEGER")
        db.execSQL("ALTER TABLE apps ADD COLUMN licenseName TEXT")
    }
}

/**
 * v2 → v3 (T-080): 버전별 링크. version_history.sourceUrl 추가.
 * GitHub bump → 릴리즈 페이지, Lookup bump → 스토어 현재 페이지. nullable → 기존 행 영향 없음.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE version_history ADD COLUMN sourceUrl TEXT")
    }
}
