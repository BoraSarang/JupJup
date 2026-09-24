package com.borasarang.communityjupjup.crawler

import com.borasarang.communityjupjup.util.text.UrlCanonical

/** 게시글 초안 1건 (Worker가 CommunityPost로 변환·저장) */
data class PostDraft(
    val boardId: Long,
    val categoryId: Int,
    val originalPostId: String,
    val title: String,
    val summary: String?,
    val authorName: String?,
    val originalUrl: String,
    /** 중복 판정 키 (UrlCanonical 정규화) */
    val canonicalUrl: String,
    val thumbnailUrl: String? = null,
    /** 본문 이미지 목록 (최대 5장) */
    val imageUrls: List<String> = emptyList(),
    val viewCount: Int? = null,
    val likeCount: Int? = null,
    val commentCount: Int? = null,
    val mallName: String? = null,
    val salePrice: Int? = null,
    val originalPrice: Int? = null,
    val discountRate: Int? = null,
    val isSoldOut: Boolean? = null,
    val dealStatus: String? = null,
    val dealLocation: String? = null,
    val publishedAt: Long? = null,
)

/** 상세 페이지 추출 결과 (본문 500자 요약 + 대표 이미지 + 전체 이미지 + 링크) */
data class DetailResult(
    val summary: String?,
    val thumbnailUrl: String?,
    val imageCount: Int,
    val imageUrls: List<String> = emptyList(),
    val links: List<BodyLink> = emptyList(),
)

/** 본문 내 링크 1건 */
data class BodyLink(
    val text: String,
    val href: String,
)

/** 이미지 목록 JSON 인코딩 (DB 저장용) */
fun encodeImageUrls(urls: List<String>): String {
    return buildString {
        append("[")
        urls.forEachIndexed { i, u ->
            if (i > 0) append(",")
            append("\"")
            append(u.replace("\\", "\\\\").replace("\"", "\\\""))
            append("\"")
        }
        append("]")
    }
}

/** 이미지 목록 JSON 디코딩 (깨지면 빈 목록) */
fun decodeImageUrls(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(raw)
            .let { it as? kotlinx.serialization.json.JsonArray }
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

/** 커뮤니티 크롤러 인터페이스. 실패는 예외로 상위에 전달 */
interface CommunityCrawler {
    val sourceName: String
    suspend fun crawl(): Result<List<PostDraft>>

    /** 단일 보드 수집 (보드 단위 워커용) */
    suspend fun crawlSingle(board: com.borasarang.communityjupjup.data.db.entity.SiteBoard): Result<List<PostDraft>>
}
