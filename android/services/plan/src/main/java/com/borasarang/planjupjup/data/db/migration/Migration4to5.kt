package com.borasarang.planjupjup.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5: plans 조회 인덱스 2종 (R5)
 * - isNew + firstCollectedAt: 신규 조회(getNewSince) 범위+정렬 커버
 * - networkType + mvnoNetwork + price: 목록 필터(getFiltered) 커버
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 이름은 Room 자동 생성 규칙 (index_<table>_<col>...) — 불일치 시 검증 실패
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plans_isNew_firstCollectedAt ON plans(isNew, firstCollectedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_plans_networkType_mvnoNetwork_price ON plans(networkType, mvnoNetwork, price)")
    }
}
