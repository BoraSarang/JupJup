package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `설명은_긴쪽_버전변경시_새노트`() {
        val merged = repo.mergeApps(
            app(desc = "short", version = "1.0"),
            app(desc = "much longer description here", version = "2.0",
                releaseNotesSummary = null),
        )
        assertEquals("much longer description here", merged.descriptionSnippet)
        assertEquals("1.0", merged.prevVersion)
        assertEquals("2.0", merged.version)
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
}
