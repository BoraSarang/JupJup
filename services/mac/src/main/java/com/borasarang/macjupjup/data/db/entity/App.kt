package com.borasarang.macjupjup.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 정규화된 macOS 앱. name+developer 정규화 해시가 id (중복 병합 기준).
 * 상세 7섹션(소개/스크린샷/특징/기능/새기능/홈페이지/다운로드) 필드 포함.
 */
@Entity(
    tableName = "apps",
    indices = [
        Index(value = ["name", "developer"], unique = true),
        Index("license"),
        Index("category"),
        Index("trackId"),
        Index("repoFullName"),
        Index("lastUpdatedAt"),
        Index("isNew"),
        Index("tags"),
        Index("firstSeenAt"),
        Index(value = ["license", "category", "lastUpdatedAt"]),
    ],
)
data class App(
    @PrimaryKey val id: String,
    /** 'macOS' 고정 (타 플랫폼 수집 시 확장 여지) */
    val platform: String,
    val name: String,
    val developer: String,
    /** OSS / FREE / PAID */
    val license: String,
    /** 스토어 가격 (USD). 무료·오픈소스는 0 */
    val price: Double,
    val currency: String,
    /** Setapp 10카테고리 */
    val category: String,
    /** 횡단 태그 CSV: "AI-Agent,MenuBar" */
    val tags: String?,
    /** App Store trackId (스토어 앱만) */
    val trackId: Long?,
    /** GitHub owner/repo (오픈소스만) */
    val repoFullName: String?,
    val homepageUrl: String?,
    /** 현재 버전 */
    val version: String?,
    /** 이전 버전 (diff 표시용) */
    val prevVersion: String?,
    /** 릴리즈노트 요약 (전문 복제 금지) */
    val releaseNotesSummary: String?,
    val releaseDate: Long?,
    /** 소개 발췌 (최대 2000자) */
    val descriptionSnippet: String?,
    /** 앱 아이콘 URL (Apple CDN·GitHub 아바타, 직접 표시) */
    val iconUrl: String?,
    /** 소개 한글 번역 (ML Kit) */
    val descriptionKo: String?,
    /** 릴리즈노트 전체 (최대 2000자) */
    val releaseNotes: String?,
    /** 릴리즈노트 한글 번역 */
    val releaseNotesKo: String?,
    /** 스토어 판매자명 */
    val sellerName: String?,
    /** 파일 크기 (bytes) */
    val fileSize: Long?,
    /** 최소 OS 버전 */
    val minOs: String?,
    /** 연령 등급 (4+/9+/12+/17+) */
    val contentRating: String?,
    /** GitHub forks 수 */
    val forks: Int?,
    /** GitHub open issues 수 */
    val issues: Int?,
    /** GitHub 라이선스명 (MIT 등) */
    val licenseName: String?,
    /** 스크린샷 URL CSV (Apple CDN 직접 표시) */
    val screenshotUrls: String?,
    val averageRating: Double?,
    val ratingCount: Int?,
    val stars: Int?,
    val primaryLanguage: String?,
    /** GitHub topics CSV */
    val topics: String?,
    val firstSeenAt: Long,
    val lastUpdatedAt: Long,
    val isNew: Boolean,
    /** 대표(최초 수집) 소스 ID */
    val sourceId: String?,
    /** 수동 라이선스 오버라이드 (자동분류 무시) */
    val licenseOverride: String?,
)
