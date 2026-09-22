package com.borasarang.communityjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 커뮤니티 게시글 메타 (V2 posts의 Room 이식).
 * 법적 정책: 본문 전체 저장 금지 — summary 500자 + originalUrl만.
 */
@Entity(
    tableName = "posts",
    indices = [
        Index("sourceId"),
        Index("boardId"),
        Index("categoryId"),
        Index("publishedAt"),
        Index("collectedAt"),
        Index(value = ["categoryId", "publishedAt"]),
        Index(value = ["sourceId", "publishedAt"]),
        Index(value = ["categoryId", "sourceId"]),
        Index(value = ["canonicalUrl"], unique = true),
    ],
)
data class CommunityPost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val boardId: Long,
    /** 통합 카테고리 1~10 */
    val categoryId: Int,
    val originalPostId: String,
    val title: String,
    /** 요약 500자 (MAX_SUMMARY_LEN 절단) */
    val summary: String?,
    /** 작성자 = 기자명 */
    val authorName: String?,
    val originalUrl: String,
    /** 중복 판정 키 (UrlCanonical 정규화, UNIQUE) */
    val canonicalUrl: String,
    val thumbnailUrl: String?,
    val viewCount: Int?,
    val likeCount: Int?,
    val commentCount: Int?,
    /** 핫딜 전용 */
    val mallName: String?,
    val salePrice: Int?,
    val originalPrice: Int?,
    val discountRate: Int?,
    val isSoldOut: Boolean?,
    /** 중고 전용: selling/sold/reserved */
    val dealStatus: String?,
    val dealLocation: String?,
    val publishedAt: Long?,
    val collectedAt: Long,
    /** 본문 이미지 목록 (JSON 배열, 최대 5장) */
    val imageUrls: String = "[]",
)
