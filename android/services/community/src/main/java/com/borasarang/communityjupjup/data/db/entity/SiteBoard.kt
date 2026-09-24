package com.borasarang.communityjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.borasarang.communityjupjup.util.category.CommunityCategories

/** 사이트 게시판 — 소스 1:N, 통합 카테고리 매핑 (V2 site_boards) */
@Entity(
    tableName = "site_boards",
    indices = [Index("sourceId"), Index("categoryId")],
)
data class SiteBoard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    /** 통합 카테고리 1~10 (CommunityCategories) */
    val categoryId: Int,
    /** false면 수집 제외 (포털 게시판 관리에서 토글) */
    val enabled: Boolean = true,
    /** 보드별 수집 주기(분). 15/30/60/120 중 선택, 기본 30 */
    val intervalMinutes: Int = 30,
)
