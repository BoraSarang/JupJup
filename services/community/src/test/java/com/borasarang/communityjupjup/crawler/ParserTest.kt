package com.borasarang.communityjupjup.crawler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    @Test
    fun `TimeParser 상대시간 파싱`() {
        val now = System.currentTimeMillis()
        val min = TimeParser.parse("5분 전")!!
        assertTrue(min in now - 400_000..now)
        val hour = TimeParser.parse("3시간 전")!!
        assertTrue(hour in now - 11_000_000..now)
        val day = TimeParser.parse("2일 전")!!
        assertTrue(day in now - 175_000_000..now)
    }

    @Test
    fun `TimeParser 절댓값 파싱`() {
        assertNotNull(TimeParser.parse("2026-09-21 10:30"))
        assertNotNull(TimeParser.parse("2026.09.21"))
        assertNotNull(TimeParser.parse("10:23"))
        assertNull(TimeParser.parse(null))
        assertNull(TimeParser.parse(""))
        assertNull(TimeParser.parse("???"))
    }

    @Test
    fun `PriceParser 핫딜 추출`() {
        val deal = PriceParser.parse("[쿠팡] 삼성 SSD 1TB 89,900원 25%", null)
        assertEquals(89900, deal.salePrice)
        assertEquals(25, deal.discountRate)
        assertEquals("쿠팡", deal.mallName)
        assertNull(deal.dealStatus)
    }

    @Test
    fun `PriceParser 중고 상태 추출`() {
        assertEquals("sold", PriceParser.parse("아이폰 판매완료", null).dealStatus)
        assertEquals("reserved", PriceParser.parse("예약중) 갤럭시", null).dealStatus)
        assertEquals("selling", PriceParser.parse("판매중 노트북 300,000원", null).dealStatus)
        val soldOut = PriceParser.parse("핫딜 품절 9,900원", null)
        assertEquals(true, soldOut.isSoldOut)
        assertEquals("sold", soldOut.dealStatus)
    }

    @Test
    fun `SelectorConfig 깨진 JSON은 기본값`() {
        val cfg = SelectorConfig.parse("{broken")
        assertEquals(SelectorConfig.defaults().listRow, cfg.listRow)
        val empty = SelectorConfig.parse(null)
        assertEquals(SelectorConfig.defaults().title, empty.title)
    }

    @Test
    fun `BoardCrawler 목록 파싱`() {
        val html = """
            <html><body>
            <div class="list_item notice"><div class="list_title"><a class="list_subject" href="/service/board/annonce/1">공지</a></div></div>
            <div class="list_item"><div class="list_title"><a class="list_subject" href="/service/board/park/123">제목1</a></div>
            <div class="list_author"><span class="nickname">닉네임</span></div>
            <div class="list_time"><span class="time">10:23</span></div>
            <div class="list_symph"><span>5</span></div>
            <div class="list_hit"><span class="hit">65.5 k</span></div></div>
            <div class="list_item"><div class="list_title"><a class="list_subject" href="/service/board/park/124">제목2</a></div>
            <div class="list_author"><span class="nickname">닉2</span></div>
            <div class="list_time"><span class="timestamp">2026-09-21 09:00:00</span></div></div>
            </body></html>
        """.trimIndent()
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            id = "test", name = "테스트", type = "BOARD", domain = "example.com",
            baseUrl = "https://example.com", enabled = true, intervalHours = 0,
            intervalMinutes = 30, lastRunAt = null, lastStatus = "NEVER_RUN",
            errorMessage = null, selectorConfigJson = null,
        )
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val crawler = BoardCrawler(source, db)
        val config = SelectorConfig.parse(
            """{"listRow":"div.list_item","title":"a.list_subject, .list_title a","author":".list_author .nickname, .nickname","time":".list_time .timestamp, .list_time","likes":".list_symph span","views":".list_hit .hit, .list_hit","excludeRow":".notice"}"""
        )
        val drafts = crawler.parseBoard(html, "https://example.com", 7L, 2, config)
        // 공지 1건 제외 → 2건
        assertEquals(2, drafts.size)
        assertEquals("제목1", drafts[0].title)
        assertEquals("닉네임", drafts[0].authorName)
        assertEquals("https://example.com/service/board/park/123", drafts[0].originalUrl)
        assertEquals(5, drafts[0].likeCount)
        assertEquals(65500, drafts[0].viewCount)
        assertEquals(7L, drafts[0].boardId)
        assertEquals(2, drafts[0].categoryId)
        assertNotNull(drafts[0].publishedAt)
    }

    @Test
    fun `parseDetail 클리앙 본문 추출`() {
        val html = """<html><body><div class="content_view"><div class="post_view"><div class="post_content"><article><div class="post_article"><h2>제목</h2><p>첫 번째 문단 내용입니다. 두 번째 문장도 있습니다.</p><p>세 번째 문단입니다.</p></div></article></div></div></div></body></html>"""
        val config = SelectorConfig.parse("""{"detailContent":".post_content article, .post_article"}""")
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertNotNull(detail.summary)
        assertTrue(detail.summary!!.contains("첫 번째 문단"))
        assertTrue(detail.summary.length <= 500)
    }

    @Test
    fun `parseDetail 범용 폴백`() {
        val body = "본문 내용입니다. ".repeat(100)
        val html = "<html><body><div><article>$body</article></div></body></html>"
        val config = SelectorConfig.parse(null)
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertNotNull(detail.summary)
        assertEquals(500, detail.summary!!.length)
    }

    @Test
    fun `parseDetail 빈 본문은 null`() {
        val html = "<html><body><div class='view_content'><p><br></p></div></body></html>"
        val config = SelectorConfig.parse("""{"detailContent":".view_content"}""")
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val empty = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertNull(empty.summary)
        assertEquals(0, empty.imageCount)
    }

    @Test
    fun `parseDetail 이미지 추출`() {
        val html = """<html><body><div class="view_content"><p>짤 하나</p><p><img src="https://i2.ruliweb.com/img/a.webp" width="716" height="715"><img src="/img/icon.gif" width="16" height="16"></p></div></body></html>"""
        val config = SelectorConfig.parse("""{"detailContent":".view_content"}""")
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertEquals("https://i2.ruliweb.com/img/a.webp", detail.thumbnailUrl)
        assertEquals(1, detail.imageCount)
    }

    @Test
    fun `parseDetail 링크 보존 최대 3개`() {
        val html = """<html><body><article><p>본문 텍스트입니다. 충분한 길이를 위해 문장을 늘립니다. 계속 이어지는 내용입니다.</p><p><a href="https://a.com/1">첫 링크</a> <a href="https://b.com/2">둘째</a> <a href="https://c.com/3">셋째</a> <a href="https://d.com/4">넷째</a> <a href="/relative">상대</a></p></article></body></html>"""
        val config = SelectorConfig.parse(null)
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertNotNull(detail.summary)
        assertTrue(detail.summary!!.contains("🔗 첫 링크: https://a.com/1"))
        assertTrue(detail.summary!!.contains("https://c.com/3"))
        assertTrue(!detail.summary!!.contains("d.com/4"))
        assertTrue(!detail.summary!!.contains("/relative"))
        assertEquals(3, detail.links.size)
        assertTrue(detail.summary!!.length <= 500)
    }

    @Test
    fun `이미지 전용 앵커는 링크로 취급 안함`() {
        val html = """<html><body><article><p>본문 텍스트입니다.</p><p><a href="https://e.com/view/1"><img src="https://e.com/a.jpg"></a> <a href="https://e.com/2">진짜 링크</a></p></article></body></html>"""
        val config = SelectorConfig.parse(null)
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertEquals(1, detail.links.size)
        assertEquals("진짜 링크", detail.links[0].text)
        assertTrue(!detail.summary!!.contains("view/1"))
    }

    @Test
    fun `이미지 목록 최대 5장`() {
        val imgs = (1..8).map { "<img src=\"https://e.com/$it.jpg\">" }.joinToString("")
        val html = "<html><body><article><p>텍스트 충분한 길이 확보용 문장입니다. 더 길게 씁니다.</p><p>$imgs</p></article></body></html>"
        val config = SelectorConfig.parse(null)
        val db = io.mockk.mockk<com.borasarang.communityjupjup.data.db.CommunityDatabase>(relaxed = true)
        val source = com.borasarang.communityjupjup.data.db.entity.CrawlSource(
            "t", "T", "BOARD", "e.com", "https://e.com", true, 0, 30, null, "NEVER_RUN", null, null,
        )
        val detail = BoardCrawler(source, db).parseDetail(html, "https://e.com", config)
        assertEquals(5, detail.imageUrls.size)
        assertEquals(5, detail.imageCount)
        assertEquals("https://e.com/1.jpg", detail.thumbnailUrl)
    }

    @Test
    fun `이미지 JSON 왕복`() {
        val urls = listOf("https://e.com/a.jpg", "https://e.com/b.webp")
        val enc = com.borasarang.communityjupjup.crawler.encodeImageUrls(urls)
        assertEquals(urls, com.borasarang.communityjupjup.crawler.decodeImageUrls(enc))
        assertEquals(emptyList<String>(), com.borasarang.communityjupjup.crawler.decodeImageUrls("깨진값"))
        assertEquals(emptyList<String>(), com.borasarang.communityjupjup.crawler.decodeImageUrls(null))
    }
}
