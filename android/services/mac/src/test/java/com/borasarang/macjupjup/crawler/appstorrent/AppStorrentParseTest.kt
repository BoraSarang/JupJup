package com.borasarang.macjupjup.crawler.appstorrent

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.category.GameGenres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class AppStorrentParseTest {

    private val gamesSource = CrawlSource(
        id = "appstorrent_games", name = "AppStorrent 게임", type = "APPSTORRENT_GAMES",
        baseUrl = "https://appstorrent.ru",
        enabled = true, intervalHours = 24, intervalMinutes = 1440,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val progSource = CrawlSource(
        id = "appstorrent_programs", name = "AppStorrent 프로그램", type = "APPSTORRENT_PROGRAMS",
        baseUrl = "https://appstorrent.ru",
        enabled = true, intervalHours = 24, intervalMinutes = 1440,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val gamesListHtml = """
    <html><body>
      <article class="games-item ">
        <div class="container">
          <div class="size"><div class="marker caption-2 tippy" title="Размер файлов: 365 МБ">365 МБ</div></div>
          <div class="cover">
            <a href="https://appstorrent.ru/783-rimworld.html" class="link-title">
              <img src="/uploads/posts/2021-11/rimworld-min.jpg" alt="RimWorld Cover">
            </a>
          </div>
          <div class="icon">
            <img src="/uploads/posts/2026-09/rimworld.webp" class="xfieldimage shop-icon" alt="RimWorld Icon">
          </div>
          <div class="subtitle">
            <a href="https://appstorrent.ru/783-rimworld.html"><h2 class="body-2">RimWorld</h2></a>
          </div>
          <div class="info">
            <a href="https://appstorrent.ru/783-rimworld.html" class="link-title">
              <h2 class="body-2">RimWorld</h2>
            </a>
            <span class="caption-1 category">
              <span class="tags_plugin">
                <a href="https://appstorrent.ru/games/strategy/">Стратегии</a>
                <a href="https://appstorrent.ru/games/">Игры</a>
              </span>
            </span>
            <div class="caption-1 version">1.6.4</div>
            <span class="caption-1 architecture">ARM, x86 (64-bit)</span>
          </div>
        </div>
      </article>
      <article class="games-item ">
        <div class="container">
          <div class="subtitle">
            <a href="/781-disco-elysium.html"><h2 class="body-2">Disco Elysium</h2></a>
          </div>
          <div class="info">
            <span class="caption-1 category">
              <span class="tags_plugin">
                <a href="/games/rpg/">RPG</a><a href="/games/">Игры</a>
              </span>
            </span>
            <div class="caption-1 version">1.4</div>
          </div>
        </div>
      </article>
      <div class="lastcomm"><a href="https://appstorrent.ru/999-lastcomm.html">Last</a></div>
    </body></html>
    """.trimIndent()

    private val progsListHtml = """
    <html><body>
      <article class="soft-item ">
        <div class="container">
          <div class="size"><div class="marker caption-2 tippy" title="Размер файлов: 421 МБ">421 МБ</div></div>
          <div class="icon">
            <img src="/uploads/posts/2022-12/camtasia_icon.webp" class="xfieldimage shop-icon" alt="Camtasia Icon">
          </div>
          <div class="subtitle">
            <a href="https://appstorrent.ru/428-camtasia.html"><h2 class="body-2">Camtasia</h2></a>
          </div>
          <div class="info">
            <div class="caption-1 version">2026.2.3</div>
            <span class="caption-1 architecture">ARM, x86 (64-bit)</span>
            <span class="caption-1 category">Запись экрана</span>
          </div>
        </div>
      </article>
      <article class="soft-item ">
        <div class="subtitle">
          <a href="/2551-proxyman.html"><h2 class="body-2">Proxyman</h2></a>
        </div>
        <div class="info">
          <div class="caption-1 version">26.0.0</div>
          <span class="caption-1 category">Исследование сети</span>
        </div>
      </article>
      <div class="lastcomm"><a href="https://appstorrent.ru/998-news.html">News</a></div>
    </body></html>
    """.trimIndent()

    private val challengeHtml = """<!DOCTYPE html><html><head><title>Just a moment...</title></head><body>challenge</body></html>"""

    private val detailHtml = """
    <html><head><meta property="og:image" content="https://cdn.appstorrent.ru/rw-og.jpg"></head>
    <body>
      <article class="main-post">
        <h1>RimWorld 1.6</h1>
        <span class="tags_plugin">
          <a href="https://appstorrent.ru/games/strategy/">Стратегии</a>
          <a href="https://appstorrent.ru/games/">Игры</a>
        </span>
        <div id="tabs-1">
          <div class="body-content">
            <p>Колонисты выживают на чужой планете. Strategy sandbox sim.</p>
            <p>Скачать торрент magnet:?xt=urn:btih:ABCDEF123456</p>
            <a href="https://example.com/game.torrent">torrent</a>
          </div>
        </div>
        <div id="tabs-3">
          <div class="screenshots">
            <img src="/uploads/posts/ss/1.jpg">
            <img src="/uploads/posts/ss/2.jpg">
            <img data-src="https://cdn.appstorrent.ru/3.png">
            <img src="magnet:x">
          </div>
        </div>
      </article>
    </body></html>
    """.trimIndent()

    @Test
    fun `목록_games_item에서_게임과_링크_파싱`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val drafts = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        assertEquals(2, drafts.size)
        val rim = drafts.first { it.app.name.contains("RimWorld") }
        assertEquals("https://appstorrent.ru/783-rimworld.html", rim.app.homepageUrl)
        assertEquals("게임", rim.app.category)
        assertEquals("1.6.4", rim.app.version)
        assertNotNull(rim.app.iconUrl)
        assertTrue(rim.app.tags!!.contains("appstorrent"))
        assertTrue(rim.app.tags!!.contains("game"))
        assertTrue(rim.app.tags!!.contains("전략"))
        assertTrue(rim.app.license == "FREE")
        assertTrue(rim.app.topics!!.contains("genre:strategy"))
    }

    @Test
    fun `프로그램_soft-item_게임태그_없음`() {
        val c = AppStorrentHtmlCrawler(progSource, AppStorrentHtmlCrawler.Mode.PROGRAMS)
        val drafts = c.parseList(progsListHtml, "https://appstorrent.ru/programs/", AppStorrentHtmlCrawler.Mode.PROGRAMS)
        assertEquals(2, drafts.size)
        drafts.forEach { d ->
            val tags = d.app.tags.orEmpty()
            assertTrue(tags.contains("appstorrent"))
            assertFalse(tags.split(",").contains("game"))
            assertTrue(d.app.category != "게임")
        }
        val camtasia = drafts.first { it.app.name.contains("Camtasia") }
        assertEquals("2026.2.3", camtasia.app.version)
        assertEquals("https://appstorrent.ru/428-camtasia.html", camtasia.app.homepageUrl)
    }

    @Test
    fun `게임목록에서_lastcomm_제외`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val drafts = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        drafts.forEach { d ->
            assertFalse(d.app.homepageUrl!!.contains("lastcomm"))
            assertFalse(d.app.name.contains("Last"))
        }
    }

    @Test
    fun `프로그램목록에서_게임모드_셀렉터_미수집`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val drafts = c.parseList(progsListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `상세_tabs1_다운로드URI_제거_본문만_보존`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val list = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        val rim = list.first { it.app.name.contains("RimWorld") }
        val out = c.enrichDetail(rim, detailHtml)!!
        val desc = out.app.descriptionSnippet.orEmpty()
        assertTrue(desc.contains("Колонисты"))
        assertFalse(desc.contains("magnet:?"))
        assertFalse(desc.contains("torrent"))
        assertFalse(desc.contains("btih"))
        assertEquals("RimWorld 1.6", out.app.name)
        assertNotNull(out.app.iconUrl)
        val shots = out.app.screenshotUrls.orEmpty()
        assertTrue(shots.contains("/uploads/posts/ss/1.jpg"))
        assertFalse(shots.contains("magnet"))
    }

    @Test
    fun `stripDownloadText_마그넷과_토렌트_제거`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val s = c.stripDownloadText("Game good magnet:?xt=urn:btih:XX and file.torrent link")
        assertFalse(s.contains("magnet"))
        assertFalse(s.contains(".torrent"))
        assertTrue(s.contains("Game good"))
    }

    @Test
    fun `CF_챌린지_HTML_감지`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val drafts = c.parseList(challengeHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `차단_메시지는_CF또는_HTTP_403으로_판단`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        assertTrue(c.parseList(challengeHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES).isEmpty())
        assertTrue(c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES).isNotEmpty())
    }

    @Test
    fun `장르_슬러그_GameGenres_매핑`() {
        assertEquals("액션", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("action"))
        assertEquals("RPG", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("rpg"))
        assertEquals("호러·서바이벌", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("horror"))
        assertEquals("캐주얼", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("arcade"))
        assertEquals("시뮬레이션", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("simulator"))
        assertEquals("어드벤처", com.borasarang.macjupjup.util.category.GameGenres.fromSlug("platformer"))
    }

    @Test
    fun `출처링크는_appstorrent_상세만`() {
        val c = AppStorrentHtmlCrawler(gamesSource, AppStorrentHtmlCrawler.Mode.GAMES)
        val drafts = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
        drafts.forEach { d ->
            val url = d.app.homepageUrl.orEmpty()
            assertTrue(url.startsWith("https://appstorrent.ru/") || url.startsWith("/"))
            assertFalse(url.contains(".torrent"))
            assertFalse(url.startsWith("magnet:"))
        }
    }

    @Test
    fun `Googlebot_UA로_CF_우회_헤더_사용`() {
        val ua = AppStorrentHtmlCrawler.BROWSER_HEADERS["User-Agent"].orEmpty()
        assertTrue(ua.contains("Googlebot"))
        assertFalse(ua.contains("Chrome/"))
    }

    @Test
    fun `상세보강은_공백_본문을_먼저_선택`() {
        val c = AppStorrentHtmlCrawler(progSource, AppStorrentHtmlCrawler.Mode.PROGRAMS)
        val withBody = c.parseList(progsListHtml, "https://appstorrent.ru/programs/", AppStorrentHtmlCrawler.Mode.PROGRAMS)
            .first().let { d -> d.copy(app = d.app.copy(descriptionSnippet = "already", longDescription = "body")) }
        val empty = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES)
            .first().copy(app = c.parseList(gamesListHtml, "https://appstorrent.ru/games/", AppStorrentHtmlCrawler.Mode.GAMES).first().app.copy(descriptionSnippet = null, longDescription = null))
        val onlySnip = withBody.copy(app = withBody.app.copy(longDescription = null))
        val ordered = c.prioritizeForDetail(listOf(withBody, empty, onlySnip))
        assertEquals(empty.app.id, ordered.first().app.id)
        assertEquals(withBody.app.id, ordered.last().app.id)
        assertEquals(60, AppStorrentHtmlCrawler.DETAIL_LIMIT)
    }

    @Test
    fun `목록초안은_DB본문보유_id로_우선순위_결정`() {
        val c = AppStorrentHtmlCrawler(progSource, AppStorrentHtmlCrawler.Mode.PROGRAMS)
        val drafts = c.parseList(progsListHtml, "https://appstorrent.ru/programs/", AppStorrentHtmlCrawler.Mode.PROGRAMS)
        drafts.forEach { d ->
            assertNull(d.app.descriptionSnippet)
            assertNull(d.app.longDescription)
        }
        val alreadyInDb = setOf(drafts.first().app.id)
        val ordered = c.prioritizeForDetail(drafts, alreadyInDb)
        assertEquals(drafts.last().app.id, ordered.first().app.id)
        assertEquals(drafts.first().app.id, ordered.last().app.id)
        val noDbHint = c.prioritizeForDetail(drafts, emptySet())
        assertEquals(drafts.map { it.app.id }, noDbHint.map { it.app.id })
    }

    @Test
    fun `체크포인트_주기는_10건`() {
        assertEquals(10, AppStorrentHtmlCrawler.CHECKPOINT_EVERY)
        assertEquals(60, AppStorrentHtmlCrawler.DETAIL_LIMIT)
    }
}
