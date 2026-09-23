package com.borasarang.macjupjup.server

import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.repository.AppListItem
import com.borasarang.macjupjup.data.repository.AppWithSourceList
import com.borasarang.macjupjup.data.repository.SettingsData
import com.borasarang.macjupjup.data.repository.toView
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: 분리된 서버 JSON 매퍼 golden 테스트 (이동 검증용, 동작 동결) */
class MacServerJsonTest {

    private fun app() = App(
        id = "a1", platform = "macOS", name = "TestApp", developer = "Dev",
        license = "OSS", price = 0.0, currency = "USD", category = "유틸리티",
        tags = null, trackId = null, repoFullName = "dev/test", homepageUrl = null,
        version = "1.0", prevVersion = null, releaseNotesSummary = "요약",
        releaseDate = null, descriptionSnippet = null, iconUrl = null,
        descriptionKo = null, releaseNotes = null, releaseNotesKo = null,
        sellerName = null, fileSize = null, minOs = null, contentRating = null,
        forks = null, issues = null, licenseName = null, screenshotUrls = null,
        averageRating = null, ratingCount = null, stars = null, primaryLanguage = null,
        topics = null, firstSeenAt = 1000L, lastUpdatedAt = 2000L, isNew = true,
        sourceId = null, licenseOverride = null, supportedLanguages = null,
    )

    @Test
    fun appsJson_빈목록_envelope() {
        assertEquals(
            """{"apps":[],"total":0,"page":1,"pageSize":50}""",
            appsJson(emptyList(), 0, 1, 50),
        )
    }

    @Test
    fun appsJson_널필드_생략_출처포함() {
        val json = Json.parseToJsonElement(
            appsJson(listOf(AppListItem(app(), "Src", "https://s.test")), 1, 1, 50),
        ).toString()
        assertTrue(json.contains(""""name":"TestApp""""))
        assertTrue(json.contains(""""sourceName":"Src""""))
        assertFalse(json.contains("tags"))
        assertFalse(json.contains("trackId"))
    }

    @Test
    fun detailJson_버전빈목록_sources키유지() {
        val json = Json.parseToJsonElement(
            detailJson(AppWithSourceList(app(), emptyList(), emptyList())),
        ).toString()
        assertTrue(json.contains(""""sources":[]"""))
        assertTrue(json.contains(""""versions":[]"""))
        assertFalse(json.contains("sourceUrl"))
    }

    @Test
    fun detailJson_epic태그는_에픽출처_sourceUrl() {
        val epic = com.borasarang.macjupjup.data.db.entity.AppSourceMapping(
            appId = "a1", sourceName = "Epic 주간 무료 게임",
            sourceUrl = "https://store.epicgames.com/en-US/p/foo", fetchedAt = 1L,
        )
        val steam = com.borasarang.macjupjup.data.db.entity.AppSourceMapping(
            appId = "a1", sourceName = "Steam 무료 맥 게임",
            sourceUrl = "https://store.steampowered.com/app/1/", fetchedAt = 2L,
        )
        val json = Json.parseToJsonElement(
            detailJson(
                AppWithSourceList(
                    app().copy(tags = "game,epic,epic-weekly-free"),
                    listOf(steam, epic),
                    emptyList(),
                ),
            ),
        ).toString()
        // sources 배열엔 두 출처 모두, 최상단 sourceUrl은 에픽만
        assertTrue(json.contains("https://store.epicgames.com/en-US/p/foo"))
        assertTrue(json.contains("store.steampowered.com"))
        val top = Regex(""""sourceUrl":"([^"]+)"""").find(json)?.groupValues?.get(1)
        assertEquals("https://store.epicgames.com/en-US/p/foo", top)
    }

    @Test
    fun preferredMapping_steam우선_에픽태그는_에픽() {
        val epic = com.borasarang.macjupjup.data.db.entity.AppSourceMapping(
            appId = "a", sourceName = "Epic", sourceUrl = "https://store.epicgames.com/p/x", fetchedAt = 1L,
        )
        val steam = com.borasarang.macjupjup.data.db.entity.AppSourceMapping(
            appId = "a", sourceName = "Steam", sourceUrl = "https://store.steampowered.com/app/9/", fetchedAt = 2L,
        )
        val out = com.borasarang.macjupjup.data.repository.preferredMapping(
            "game,epic,epic-weekly-free",
            listOf(steam, epic),
        )
        assertEquals("https://store.epicgames.com/p/x", out?.sourceUrl)
        val outSteam = com.borasarang.macjupjup.data.repository.preferredMapping(
            "game,steam,steam-appid:9",
            listOf(epic, steam),
        )
        assertEquals("https://store.steampowered.com/app/9/", outSteam?.sourceUrl)
    }

    @Test
    fun appElement_supportedLanguages_노출_널이면생략() {
        val withLang = Json.parseToJsonElement(
            appsJson(listOf(AppListItem(app().copy(supportedLanguages = "en,ko"), null, null)), 1, 1, 50),
        ).toString()
        assertTrue(withLang.contains(""""supportedLanguages":"en,ko""""))
        val withoutLang = Json.parseToJsonElement(
            appsJson(listOf(AppListItem(app(), null, null)), 1, 1, 50),
        ).toString()
        assertFalse(withoutLang.contains("supportedLanguages"))
    }

    @Test
    fun appElement_레거시긴_snippet은_발췌상한_longDescription_승계() {
        val legacy = "L".repeat(8000)
        val json = Json.parseToJsonElement(
            appsJson(listOf(AppListItem(app().copy(descriptionSnippet = legacy), null, null)), 1, 1, 50),
        ).toString()
        val snip = Regex(""""descriptionSnippet":"([^"]+)"""").find(json)?.groupValues?.get(1)
        val long = Regex(""""longDescription":"([^"]+)"""").find(json)?.groupValues?.get(1)
        assertEquals(300, snip!!.length)
        assertEquals(8000, long!!.length)
    }

    @Test
    fun settingsJson_토큰값_미노출_여부만() {
        val json = settingsJson(
            SettingsData(port = 3000, retentionDays = 30, autoStart = true,
                watchdogIntervalSec = 60, githubToken = "secret").toView(),
        )
        assertTrue(json.contains(""""githubTokenSet":true"""))
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("githubToken\""))
    }
}
