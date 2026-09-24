package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 앱-출처 매핑. 동일 앱 다출처 수집 시 전수 기록 */
@Entity(
    tableName = "app_sources",
    primaryKeys = ["appId", "sourceName"],
    indices = [Index("appId"), Index("sourceName")],
)
data class AppSourceMapping(
    val appId: String,
    val sourceName: String,
    val sourceUrl: String?,
    val fetchedAt: Long,
)
