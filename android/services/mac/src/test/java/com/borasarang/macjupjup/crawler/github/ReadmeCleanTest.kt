package com.borasarang.macjupjup.crawler.github

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadmeCleanTest {

    private val source = CrawlSource(
        id = "t", name = "t", type = "GITHUB_SEARCH", baseUrl = "https://api.github.com",
        enabled = true, intervalHours = 6, intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    @Test
    fun `마크다운_보존_T140`() {
        val raw = "# Title\n\nSome **bold** text with [link](https://x).\n\n- item one\n- item two\n"
        val cleaned = GitHubSearchCrawler(source).cleanReadme(raw)
        assertNotNull(cleaned)
        assertTrue(cleaned!!.contains("# Title"))
        assertTrue(cleaned.contains("**bold**"))
        assertTrue(cleaned.contains("[link](https://x)"))
        assertTrue(cleaned.contains("- item one"))
    }

    @Test
    fun `HTML_살균_T140`() {
        val raw = "Hello <script>alert(1)</script> world\n\n<!-- comment -->\n\nText"
        val cleaned = GitHubSearchCrawler(source).cleanReadme(raw)
        assertNotNull(cleaned)
        assertTrue(!cleaned!!.contains("<script>"))
        assertTrue(!cleaned.contains("<!--"))
        assertTrue(cleaned.contains("Hello"))
        assertTrue(cleaned.contains("world"))
    }

    @Test
    fun `코드펜스_보존_T140`() {
        val raw = "Usage:\n\n```sh\nbrew install foo\n```\n"
        val cleaned = GitHubSearchCrawler(source).cleanReadme(raw)
        assertNotNull(cleaned)
        assertTrue(cleaned!!.contains("```sh"))
        assertTrue(cleaned.contains("brew install foo"))
    }

    @Test
    fun `빈문서_null`() {
        assertNull(GitHubSearchCrawler(source).cleanReadme("   \n "))
    }
}
