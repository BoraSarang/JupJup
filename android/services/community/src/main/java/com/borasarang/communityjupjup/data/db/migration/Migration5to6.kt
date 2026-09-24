package com.borasarang.communityjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: 목록 필터 경로 복합 인덱스 (R35).
 * Room 검증과 일치시키기 위해 자동 생성명(index_posts_*) 그대로 사용.
 * collectedAt 단일 인덱스는 엔티티 선언済·신규 설치 시 자동 생성되므로 추가 불필요.
 * LIKE 전방 와일드카드 자체는 인덱스로 개선 불가라 유지.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_categoryId_sourceId` ON `posts` (`categoryId`, `sourceId`)")
    }
}
