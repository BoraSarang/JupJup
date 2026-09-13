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
        sourceId = null, licenseOverride = null,
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
