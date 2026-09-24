package com.borasarang.macjupjup.data.db.entity

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
    /** GITHUB_SEARCH / GITHUB_RELEASES / CHART_RSS / ITUNES_LOOKUP / NAME_MATCH / MAS_DISCOVERY */
    val type: String,
    val baseUrl: String,
    val enabled: Boolean,
    val intervalHours: Int,
    /** 수집 주기(분). 스케줄 기준값 (60/360/720/1440/10080/43200) */
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    /** NEVER_RUN / SUCCESS / FAILED / RUNNING */
    val lastStatus: String,
    val errorMessage: String?,
    /** 소스별 설정 JSON (GitHub 쿼리·PH 토픽 등 외부화) */
    val selectorConfigJson: String?,
)
