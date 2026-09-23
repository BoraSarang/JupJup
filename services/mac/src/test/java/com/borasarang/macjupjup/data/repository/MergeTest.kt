package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {

    private fun app(
        id: String = "a",
        license: String = "FREE",
        desc: String? = null,
        icon: String? = null,
        version: String? = null,
        category: String = "유틸리티",
        tags: String? = null,
        releaseNotesSummary: String? = null,
    ) = App(
        id = id, platform = "macOS", name = "N", developer = "D",
        license = license, price = 0.0, currency = "USD", category = category,
        tags = tags, trackId = null, repoFullName = null, homepageUrl = null,
        version = version, prevVersion = null, releaseNotesSummary = releaseNotesSummary,
        releaseNotes = null, releaseDate = null, descriptionSnippet = desc,
        screenshotUrls = null, averageRating = null, ratingCount = null,
        stars = null, primaryLanguage = null, topics = null, iconUrl = icon,
        descriptionKo = null, releaseNotesKo = null, sellerName = null,
        fileSize = null, minOs = null, contentRating = null, forks = null,
        issues = null, licenseName = null, firstSeenAt = 1, lastUpdatedAt = 2,
        isNew = true, sourceId = "s", licenseOverride = null,
        supportedLanguages = null,
    )

    private val repo = AppRepository(mockk(relaxed = true))

    @Test
    fun `null은_기존값유지`() {
        val merged = repo.mergeApps(
            app(desc = "old desc", icon = "old-icon", version = "1.0"),
            app(desc = null, icon = null, version = null),
        )
        assertEquals("old desc", merged.descriptionSnippet)
        assertEquals("old-icon", merged.iconUrl)
        assertEquals("1.0", merged.version)
    }

    @Test
    fun `설명은_카드용발췌_전문은_longDescription`() {
        val longBody = "much longer description here".repeat(50)
        val merged = repo.mergeApps(
            app(desc = "short", version = "1.0"),
            app(desc = longBody.take(300), version = "2.0",
                releaseNotesSummary = null).copy(longDescription = longBody),
        )
        assertTrue(merged.descriptionSnippet!!.length <= 300)
        assertEquals(longBody, merged.longDescription)
        assertEquals("1.0", merged.prevVersion)
        assertEquals("2.0", merged.version)
    }

    @Test
    fun `레거시긴_snippet은_전문으로_승계_발췌는_상한`() {
        val legacy = ("L".repeat(100) + " ").repeat(30).trim() // >300
        val merged = repo.mergeApps(
            app(desc = legacy),
            app(desc = null),
        )
        assertTrue(merged.descriptionSnippet!!.length <= 300)
        assertEquals(legacy, merged.longDescription)
    }

    @Test
    fun `draft_snippet이_현저히_짧으면_기존_유지`() {
        val good = "A well-written product description that explains features clearly. ".repeat(4).trim()
        val junk = "tmp"
        val merged = repo.mergeApps(app(desc = good), app(desc = junk))
        assertEquals(good, merged.descriptionSnippet)
        // draft가 더 길거나 동등하면 채택
        val newer = repo.mergeApps(app(desc = "short"), app(desc = "updated longer description that is better"))
        assertEquals("updated longer description that is better", newer.descriptionSnippet)
        // draft null → 기존 유지
        val keep = repo.mergeApps(app(desc = good), app(desc = null))
        assertEquals(good, keep.descriptionSnippet)
    }

    @Test
    fun `preferSnippet_단위`() {
        assertEquals("keep", repo.preferSnippet("keep", null))
        assertEquals("keep", repo.preferSnippet("keep", "  "))
        assertEquals("new", repo.preferSnippet(null, "new"))
        assertEquals(
            "much longer existing body text here",
            repo.preferSnippet("much longer existing body text here", "x"),
        )
        assertEquals("draft wins", repo.preferSnippet("old", "draft wins"))
    }

    @Test
    fun `라이선스_우선순위`() {
        assertEquals("PAID", repo.mergeApps(app(license = "FREE"), app(license = "PAID")).license)
        assertEquals("OSS", repo.mergeApps(app(license = "PAID"), app(license = "OSS")).license)
    }

    @Test
    fun `카테고리유지_태그합집합`() {
        val merged = repo.mergeApps(
            app(category = "개발", tags = "MenuBar"),
            app(category = "유틸리티", tags = "AI-Agent"),
        )
        assertEquals("개발", merged.category)
        assertEquals("MenuBar,AI-Agent", merged.tags)
    }

    @Test
    fun `isNew_firstSeen_유지`() {
        val merged = repo.mergeApps(
            app(id = "x").copy(firstSeenAt = 100, isNew = false),
            app(id = "x").copy(firstSeenAt = 200, isNew = true),
        )
        assertEquals(100, merged.firstSeenAt)
        assertEquals(false, merged.isNew)
        assertNull(merged.prevVersion)
    }

    @Test
    fun `버전bump_NEW해제`() {
        val merged = repo.mergeApps(
            app(id = "x", version = "1.0").copy(isNew = true),
            app(id = "x", version = "2.0"),
        )
        assertEquals(false, merged.isNew)
        assertEquals("1.0", merged.prevVersion)
    }

    @Test
    fun `supportedLanguages_draft우선_null은기존유지`() {
        assertEquals(
            "en,ko",
            repo.mergeApps(
                app().copy(supportedLanguages = "en"),
                app().copy(supportedLanguages = "en,ko"),
            ).supportedLanguages,
        )
        assertEquals(
            "en,ja",
            repo.mergeApps(
                app().copy(supportedLanguages = "en,ja"),
                app().copy(supportedLanguages = null),
            ).supportedLanguages,
        )
    }
}
