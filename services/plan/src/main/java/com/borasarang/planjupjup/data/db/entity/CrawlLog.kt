package com.borasarang.planjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 수집 이력/에러 로그. 디버그 로그 화면과 /api 소스 상태의 근거 */
@Entity(
    tableName = "crawl_logs",
    indices = [Index("sourceId"), Index("startedAt")],
)
data class CrawlLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val sourceName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    /** SUCCESS / FAILED */
    val status: String,
    val plansFound: Int,
    val plansNew: Int,
    val plansUpdated: Int,
    val errorMessage: String?,
)
