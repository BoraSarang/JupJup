package com.borasarang.macjupjup.crawler.chart

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChartParseTest {

    private val source = CrawlSource(
        id = "t", name = "t", type = "CHART_RSS", baseUrl = "https://itunes.apple.com",
        enabled = true, intervalHours = 24, intervalMinutes = 1440,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val feed = """
    {"feed":{"entry":[
      {"im:name":{"label":"Raycast"},"im:artist":{"label":"Raycast Technologies"},
       "id":{"attributes":{"im:id":"591560477"}},
       "category":{"attributes":{"label":"Productivity"}},
       "im:price":{"attributes":{"amount":"0.00","currency":"USD"}},
       "im:releaseDate":{"label":"2020-01-01T00:00:00-07:00"},
       "link":{"attributes":{"href":"https://apps.apple.com/us/app/id591560477"}}},
      {"im:name":{"label":"Bartender 5"},"im:artist":{"label":"Surtees Studios"},
       "id":{"attributes":{"im:id":"123456"}},
       "category":{"attributes":{"label":"Utilities"}},
       "im:price":{"attributes":{"amount":"16.00","currency":"USD"}},
       "im:releaseDate":{"label":"bad-date"},
       "link":{"attributes":{"href":"https://apps.apple.com/us/app/id123456"}}}
    ]}}
    """.trimIndent()

    @Test
    fun `차트_2건_가격분류`() {
        val drafts = ChartRssCrawler(source).parseChart(feed, "free")
        assertEquals(2, drafts.size)
        val free = drafts[0].app
        assertEquals("FREE", free.license)
        assertEquals(591560477L, free.trackId)
        assertEquals("생산성", free.category)
        assertNotNull(free.releaseDate)
        val paid = drafts[1].app
        assertEquals("PAID", paid.license)
        assertEquals("유틸리티", paid.category)
        assertNull(paid.releaseDate)
    }
}
