package com.borasarang.communityjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.borasarang.common.util.net.NetMeter

/** 수집 이력/에러 로그. 디버그 로그 화면과 /api 소스 상태의 근거 */
@Entity(
    tableName = "crawl_logs",
    indices = [Index("sourceId"), Index("startedAt"), Index(value = ["startedAt", "sourceId"])],
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
    /** 수집 1회 실행의 실수신·실송신 바이트 (R42b, NetMeter 델타) */
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
)
