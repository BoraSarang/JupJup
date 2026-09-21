package com.borasarang.communityjupjup.server

import com.borasarang.communityjupjup.data.db.entity.CommunityPost
import com.borasarang.communityjupjup.data.repository.PagedPosts
import com.borasarang.communityjupjup.data.repository.PostListItem
import com.borasarang.communityjupjup.data.repository.PostWithSourceList
import org.junit.Assert.assertTrue
import org.junit.Test

class CmServerJsonTest {

    private fun samplePost() = CommunityPost(
        sourceId = "clien_park",
        boardId = 1,
        categoryId = 2,
        originalPostId = "123",
        title = "테스트 제목",
        summary = "요약",
        authorName = "닉네임",
        originalUrl = "https://www.clien.net/service/board/park/123",
        canonicalUrl = "https://www.clien.net/service/board/park/123",
        thumbnailUrl = null,
        viewCount = 100,
        likeCount = 5,
        commentCount = 3,
        mallName = null,
        salePrice = null,
        originalPrice = null,
        discountRate = null,
        isSoldOut = null,
        dealStatus = null,
        dealLocation = null,
        publishedAt = 1000L,
        collectedAt = 2000L,
    )

    @Test
    fun `postsJson 목록 구조`() {
        val item = PostListItem(samplePost(), "클리앙 모두의공원", "모두의공원", "유머/이슈")
        val json = postsJson(PagedPosts(listOf(item), 1, 1, 50))
        assertTrue(json.contains("\"total\":1"))
        assertTrue(json.contains("테스트 제목"))
        assertTrue(json.contains("클리앙 모두의공원"))
        assertTrue(json.contains("유머/이슈"))
        assertTrue(json.contains("\"likeCount\":5"))
    }

    @Test
    fun `postDetailJson 상세 구조`() {
        val json = postDetailJson(PostWithSourceList(samplePost(), null, null))
        assertTrue(json.contains("\"originalUrl\":\"https://www.clien.net/service/board/park/123\""))
        assertTrue(json.contains("\"categoryId\":2"))
        assertTrue(json.contains("\"categoryName\":\"유머/이슈\""))
    }

    @Test
    fun `categoriesJson 10종`() {
        val json = categoriesJson()
        assertTrue(json.contains("속보/인기"))
        assertTrue(json.contains("핫딜/알뜰구매/지름"))
        assertTrue(json.contains("중고장터"))
    }
}

class ThumbUrlTest {
    @org.junit.Test
    fun `허용 판정`() {
        org.junit.Assert.assertTrue(isAllowedThumbUrl("https://i3.ruliweb.com/img/a.webp"))
        org.junit.Assert.assertTrue(isAllowedThumbUrl("http://todayhumor.co.kr/x.jpg"))
        org.junit.Assert.assertFalse(isAllowedThumbUrl(""))
        org.junit.Assert.assertFalse(isAllowedThumbUrl("ftp://a.com/x.jpg"))
        org.junit.Assert.assertFalse(isAllowedThumbUrl("http://127.0.0.1:3003/x.jpg"))
        org.junit.Assert.assertFalse(isAllowedThumbUrl("http://192.168.0.2/x.jpg"))
        org.junit.Assert.assertFalse(isAllowedThumbUrl("not a url"))
    }
}
