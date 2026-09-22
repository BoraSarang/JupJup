package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 뉴스 기사 (R32 PLAN_v17).
 * id = 원문 URL의 SHA-256 hex (hash 겸용, 중복 제거 기준).
 * 본문 이미지는 저장 없이 원본 URL 그대로 contentHtml에 유지.
 */
@Entity(
    tableName = "news_articles",
    indices = [
        Index("originalUrl", unique = true),
        Index("main", "sub", "publishedAt"),
        Index("publishedAt"),
    ],
)
data class NewsArticle(
    @PrimaryKey val id: String,
    /** 수집처 id (예: news_macrumors) */
    val sourceId: String,
    /** 수집처 표시명 (예: MacRumors) */
    val sourceName: String,
    /** mac / ai / sec */
    val main: String,
    /** 서브 카테고리 (예: macOS, 모델 출시). 미분류는 "전체" */
    val sub: String,
    val title: String,
    /** 1줄 요약 (본문 첫 문장, 최대 200자) */
    val summary: String?,
    /** 한글 제목·요약 (TranslateWorker, ML Kit 온디바이스, R41) */
    val titleKo: String? = null,
    val summaryKo: String? = null,
    /** 본문 HTML (원본 이미지 URL 유지, script/iframe 제거) */
    val contentHtml: String?,
    val originalUrl: String,
    val thumbnailUrl: String?,
    /** 원문 게시 시각 (epoch ms, 없으면 수집 시각) */
    val publishedAt: Long,
    val collectedAt: Long,
    /** 인기 태그 (R50, 쉼표 구분 상위 10개. 수집 시 추출, 없으면 null) */
    val tags: String? = null,
)
