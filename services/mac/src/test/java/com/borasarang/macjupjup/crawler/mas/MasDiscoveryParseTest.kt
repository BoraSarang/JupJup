package com.borasarang.macjupjup.crawler.mas

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasDiscoveryParseTest {

    private val source = CrawlSource(
        id = "t", name = "t", type = "MAS_DISCOVERY", baseUrl = "https://itunes.apple.com",
        enabled = true, intervalHours = 24, intervalMinutes = 1440,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val searchJson = """
    {"resultCount":2,"results":[
      {"trackId":6471012328,"trackName":"SoundPaste","sellerName":"J.J. McCullough",
       "trackPrice":0.0,"currency":"USD","primaryGenreName":"Productivity","version":"1.4",
       "releaseNotes":"버그 수정","artworkUrl100":"https://example.com/icon.png",
       "averageUserRating":4.5,"userRatingCount":20,
       "screenshotUrls":["https://example.com/s1.png"],
       "trackViewUrl":"https://apps.apple.com/us/app/soundpaste/id6471012328",
       "currentVersionReleaseDate":"2024-02-19T00:00:00-07:00",
       "fileSizeBytes":"2831155","minimumOsVersion":"13.0","trackContentRating":"4+"},
      {"trackName":"이름없음"}
    ]}
    """.trimIndent()

    @Test
    fun `search_파싱_trackId_홈페이지null`() {
        val drafts = MacStoreDiscoveryCrawler(source).parseSearch(searchJson)
        assertEquals(1, drafts.size)
        val app = drafts[0].app
        assertEquals("SoundPaste", app.name)
        assertEquals(6471012328L, app.trackId)
        assertEquals("FREE", app.license)
        // T-060: 스토어에 홈페이지 없음 → 둔갑 금지
        assertNull(app.homepageUrl)
        assertNull(app.repoFullName)
        assertEquals("생산성", app.category)
        assertEquals("1.4", app.version)
        assertTrue(app.iconUrl?.contains("icon.png") == true)
        // 매핑 출처는 스토어 URL
        assertTrue(drafts[0].mappings[0].sourceUrl?.contains("id6471012328") == true)
    }
}
