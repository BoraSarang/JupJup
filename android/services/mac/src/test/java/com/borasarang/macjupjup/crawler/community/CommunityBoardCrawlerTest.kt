package com.borasarang.macjupjup.crawler.community

import com.borasarang.common.crawl.SelectorConfig
import com.borasarang.common.crawl.TimeParser
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityBoardCrawlerTest {

    private val damoangCfg = SelectorConfig.parse(
        """{"main":"apple","listRow":"a.post-row","title":".post-title","author":".post-meta-text","time":".post-meta-text","detailContent":".prose"}""",
    )

    private val damoangHtml = """
        <html><body>
        <div class="svelte-x">
          <a href="/applemoang/20651" class="post-row bg-background hover:bg-accent block px-4">
            <span class="truncate post-title" title="애플워치는 단독사용 시 배터리 소모량이 다르군요">애플워치는 단독사용 시 배터리 소모량이 다르군요</span>
            <span class="post-meta-text"><img src="https://r2.damoang.net/x.jpg"/><span>라그랑블루</span></span>
            <span class="post-meta-text">08:35</span>
            <button class="comment-count">[2]</button>
          </a>
          <a href="/applemoang/20640" class="post-row bg-background">
            <span class="truncate post-title" title="iOS 27.2 Developer Beta 2 릴리스">iOS 27.2 Developer Beta 2 릴리스</span>
            <span class="post-meta-text">13:46</span>
          </a>
        </div>
        </body></html>
    """.trimIndent()

    private val clienCfg = SelectorConfig.parse(
        """{"main":"mac","listRow":"div.list_item","title":"a.list_subject, .list_title a","author":".nickname","time":".list_time","excludeRow":".notice","detailContent":".post_content article","comments":".list_reply .line"}""",
    )

    private val clienHtml = """
        <html><body>
        <div class="list_item notice">
          <div class="list_title"><a class="list_subject" href="/service/board/cm_mac/1">[공지] 규칙</a></div>
          <div class="list_time"><span>10-30</span></div>
          <div class="list_author"><span class="nickname">관리자</span></div>
        </div>
        <div class="list_item symph-row">
          <div class="list_title"><a class="list_subject" href="/service/board/cm_mac/5088373">미러링 앱 리딤</a></div>
          <div class="list_time"><span>13:46</span></div>
          <div class="list_author"><span class="nickname">쏠!</span></div>
          <div class="list_reply"><span class="line">4</span></div>
        </div>
        </body></html>
    """.trimIndent()

    private fun source(id: String = "community_damoang_apple") = CrawlSource(
        id = id,
        name = "테스트",
        type = Constants.TYPE_COMMUNITY_BOARD,
        baseUrl = "https://example.com/board",
        enabled = true,
        intervalHours = 1,
        intervalMinutes = 30,
        lastRunAt = null,
        lastStatus = Constants.STATUS_NEVER_RUN,
        errorMessage = null,
        selectorConfigJson = null,
    )

    private fun newCrawler(src: CrawlSource, cfg: SelectorConfig): CommunityBoardCrawler =
        CommunityBoardCrawler(src, mockk<MacDatabase>(relaxed = true), cfg)

    @Test
    fun `셀렉터_config_파싱`() {
        assertEquals("a.post-row", damoangCfg.listRow)
        assertEquals(".post-title", damoangCfg.title)
        assertEquals("apple", damoangCfg.main)
        assertEquals(".prose", damoangCfg.detailContent)
    }

    @Test
    fun `다모앙_목록_파싱`() {
        val c = newCrawler(source("community_damoang_apple"), damoangCfg)
        val posts = c.parseList(damoangHtml, "https://damoang.net/applemoang", damoangCfg, "apple")
        assertEquals(2, posts.size)
        val p = posts[0]
        assertEquals("apple", p.main)
        assertTrue(p.title.contains("애플워치"))
        assertEquals("https://damoang.net/applemoang/20651", p.originalUrl)
        assertEquals(64, p.id.length)
        assertNotNull(p.publishedAt)
        assertEquals(2, p.commentCount)
    }

    @Test
    fun `클리앙_공지_제외`() {
        val c = newCrawler(source("community_clien_mac"), clienCfg)
        val posts = c.parseList(clienHtml, "https://m.clien.net/service/board/cm_mac", clienCfg, "mac")
        assertEquals(1, posts.size)
        assertEquals("미러링 앱 리딤", posts[0].title)
        assertEquals("https://m.clien.net/service/board/cm_mac/5088373", posts[0].originalUrl)
        assertEquals("쏠!", posts[0].authorName)
        assertEquals(4, posts[0].commentCount)
    }

    @Test
    fun `sourceId_to_main_매핑`() {
        assertEquals("apple", CommunityBoardCrawler.sourceIdToMain("community_damoang_apple"))
        assertEquals("mac", CommunityBoardCrawler.sourceIdToMain("community_clien_mac"))
        assertEquals("ai", CommunityBoardCrawler.sourceIdToMain("community_damoang_ai"))
        assertEquals("mac", CommunityBoardCrawler.sourceIdToMain("community_dc_macbook"))
    }

    @Test
    fun `sha256_결정적`() {
        val a = CommunityBoardCrawler.sha256Hex("https://example.com/a")
        val b = CommunityBoardCrawler.sha256Hex("https://example.com/a")
        val c = CommunityBoardCrawler.sha256Hex("https://example.com/b")
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(64, a.length)
    }

    @Test
    fun `시간_파서_HHMM`() {
        val now = System.currentTimeMillis()
        val ts = TimeParser.parse("08:35")
        assertNotNull(ts)
        assertTrue(ts!! <= now)
        assertTrue(now - ts < 86_400_000L + 3_600_000L)
    }

    @Test
    fun `시간_파서_상대`() {
        val now = System.currentTimeMillis()
        val ts = TimeParser.parse("5분 전")
        assertNotNull(ts)
        assertTrue(now - ts!! in 0..6 * 60_000L)
    }

    @Test
    fun `Googlebot_UA_사용`() {
        val ua = CommunityBoardCrawler.HEADERS["User-Agent"].orEmpty()
        assertTrue(ua.contains("Googlebot"))
    }
}
