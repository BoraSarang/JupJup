package com.borasarang.communityjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 수집 소스 설정. BOARD 타입은 selectorConfigJson의 셀렉터로 파싱 */
@Entity(
    tableName = "sources",
    indices = [Index("type"), Index("enabled")],
)
data class CrawlSource(
    @PrimaryKey val id: String,
    val name: String,
    /** BOARD (selector_config 기반 범용 보드 크롤러) */
    val type: String,
    val domain: String,
    val baseUrl: String,
    val enabled: Boolean,
    val intervalHours: Int,
    /** 수집 주기(분). 스케줄 기준값 (15분 하한, WorkManager 제약) */
    val intervalMinutes: Int,
    val lastRunAt: Long?,
    /** NEVER_RUN / SUCCESS / FAILED / RUNNING */
    val lastStatus: String,
    val errorMessage: String?,
    /** 보드별 목록/제목/작성자/시간/추천수 CSS 셀렉터 JSON (V2 GenericSpider) */
    val selectorConfigJson: String?,
)
