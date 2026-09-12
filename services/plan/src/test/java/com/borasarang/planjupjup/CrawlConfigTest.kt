package com.borasarang.planjupjup

import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrawlConfigTest {

    @Test
    fun fromJson_parsesFullConfig() {
        val raw = """
            {
              "listPageUrl": "https://example.com/list",
              "itemSelector": ".card",
              "maxPages": 5,
              "fields": {
                "planName": "p.tit",
                "price": ".now span",
                "networkType": ".purple"
              }
            }
        """.trimIndent()
        val config: CrawlerSelectorConfig? = CrawlerSelectorConfig.fromJson(raw)
        assertEquals("https://example.com/list", config?.listPageUrl)
        assertEquals(".card", config?.itemSelector)
        assertEquals(5, config?.maxPages)
        assertEquals("p.tit", config?.fields?.planName)
        assertEquals(".purple", config?.fields?.networkType)
        assertEquals(null, config?.fields?.eventBadge)
    }

    @Test
    fun fromJson_returnsNullOnBadInput() {
        assertNull(CrawlerSelectorConfig.fromJson(null))
        assertNull(CrawlerSelectorConfig.fromJson(""))
        assertNull(CrawlerSelectorConfig.fromJson("{broken"))
        assertNull(CrawlerSelectorConfig.fromJson("""{"itemSelector":".card"}"""))
    }

    @Test
    fun fromJson_ignoresUnknownKeys() {
        val config = CrawlerSelectorConfig.fromJson(
            """{"listPageUrl":"https://e.com","itemSelector":".c","future":{"x":1}}""",
        )
        assertEquals("https://e.com", config?.listPageUrl)
    }
}
