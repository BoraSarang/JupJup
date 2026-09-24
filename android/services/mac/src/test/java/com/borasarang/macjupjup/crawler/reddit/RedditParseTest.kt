package com.borasarang.macjupjup.crawler.reddit

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditParseTest {

    private val source = CrawlSource(
        id = "reddit_macapps", name = "Reddit r/macapps", type = "REDDIT_JSON",
        baseUrl = "https://www.reddit.com/r/macapps",
        enabled = true, intervalHours = 12, intervalMinutes = 720,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val sample = """
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns="http://www.w3.org/2005/Atom">
      <title>r/MacApps</title>
      <entry>
        <author><name>/u/alice</name></author>
        <category term="macapps" label="r/macapps"/>
        <content type="html">&lt;div class="md"&gt;&lt;p&gt;Free and open source.&lt;/p&gt;
          &lt;a href="https://fetchbar.example.com"&gt;site&lt;/a&gt;
          &lt;img src="https://b.thumbs.redditmedia.com/x.jpg"/&gt;&lt;/div&gt;</content>
        <id>t3_abc</id>
        <link rel="alternate" href="https://www.reddit.com/r/macapps/comments/abc/fetchbar/"/>
        <published>2024-11-22T04:53:20+00:00</published>
        <title>[FetchBar] Menu bar clipboard history for macOS</title>
      </entry>
      <entry>
        <author><name>/u/bob</name></author>
        <category term="macapps" label="r/macapps"/>
        <content type="html">&lt;p&gt;Notch utility&lt;/p&gt;</content>
        <id>t3_xyz</id>
        <link rel="alternate" href="https://www.reddit.com/r/macapps/comments/xyz/notchfit/"/>
        <published>2024-11-21T00:53:20+00:00</published>
        <title>Show HN: NotchFit — notch utility</title>
      </entry>
      <entry>
        <author><name>/u/c</name></author>
        <content type="html">&lt;p&gt;bad&lt;/p&gt;</content>
        <id>t3_ns</id>
        <link rel="alternate" href="https://www.reddit.com/r/macapps/comments/nsfw/"/>
        <published>2024-11-20T00:53:20+00:00</published>
        <title>[NSFW App] bad</title>
      </entry>
      <entry>
        <author><name>/u/mod</name></author>
        <content type="html">&lt;p&gt;chat&lt;/p&gt;</content>
        <id>t3_st</id>
        <link rel="alternate" href="https://www.reddit.com/r/macapps/comments/sticky/"/>
        <published>2024-11-19T00:53:20+00:00</published>
        <title>Weekly discussion thread</title>
      </entry>
    </feed>
    """.trimIndent()

    @Test
    fun `parse_2건파싱_스티키와NSFW_스킵`() {
        val drafts = RedditMacAppsCrawler(source).parsePosts(sample)
        assertEquals(2, drafts.size)
        val first = drafts[0].app
        assertEquals("FetchBar", first.name)
        assertEquals("r/macapps", first.developer)
        assertEquals(
            "https://www.reddit.com/r/macapps/comments/abc/fetchbar/",
            drafts[0].mappings[0].sourceUrl,
        )
        assertEquals("https://fetchbar.example.com", first.homepageUrl)
        assertEquals(1732251200000L, first.releaseDate)
        assertTrue(first.topics!!.contains("reddit"))
        assertTrue(drafts.none { it.app.name.contains("Weekly") })
        assertTrue(drafts.none { it.app.name.contains("NSFW") })
    }

    @Test
    fun `cleanName_브라켓과ShowHN_프리픽스_제거`() {
        val c = RedditMacAppsCrawler(source)
        assertEquals("FetchBar", c.cleanName("[FetchBar] Menu bar clipboard"))
        assertEquals("NotchFit", c.cleanName("Show HN: NotchFit — notch utility"))
        assertEquals("그냥제목", c.cleanName(" 그냥제목 "))
    }

    @Test
    fun `parse_깨진XML_parseFail`() {
        try {
            RedditMacAppsCrawler(source).parsePosts("{broken")
            throw AssertionError("expected parse fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Reddit"))
        }
    }

    @Test
    fun `parse_빈피드_빈목록`() {
        val empty = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>x</title></feed>"""
        assertTrue(RedditMacAppsCrawler(source).parsePosts(empty).isEmpty())
    }
}
