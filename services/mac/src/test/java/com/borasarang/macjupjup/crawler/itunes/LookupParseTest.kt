package com.borasarang.macjupjup.crawler.itunes

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LookupParseTest {

    private val source = CrawlSource(
        id = "t", name = "t", type = "ITUNES_LOOKUP", baseUrl = "https://itunes.apple.com",
        enabled = true, intervalHours = 24, intervalMinutes = 1440,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val body = """
    {"resultCount":2,"results":[
      {"trackId":591560477,"version":"1.89.0",
       "releaseNotes":"AI 채팅 개선","currentVersionReleaseDate":"2026-09-01T00:00:00Z",
       "description":"Launcher app","screenshotUrls":["https://a","https://b"],
       "averageUserRating":4.8,"userRatingCount":1200,
       "primaryGenreName":"Productivity","trackViewUrl":"https://apps.apple.com/us/app/id591560477"},
      {"trackId":123,"description":null,"screenshotUrls":[]}
    ]}
    """.trimIndent()

    @Test
    fun `lookup_2건_null내성`() {
        val results = ITunesLookupPoller(
            source,
            repoProvider = { emptyList<com.borasarang.macjupjup.data.db.entity.App>() },
        ).parseLookup(body)
        assertEquals(2, results.size)
        val r = results[0]
        assertEquals(591560477L, r.trackId)
        assertEquals("1.89.0", r.version)
        assertEquals("AI 채팅 개선", r.releaseNotes)
        assertEquals(2, r.screenshotUrls?.size)
        assertEquals(4.8, r.averageRating!!, 0.001)
        assertEquals("Productivity", r.appleCategory)
        assertNotNull(r.releaseDate)
    }
}
