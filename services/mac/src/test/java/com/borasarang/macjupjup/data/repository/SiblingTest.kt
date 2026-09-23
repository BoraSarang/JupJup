package com.borasarang.macjupjup.data.repository

import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.App
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiblingTest {

    private fun app(id: String, name: String, developer: String) = App(
        id = id, platform = "macOS", name = name, developer = developer,
        license = "FREE", price = 0.0, currency = "USD", category = "유틸리티",
        tags = null, trackId = null, repoFullName = null, homepageUrl = null,
        version = null, prevVersion = null, releaseNotesSummary = null,
        releaseNotes = null, releaseDate = null, descriptionSnippet = null,
        screenshotUrls = null, averageRating = null, ratingCount = null,
        stars = null, primaryLanguage = null, topics = null, iconUrl = null,
        descriptionKo = null, releaseNotesKo = null, sellerName = null,
        fileSize = null, minOs = null, contentRating = null, forks = null,
        issues = null, licenseName = null, firstSeenAt = 1, lastUpdatedAt = 2,
        isNew = true, sourceId = "s", licenseOverride = null,
        supportedLanguages = null,
    )

    private val repo = AppRepository(mockk(relaxed = true))

    @Test
    fun `수집원표기_형제흡수`() {
        val rows = listOf(app("mmb-lyrimuse", "Lyrimuse", "MacMenuBar"))
        val found = repo.findSiblingIn(rows, app("ph-lyrimuse", "Lyrimuse", "Khalil"))
        assertEquals("mmb-lyrimuse", found?.id)
    }

    @Test
    fun `실개발사_동명이앱_보호`() {
        val rows = listOf(app("a-memo", "Memo", "John"))
        val found = repo.findSiblingIn(rows, app("b-memo", "Memo", "Jane"))
        assertNull(found)
    }

    @Test
    fun `대소문자_무시`() {
        val rows = listOf(app("a", "Lyrimuse", "MacMenuBar"))
        val found = repo.findSiblingIn(rows, app("b", "LYRIMUSE", "Product Hunt"))
        assertEquals("a", found?.id)
    }

    @Test
    fun `후보다수_병합중단`() {
        val rows = listOf(
            app("a-memo", "Memo", "MacMenuBar"),
            app("b-memo", "Memo", "John"),
        )
        val found = repo.findSiblingIn(rows, app("c-memo", "Memo", "Product Hunt"))
        assertNull(found)
    }
}
