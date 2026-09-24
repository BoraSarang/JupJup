package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 앱 버전 히스토리. 버전 bump = 새 기능 신호 */
@Entity(
    tableName = "version_history",
    primaryKeys = ["appId", "version"],
    indices = [Index("appId"), Index("detectedAt")],
)
data class VersionHistory(
    val appId: String,
    val version: String,
    val detectedAt: Long,
    /** 릴리즈노트 요약 (전문 복제 금지) */
    val notesSummary: String?,
    /** 버전 감지 출처 소스 ID */
    val source: String?,
    /** T-080: 해당 버전 링크 (GitHub 릴리즈 페이지 / MAS 스토어 현재 페이지). 구버전 행은 null */
    val sourceUrl: String?,
)
