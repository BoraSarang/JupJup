package com.borasarang.macjupjup.worker

import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping
import com.borasarang.macjupjup.data.db.entity.CommunityPost
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUploaderTest {

    private fun app() = App(
        id = "a1", platform = "macOS", name = "Alpha", developer = "Dev",
        license = "FREE", price = 0.0, currency = "USD", category = "유틸",
        tags = null, trackId = null, repoFullName = null, homepageUrl = null,
        version = "1.0", prevVersion = null, releaseNotesSummary = null,
        releaseNotes = null, releaseDate = null, descriptionSnippet = "짧은 소개",
        screenshotUrls = null, averageRating = null, ratingCount = null,
        stars = null, primaryLanguage = null, topics = null, iconUrl = null,
        descriptionKo = null, releaseNotesKo = null, sellerName = null,
        fileSize = null, minOs = null, contentRating = null, forks = null,
        issues = null, licenseName = null, firstSeenAt = 1, lastUpdatedAt = 2,
        isNew = true, sourceId = "s", licenseOverride = null,
        supportedLanguages = null,
    )

    @Test
    fun `appsPayload_구조`() {
        val body = CloudUploader.appsPayload(
            listOf(app()),
            listOf(AppSourceMapping("a1", "github", "https://x", 3)),
        )
        assertTrue(body.contains("\"drafts\""))
        assertTrue(body.contains("\"appId\":\"a1\""))
        assertTrue(body.contains("\"sourceName\":\"github\""))
        assertTrue(body.contains("\"version\":\"1.0\""))
    }

    @Test
    fun `newsPayload_관계포함`() {
        val a = NewsArticle(
            id = "n1", sourceId = "s", sourceName = "S", main = "mac", sub = "전체",
            title = "T", summary = "S", contentHtml = null, originalUrl = "u",
            thumbnailUrl = null, publishedAt = 5, collectedAt = 6,
        )
        val body = CloudUploader.newsPayload(listOf(a), listOf(NewsAppRelation("n1", "a1")))
        assertTrue(body.contains("\"articles\""))
        assertTrue(body.contains("\"newsId\":\"n1\""))
        assertTrue(body.contains("\"appId\":\"a1\""))
    }

    @Test
    fun `communityPayload_구조`() {
        val p = CommunityPost(
            id = "c1", sourceId = "s", sourceName = "S", main = "mac", title = "T",
            summary = null, authorName = "A", originalUrl = "u", thumbnailUrl = null,
            commentCount = 3, viewCount = null, publishedAt = 5, collectedAt = 6,
        )
        val body = CloudUploader.communityPayload(listOf(p))
        assertTrue(body.contains("\"posts\""))
        assertTrue(body.contains("\"commentCount\":3"))
    }

    @Test
    fun `appsPayload_소스포함`() {
        val s = com.borasarang.macjupjup.data.db.entity.CrawlSource(
            id = "github", name = "GitHub", type = "GITHUB_SEARCH", baseUrl = "https://x",
            enabled = true, intervalHours = 1, intervalMinutes = 60, lastRunAt = null,
            lastStatus = "SUCCESS", errorMessage = null, selectorConfigJson = null,
        )
        val body = CloudUploader.appsPayload(listOf(app()), emptyList(), listOf(s))
        assertTrue(body.contains("\"sources\""))
        assertTrue(body.contains("\"type\":\"GITHUB_SEARCH\""))
    }

    @Test
    fun `post_라운드트립_및_401`() {
        val lastAuth = AtomicReference("")
        val lastPath = AtomicReference("")
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            lastAuth.set(ex.requestHeaders.getFirst("Authorization") ?: "")
            lastPath.set(ex.requestURI.path)
            val code = if (lastAuth.get() == "Bearer t") 200 else 401
            val resp = "{\"ok\":true}".toByteArray()
            ex.sendResponseHeaders(code, resp.size.toLong())
            ex.responseBody.use { it.write(resp) }
        }
        srv.start()
        try {
            val base = "http://127.0.0.1:${srv.address.port}"
            val ok = CloudUploader.post(base, "t", "apps", "{\"drafts\":[]}")
            assertTrue(ok.ok)
            assertEquals(200, ok.code)
            assertEquals("/ingest/apps", lastPath.get())
            val bad = CloudUploader.post(base, "wrong", "apps", "{}")
            assertEquals(401, bad.code)
        } finally {
            srv.stop(0)
        }
    }
}
