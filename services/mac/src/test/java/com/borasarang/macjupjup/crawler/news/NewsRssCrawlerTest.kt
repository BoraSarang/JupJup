package com.borasarang.macjupjup.crawler.news

import com.borasarang.macjupjup.util.NewsCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRssCrawlerTest {

    private val macFeed = NewsFeed("MacRumors", "https://feeds.macrumors.com/MacRumors-All", NewsCategories.MAIN_MAC)

    private val rss2 = """
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
        <channel><title>MacRumors</title>
        <item>
          <title>Apple Releases macOS Update</title>
          <link>https://www.macrumors.com/2026/09/21/macos-update/</link>
          <pubDate>Sun, 21 Sep 2026 10:00:00 +0000</pubDate>
          <category>macOS</category>
          <description>Apple today released a new macOS update.</description>
          <media:thumbnail url="https://images.macrumors.com/t/test.jpg"/>
        </item>
        <item>
          <title>제목 없음 링크</title>
          <link></link>
        </item>
        </channel></rss>
    """.trimIndent()

    private val atom = """
        <feed xmlns="http://www.w3.org/2005/Atom">
        <title>OpenAI Blog</title>
        <entry>
          <title>GPT Next Model Release</title>
          <link href="https://openai.com/blog/gpt-next"/>
          <updated>2026-09-21T10:00:00Z</updated>
          <summary>New model announced today.</summary>
        </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `RSS2_파싱`() {
        val items = NewsRssCrawler.parseRss(rss2, macFeed)
        assertEquals(1, items.size)
        val it = items[0]
        assertEquals("Apple Releases macOS Update", it.title)
        assertEquals("https://www.macrumors.com/2026/09/21/macos-update/", it.link)
        assertEquals(NewsCategories.MAIN_MAC, it.main)
        assertEquals("macOS", it.rssCategory)
        assertEquals("https://images.macrumors.com/t/test.jpg", it.thumbnailUrl)
        assertNotNull(it.publishedAt)
        assertTrue(it.feedText.contains("macOS update"))
    }

    @Test
    fun `Atom_파싱`() {
        val feed = NewsFeed("OpenAI", "https://openai.com/blog/rss/", NewsCategories.MAIN_AI)
        val items = NewsRssCrawler.parseRss(atom, feed)
        assertEquals(1, items.size)
        assertEquals("GPT Next Model Release", items[0].title)
        assertEquals("https://openai.com/blog/gpt-next", items[0].link)
        assertNotNull(items[0].publishedAt)
    }

    @Test
    fun `해시_결정적_중복제거`() {
        val h1 = NewsRssCrawler.sha256Hex("https://example.com/a")
        val h2 = NewsRssCrawler.sha256Hex("https://example.com/a")
        val h3 = NewsRssCrawler.sha256Hex("https://example.com/b")
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
        assertEquals(64, h1.length)
    }

    @Test
    fun `날짜_파싱`() {
        assertNotNull(NewsRssCrawler.parseNewsDate("Sun, 21 Sep 2026 10:00:00 +0000"))
        assertNotNull(NewsRssCrawler.parseNewsDate("2026-09-21T10:00:00Z"))
        assertNotNull(NewsRssCrawler.parseNewsDate("2026-09-21T10:00:00+09:00"))
        assertEquals(1790010503000L, NewsRssCrawler.parseNewsDate("1790010503"))
        assertEquals(1790010503000L, NewsRssCrawler.parseNewsDate("1790010503000"))
        assertNull(NewsRssCrawler.parseNewsDate("not a date"))
        assertNull(NewsRssCrawler.parseNewsDate(""))
    }

    @Test
    fun `본문_정제`() {
        val html = """<div><script>alert(1)</script><p>본문 <a href="/path">링크</a></p>
            <img src="/img/a.jpg" srcset="x 2x"><iframe src="https://evil.com"></iframe></div>"""
        val clean = NewsRssCrawler.sanitizeHtml(html, "https://example.com/news/1")
        assertTrue(!clean.contains("<script"))
        assertTrue(!clean.contains("<iframe"))
        assertTrue(clean.contains("referrerpolicy=\"no-referrer\""))
        assertTrue(clean.contains("loading=\"lazy\""))
        assertTrue(clean.contains("https://example.com/img/a.jpg"))
        assertTrue(clean.contains("https://example.com/path"))
        assertTrue(!clean.contains("srcset"))
    }

    @Test
    fun `본문_추출_휴리스틱`() {
        val html = """<html><body><nav>메뉴</nav>
            <article><h1>제목</h1><p>첫 문장입니다. 둘째 문장.</p>
            <img src="https://example.com/t.jpg"></article></body></html>"""
        val body = NewsRssCrawler.extractBody(html, "https://example.com/1")
        assertTrue(body.text.contains("첫 문장"))
        assertTrue(!body.text.contains("메뉴"))
        assertEquals("https://example.com/t.jpg", body.firstImage)
    }

    @Test
    fun `설명본문_이미지_썸네일`() {
        val xml = """
            <rss version="2.0">
            <channel><title>9to5Mac</title>
            <item><title>MagSafe Monday</title><link>https://9to5mac.com/2026/09/21/test/</link>
            <pubDate>Mon, 21 Sep 2026 18:11:00 +0000</pubDate>
            <description><![CDATA[<div class="feat-image"><img src="https://9to5mac.com/wp-content/uploads/sites/6/2026/09/solar.jpg" /></div><p>본문 텍스트.</p>]]></description>
            </item></channel></rss>
        """.trimIndent()
        val items = NewsRssCrawler.parseRss(xml, macFeed)
        assertEquals(1, items.size)
        assertEquals("https://9to5mac.com/wp-content/uploads/sites/6/2026/09/solar.jpg", items[0].thumbnailUrl)
        assertTrue(items[0].feedText.contains("본문 텍스트"))
    }

    @Test
    fun `content_encoded_우선`() {        val xml = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
            <channel><title>T</title>
            <item><title>기사</title><link>https://example.com/1</link>
            <description>짧은 설명</description>
            <content:encoded><![CDATA[<p>긴 본문 내용입니다.</p>]]></content:encoded>
            </item></channel></rss>
        """.trimIndent()
        val items = NewsRssCrawler.parseRss(xml, macFeed)
        assertEquals(1, items.size)
        assertTrue(items[0].feedText.contains("긴 본문"))
        assertNotNull(items[0].feedHtml)
    }

    @Test
    fun `앱매칭_단어경계`() {
        val names = listOf("a1" to "Raycast", "a2" to "start", "a3" to "한글앱")
        // 정상 매칭
        assertEquals(listOf("a1"), NewsRssCrawler.matchAppIds("Raycast update 1.90 released", names))
        // "Starting"에 "start" 오탐 금지
        assertEquals(
            emptyList<String>(),
            NewsRssCrawler.matchAppIds("Now Starting at 649 on Amazon", names),
        )
        // 한글 부분 매칭 허용
        assertEquals(listOf("a3"), NewsRssCrawler.matchAppIds("한글앱 새 버전 출시", names))
        assertEquals(
            listOf("a1", "a3"),
            NewsRssCrawler.matchAppIds("Raycast와 한글앱 업데이트", names),
        )
    }

    @Test
    fun `단락화_단일문장_분할`() {
        val long = "Apple released macOS 27. It fixes bugs. It improves performance. New Siri languages arrive. Password changes coming. Tracker updated daily."
        val html = NewsRssCrawler.paragraphize(long)
        assertTrue(html.startsWith("<p>"))
        assertTrue(html.contains("</p><p>"))
        assertTrue(!html.contains("&lt;"))
    }

    @Test
    fun `단락화_이스케이프`() {
        val html = NewsRssCrawler.paragraphize("a & b <c>")
        assertTrue(html.contains("a &amp; b &lt;c&gt;"))
    }
}
