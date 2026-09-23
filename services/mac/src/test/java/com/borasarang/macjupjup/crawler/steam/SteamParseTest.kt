package com.borasarang.macjupjup.crawler.steam

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamParseTest {

    private val source = CrawlSource(
        id = "steam_free_mac", name = "Steam 무료 맥 게임", type = "STEAM_FREETOMAC",
        baseUrl = "https://store.steampowered.com",
        enabled = true, intervalHours = 6, intervalMinutes = 360,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private val rowMac = """
    <a href="https://store.steampowered.com/app/4883240/Fuel_Up/" data-ds-appid="4883240"
       data-ds-tagids="[599,492,122]" class="search_result_row">
      <div class="search_capsule"><img src="https://cdn.example/capsule.jpg"></div>
      <div class="search_name"><span class="title">Fuel Up</span></div>
      <div class="search_platforms"><span class="platform_img win"></span><span class="platform_img mac"></span></div>
      <div class="search_released">22 Sep, 2026</div>
      <div class="search_price_discount_combined"><div class="discount_final_price free">Free</div></div>
    </a>
    """.trimIndent()

    private val rowNoMac = """
    <a href="https://store.steampowered.com/app/1/" data-ds-appid="1" data-ds-tagids="[19]"
       class="search_result_row">
      <span class="title">Windows Only</span>
      <div class="search_platforms"><span class="platform_img win"></span></div>
    </a>
    """.trimIndent()

    private fun payload(vararg rows: String) =
        """{"success":1,"results_html":"${esc(rows.joinToString("\n"))}"}"""

    @Test
    fun `parse_맥플랫폼_행만_파싱`() {
        val c = SteamFreeMacCrawler(source)
        val drafts = c.parseSearch(payload(rowMac))
        assertEquals(1, drafts.size)
        val app = drafts[0].app
        assertEquals("Fuel Up", app.name)
        assertEquals("Steam", app.developer)
        assertEquals("게임", app.category)
        assertEquals("FREE", app.license)
        assertEquals(0.0, app.price, 0.0)
        assertNotNull(app.tags)
        assertTrue(app.tags!!.contains("game"))
        assertTrue(app.tags!!.contains("시뮬레이션"))
        assertTrue(app.tags!!.contains("steam"))
        assertEquals("https://store.steampowered.com/app/4883240/Fuel_Up/", drafts[0].mappings[0].sourceUrl)
        assertEquals("https://cdn.example/capsule.jpg", app.iconUrl)
        assertNotNull(app.releaseDate)
    }

    @Test
    fun `parse_mac없는행_스킵`() {
        val c = SteamFreeMacCrawler(source)
        val drafts = c.parseSearch(payload(rowNoMac))
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `parse_빈결과_빈목록`() {
        val c = SteamFreeMacCrawler(source)
        assertTrue(c.parseSearch("""{"success":1,"results_html":""}""").isEmpty())
        assertTrue(c.parseSearch("""{"success":0}""").isEmpty())
    }

    @Test
    fun `enrich_macfalse면_null`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """{"4883240":{"success":true,"data":{"platforms":{"mac":false},"name":"Fuel Up"}}}"""
        assertNull(c.enrich(draft, detail))
    }

    @Test
    fun `enrich_장르와헤더이미지_보강`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """
        {"4883240":{"success":true,"data":{
          "platforms":{"mac":true,"windows":true},
          "name":"Fuel Up",
          "header_image":"https://cdn.example/header.jpg",
          "short_description":"Short desc",
          "genres":[{"id":"23","description":"Indie"},{"id":"37","description":"Free To Play"}]
        }}}
        """.trimIndent()
        val out = c.enrich(draft, detail)
        assertNotNull(out)
        assertEquals("Fuel Up", out!!.app.name)
        assertEquals("인디", out.app.tags!!.split(",").first { it in setOf("인디", "시뮬레이션") })
        assertEquals("https://cdn.example/header.jpg", out.app.iconUrl)
        assertTrue(out.app.tags!!.contains("게임") || out.app.category == "게임")
        assertEquals("게임", out.app.category)
    }

    @Test
    fun `enrich_supportedLanguages_객체키_파싱`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """
        {"4883240":{"success":true,"data":{
          "platforms":{"mac":true},
          "name":"Fuel Up",
          "supported_languages":{"english":"Full Audio, Subtitles","koreana":"Subtitles"}
        }}}
        """.trimIndent()
        val out = c.enrich(draft, detail)
        assertEquals("en,ko", out!!.app.supportedLanguages)
    }

    @Test
    fun `enrich_supportedLanguages_HTML문자열_파싱`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """
        {"4883240":{"success":true,"data":{
          "platforms":{"mac":true},
          "name":"Fuel Up",
          "supported_languages":"<strong>English</strong>: Full Audio, <strong>Korean</strong>: Subtitles"
        }}}
        """.trimIndent()
        val out = c.enrich(draft, detail)
        assertEquals("en,ko", out!!.app.supportedLanguages)
    }

    @Test
    fun `enrich_supportedLanguages_없으면_기존유지`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """
        {"4883240":{"success":true,"data":{
          "platforms":{"mac":true},
          "name":"Fuel Up"
        }}}
        """.trimIndent()
        assertEquals(null, c.enrich(draft, detail)!!.app.supportedLanguages)
    }

    @Test
    fun `parseReleaseDate_영문날짜`() {
        val c = SteamFreeMacCrawler(source)
        assertNotNull(c.parseReleaseDate("22 Sep, 2026"))
        assertNotNull(c.parseReleaseDate("Sep 22, 2026"))
        assertNull(c.parseReleaseDate("not-a-date"))
    }

    @Test
    fun `enrich_소개본문_스크린샷_요건_보강`() {
        val c = SteamFreeMacCrawler(source)
        val draft = c.parseSearch(payload(rowMac)).first()
        val detail = """
        {"4883240":{"success":true,"data":{
          "platforms":{"mac":true,"windows":true},
          "name":"Fuel Up",
          "header_image":"https://cdn.example/header.jpg",
          "short_description":"짧은 소개",
          "about_the_game":"<h2 class=\"bb_tag\">Full About</h2><p>긴 본문 문단.</p><ul><li>특징 하나</li></ul>",
          "detailed_description":"<p>detailed</p>",
          "screenshots":[
            {"id":0,"path_thumbnail":"https://cdn.example/s0.jpg","path_full":"https://cdn.example/full0.jpg"},
            {"id":1,"path_thumbnail":"https://cdn.example/s1.jpg","path_full":"https://cdn.example/full1.jpg"}
          ],
          "mac_requirements":[],
          "pc_requirements":{"minimum":"<strong>최소:</strong><br><ul class=\"bb_ul\"><li>OS: Windows 10</li></ul>"},
          "supported_languages":{"english":"Full Audio","koreana":"Subtitles"}
        }}}
        """.trimIndent()
        val out = c.enrich(draft, detail)!!
        val snip = out.app.descriptionSnippet!!
        val desc = out.app.longDescription!!
        // 소개 발췌 = 짧은 설명, 전문 = README·SYSREQ 포함 전체
        assertTrue(snip.contains("짧은 소개"))
        assertTrue(!snip.contains(SteamFreeMacCrawler.README_MARKER))
        assertTrue(desc.contains(SteamFreeMacCrawler.README_MARKER))
        assertTrue(desc.contains("Full About"))
        assertTrue(desc.contains(SteamFreeMacCrawler.SYSREQ_MARKER))
        assertTrue(desc.contains("macOS"))
        assertTrue(desc.contains("Windows 10"))
        assertEquals(
            "https://cdn.example/s0.jpg\nhttps://cdn.example/s1.jpg",
            out.app.screenshotUrls,
        )
        assertEquals("en,ko", out.app.supportedLanguages)
        assertEquals("macOS", out.app.minOs)
        assertTrue(out.app.tags!!.contains("windows"))
        assertTrue(desc.length <= SteamFreeMacCrawler.GAME_DESC_MAX)
    }

    @Test
    fun `aboutHtmlToMarkdown_태그제거_마크다운`() {
        val c = SteamFreeMacCrawler(source)
        val md = c.aboutHtmlToMarkdown(
            "<h2>Title</h2><p>Body <strong>bold</strong>.</p><ul><li>item</li></ul>",
        )
        assertTrue(md.contains("### Title"))
        assertTrue(md.contains("Body"))
        assertTrue(md.contains("- item"))
        assertFalse(md.contains("<p>"))
    }

    @Test
    fun `composeDescription_SYSREQ없으면_마커없음`() {
        val c = SteamFreeMacCrawler(source)
        val d = c.composeDescription("short", "about body", sysReqHtml = "", fallback = null)!!
        assertTrue(d.contains(SteamFreeMacCrawler.README_MARKER))
        assertFalse(d.contains(SteamFreeMacCrawler.SYSREQ_MARKER))
    }

    @Test
    fun `parseScreenshots_빈배열_null`() {
        val c = SteamFreeMacCrawler(source)
        assertNull(c.parseScreenshots(kotlinx.serialization.json.JsonArray(emptyList())))
        assertNull(c.parseScreenshots(null))
    }
}
