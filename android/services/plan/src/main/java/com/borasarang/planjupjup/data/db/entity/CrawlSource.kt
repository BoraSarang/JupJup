package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 수집 소스 설정. type/status는 String 상수(Constants) 사용 */
@Entity(
    tableName = "sources",
    indices = [Index("type"), Index("enabled")],
)
data class CrawlSource(
    @PrimaryKey val id: String,
    val name: String,
    /** COMPARE_SITE / CARRIER_SITE / BRAND_LIST */
    val type: String,
    val baseUrl: String,
    val enabled: Boolean,
    val intervalHours: Int,
    /** 수집 주기(분). 스케줄 기준값 (30/60/360/720/1440/10080) */
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    /** NEVER_RUN / SUCCESS / FAILED / RUNNING */
    val lastStatus: String,
    val errorMessage: String?,
    /** 사이트별 CSS 셀렉터 설정 JSON (외부화) */
    val selectorConfigJson: String?,
)
