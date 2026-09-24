package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 요금제별 출처 매핑 (N:M). 같은 요금제가 여러 소스에서 수집되면 행이 여러 개 */
@Entity(
    tableName = "plan_sources",
    primaryKeys = ["planId", "sourceName"],
    indices = [Index("planId"), Index("sourceName")],
)
data class PlanSourceMapping(
    val planId: String,
    val sourceName: String,
    val sourceUrl: String,
    val fetchedAt: Long,
    /** 파싱 당시 원문 HTML (재파싱·디버깅용) */
    val rawDataJson: String?,
)
