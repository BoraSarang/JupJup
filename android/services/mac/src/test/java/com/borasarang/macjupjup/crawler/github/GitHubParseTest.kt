package com.borasarang.macjupjup.crawler.github

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubParseTest {

    private val source = CrawlSource(
        id = "t", name = "t", type = "GITHUB_SEARCH", baseUrl = "https://api.github.com",
        enabled = true, intervalHours = 6, intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val searchJson = """
    {"total_count":2,"items":[
      {"full_name":"owner/RaycastClone","name":"RaycastClone",
       "owner":{"login":"owner"},"description":"macOS launcher",
       "stargazers_count":120,"language":"Swift","topics":["macos","launcher"],
       "html_url":"https://github.com/owner/RaycastClone","homepage":"",
       "pushed_at":"2026-09-01T00:00:00Z","default_branch":"main"},
      {"full_name":"o/NoDesc","name":"NoDesc","owner":{"login":"o"},
       "description":null,"stargazers_count":5,"language":null,"topics":[],
       "html_url":"https://github.com/o/NoDesc","homepage":null,
       "pushed_at":"bad-date","default_branch":"main"}
    ]}
    """.trimIndent()

    @Test
    fun `search_2건파싱_null내성`() {
        val drafts = GitHubSearchCrawler(source).parseRepos(searchJson)
        assertEquals(2, drafts.size)
        val first = drafts[0].app
        assertEquals("owner/RaycastClone", first.repoFullName)
        assertEquals("OSS", first.license)
        assertEquals(120, first.stars)
        assertEquals("Swift", first.primaryLanguage)
        assertEquals("생산성", first.category)
        assertNotNull(drafts[1].app)
    }

    @Test
    fun `homepage_빈값은null_실값유지_T072`() {
        val drafts = GitHubSearchCrawler(source).parseRepos(searchJson)
        // homepage "" / null → repo URL로 둔갑 금지, 공란 유지
        assertNull(drafts[0].app.homepageUrl)
        assertNull(drafts[1].app.homepageUrl)
        val withHome = GitHubSearchCrawler(source).parseRepos(
            """{"total_count":1,"items":[{"full_name":"aliyar/FetchBar","name":"FetchBar",
            "owner":{"login":"aliyar"},"description":"d","stargazers_count":10,
            "html_url":"https://github.com/aliyar/FetchBar",
            "homepage":"https://fetchbar.greatpixels.com/","default_branch":"main"}]}""",
        )
        assertEquals("https://fetchbar.greatpixels.com/", withHome[0].app.homepageUrl)
    }

    @Test
    fun `releases_최신태그`() {
        val body = """[{"tag_name":"v1.2.0","draft":false,
          "body":"새 기능 추가","html_url":"https://github.com/o/r/releases/tag/v1.2.0"},
          {"tag_name":"v1.1.0","draft":false,"body":"이전","html_url":"x"}]"""
        val latest = GitHubReleasesCrawler(source, { emptyList() }).parseLatestRelease(body)
        assertNotNull(latest)
        assertEquals("v1.2.0", latest!!.tag)
        assertEquals("새 기능 추가", latest.notes)
    }

    @Test
    fun `releases_없으면_null`() {
        val latest = GitHubReleasesCrawler(source, { emptyList() }).parseLatestRelease("[]")
        assertNull(latest)
    }

    @Test
    fun `릴리즈노트_HTML살균_T143`() {
        val crawler = GitHubReleasesCrawler(source, { emptyList() })
        val cleaned = crawler.cleanNotes("## 변경\n\n<img src=\"x.png\">\n\n- <b>굵게</b> 수정\n\n<!-- hi -->\n\na < b 비교")
        assertNotNull(cleaned)
        assertTrue(!cleaned!!.contains("<img"))
        assertTrue(!cleaned.contains("<b>"))
        assertTrue(!cleaned.contains("<!--"))
        assertTrue(cleaned.contains("## 변경"))
        assertTrue(cleaned.contains("- 굵게 수정"))
        assertTrue(cleaned.contains("a < b 비교"))
        assertNull(crawler.cleanNotes("   "))
        assertNull(crawler.cleanNotes(null))
    }

    @Test
    fun `epoch_파싱`() {
        assertTrue((GitHubSearchCrawler.parseEpoch("2026-09-01T00:00:00Z") ?: 0) > 0)
        assertNull(GitHubSearchCrawler.parseEpoch("bad-date"))
    }

    @Test
    fun `본문없는_id_우선_정렬_동순위는_stars`() {
        val c = GitHubSearchCrawler(source)
        val a = GitHubSearchCrawler(source).parseRepos(searchJson)
        // bodyIds에 이미 보유 id → 뒤로. 없는 id → 앞으로.
        val withBody = a[0].app.id // RaycastClone (본문 보유로 가정)
        val prioritized = c.prioritizeForReadme(a, setOf(withBody))
        assertEquals("NoDesc", prioritized[0].app.name)
        assertEquals("RaycastClone", prioritized[1].app.name)
        // 동순위(둘 다 본문 없음)면 stars desc
        val emptySetOrder = c.prioritizeForReadme(a, emptySet())
        assertEquals("RaycastClone", emptySetOrder[0].app.name) // 120 stars > 5
    }

    @Test
    fun `README_첫줄_배지는_snippet_무시`() {
        val c = GitHubSearchCrawler(source)
        assertTrue(c.isBadgeOrHeading("# TokenMini"))
        assertTrue(c.isBadgeOrHeading("[![CI](https://img.shields.io/...)](...)"))
        assertTrue(c.isBadgeOrHeading("![icon](https://example.com/i.png)"))
        assertTrue(c.isBadgeOrHeading("<div align=\"center\">"))
        assertTrue(c.isBadgeOrHeading("https://github.com/o/r"))
        assertFalse(c.isBadgeOrHeading("Free AI usage monitor for Mac."))
    }

    @Test
    fun `체크포인트_주기는_10건`() {
        assertEquals(10, GitHubSearchCrawler.CHECKPOINT_EVERY)
        assertEquals(3, GitHubSearchCrawler.RATE_LIMIT_ABORT)
    }

    @Test
    fun `백필_성공은전문주입_404는_lastUpdatedAt회전_미시도는제외`() {
        val c = GitHubSearchCrawler(source)
        val drafts = GitHubSearchCrawler(source).parseRepos(searchJson)
        val ok = drafts[0]
        val nf = drafts[1]
        val untried = nf.copy(app = nf.app.copy(id = "other", repoFullName = "o/Other"))
        val out = c.buildMissingOut(
            missing = listOf(ok, nf, untried),
            readmeMap = mapOf(ok.app.repoFullName!! to "# Body\n\nLong README content here"),
            rotateRepos = setOf(nf.app.repoFullName!!),
            rotateAt = 7777L,
        )
        assertEquals(2, out.size)
        val enriched = out.first { it.app.id == ok.app.id }
        assertEquals("# Body\n\nLong README content here", enriched.app.longDescription)
        val rotated = out.first { it.app.id == nf.app.id }
        assertEquals(7777L, rotated.app.lastUpdatedAt)
        assertNull(rotated.app.longDescription)
        assertTrue(out.none { it.app.id == untried.app.id })
    }
}
