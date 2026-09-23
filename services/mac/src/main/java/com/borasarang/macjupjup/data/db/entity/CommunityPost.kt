package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 커뮤니티 게시글 (PLAN_v23).
 * id = originalUrl SHA-256 hex. news_articles와 분리 (메인 키 충돌 방지).
 * 법적 정책: 목록은 summary 500자, 상세 contentHtml은 신규 건만 절단 저장.
 */
@Entity(
    tableName = "community_posts",
    indices = [
        Index("originalUrl", unique = true),
        Index("main", "publishedAt"),
        Index("main", "sourceId"),
        Index("publishedAt"),
        Index("collectedAt"),
    ],
)
data class CommunityPost(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceName: String,
    /** apple / mac / ai */
    val main: String,
    val title: String,
    /** 목록 요약 (500자 절단) */
    val summary: String?,
    val authorName: String?,
    val originalUrl: String,
    val thumbnailUrl: String?,
    val commentCount: Int?,
    val viewCount: Int?,
    /** 상세 본문 HTML (신규만, 절단). null이면 상세에서 원문 링크 안내 */
    val contentHtml: String? = null,
    val publishedAt: Long,
    val collectedAt: Long,
)
