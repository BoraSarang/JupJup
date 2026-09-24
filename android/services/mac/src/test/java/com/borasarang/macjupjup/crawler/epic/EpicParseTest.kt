package com.borasarang.macjupjup.crawler.epic

import com.borasarang.macjupjup.data.db.entity.CrawlSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpicParseTest {

    private val source = CrawlSource(
        id = "epic_free_games", name = "Epic 주간 무료 게임", type = "EPIC_FREE",
        baseUrl = "https://store-site-backend-static.ak.epicgames.com",
        enabled = true, intervalHours = 12, intervalMinutes = 720,
        lastRunAt = null, lastStatus = "NEVER_RUN", errorMessage = null, selectorConfigJson = null,
    )

    private val fixture = """
    {
      "data": {
        "Catalog": {
          "searchStore": {
            "elements": [
              {
                "title": "Weekly Free Game",
                "productSlug": "weekly-free-game/home",
                "urlSlug": "weekly-free-game",
                "offerMappings": [{"pageSlug": "weekly-free-game-61832d", "pageType": "productHome"}],
                "catalogNs": {"mappings": [{"pageSlug": "weekly-free-game-61832d", "pageType": "productHome"}]},
                "description": "A free weekly game",
                "seller": {"name": "Some Studio"},
                "keyImages": [
                  {"type": "Thumbnail", "url": "https://cdn.example/thumb.jpg"},
                  {"type": "OfferImageWide", "url": "https://cdn.example/wide.jpg"}
                ],
                "categories": [{"path": "freegames"}, {"path": "games"}],
                "price": {
                  "totalPrice": {
                    "discount": 0,
                    "originalPrice": 1999,
                    "currencyCode": "USD"
                  }
                },
                "promotions": {
                  "promotionalOffers": [
                    {
                      "promotionalOffers": [
                        {"startDate": "2026-09-18T15:00:00.000Z", "endDate": "2026-09-25T15:00:00.000Z"}
                      ]
                    }
                  ]
                }
              },
              {
                "title": "Not Free Game",
                "productSlug": "not-free",
                "categories": [{"path": "games"}],
                "price": {
                  "totalPrice": {"discount": 0, "originalPrice": 5999}
                },
                "promotions": null
              },
              {
                "title": "Paid Promo",
                "productSlug": "paid-promo",
                "categories": [{"path": "games"}],
                "price": {
                  "totalPrice": {"discount": 1000, "originalPrice": 5999}
                },
                "promotions": {
                  "promotionalOffers": [
                    {"promotionalOffers": [{"endDate": "2026-09-25T15:00:00.000Z"}]}
                  ]
                }
              }
            ]
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun `parse_주간무료만_채택`() {
        val c = EpicFreeGamesCrawler(source)
        val drafts = c.parsePromotions(fixture)
        assertEquals(1, drafts.size)
        val app = drafts[0].app
        assertEquals("Weekly Free Game", app.name)
        assertEquals("Some Studio", app.developer)
        assertEquals("게임", app.category)
        assertEquals("FREE", app.license)
        assertEquals(0.0, app.price, 0.0)
        assertTrue(app.tags!!.contains("game"))
        assertTrue(app.tags!!.contains("epic"))
        assertEquals("https://cdn.example/thumb.jpg", app.iconUrl)
        assertEquals(
            "https://store.epicgames.com/en-US/p/weekly-free-game-61832d",
            drafts[0].mappings[0].sourceUrl,
        )
        assertNotNull(app.releaseDate)
    }

    @Test
    fun `pageStoreUrl_pageSlug우선_urlSlug해시는사용안함`() {
        val c = EpicFreeGamesCrawler(source)
        val withMappings = """
            {"offerMappings":[{"pageSlug":"mindcop-78e6c1","pageType":"productHome"}],
             "urlSlug":"1513de80f23f42e584540be749826057","productSlug":null}
        """.trimIndent()
        val el = kotlinx.serialization.json.Json.parseToJsonElement(withMappings)
        assertEquals(
            "https://store.epicgames.com/en-US/p/mindcop-78e6c1",
            c.pageStoreUrl(el as kotlinx.serialization.json.JsonObject),
        )
        val productOnly = """{"productSlug":"foo/home","urlSlug":"ignored"}""".trimIndent()
        assertEquals(
            "https://store.epicgames.com/en-US/p/foo",
            c.pageStoreUrl(kotlinx.serialization.json.Json.parseToJsonElement(productOnly) as kotlinx.serialization.json.JsonObject),
        )
        val fallback = """{"urlSlug":"only-slug"}""".trimIndent()
        assertEquals(
            "https://store.epicgames.com/en-US/p/only-slug",
            c.pageStoreUrl(kotlinx.serialization.json.Json.parseToJsonElement(fallback) as kotlinx.serialization.json.JsonObject),
        )
    }

    @Test
    fun `isFree_할인없는원가_스킵`() {
        val c = EpicFreeGamesCrawler(source)
        val drafts = c.parsePromotions(fixture)
        assertTrue(drafts.none { it.app.name == "Not Free Game" })
    }

    @Test
    fun `isFree_부분할인_스킵`() {
        val c = EpicFreeGamesCrawler(source)
        val drafts = c.parsePromotions(fixture)
        assertTrue(drafts.none { it.app.name == "Paid Promo" })
    }

    @Test
    fun `parse_깨진JSON_parseFail`() {
        val c = EpicFreeGamesCrawler(source)
        try {
            c.parsePromotions("{broken")
            throw AssertionError("expected parse fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Epic"))
        }
    }

    @Test
    fun `parse_빈요소_빈목록`() {
        val c = EpicFreeGamesCrawler(source)
        val empty = """{"data":{"Catalog":{"searchStore":{"elements":[]}}}}"""
        assertTrue(c.parsePromotions(empty).isEmpty())
    }

    @Test
    fun `pickImage_썸네일_우선`() {
        val c = EpicFreeGamesCrawler(source)
        val drafts = c.parsePromotions(fixture)
        assertEquals("https://cdn.example/thumb.jpg", drafts[0].app.iconUrl)
        assertFalse(drafts[0].app.tags!!.isBlank())
    }

    @Test
    fun `applySteamDetails_README본문과스크린샷_보강`() {
        val c = EpicFreeGamesCrawler(source)
        val drafts = c.parsePromotions(fixture)
        val detail = """
            {"123":{"success":true,"data":{
              "short_description":"Epic short",
              "about_the_game":"<h2>About</h2><p>Full body paragraph.</p>",
              "screenshots":[
                {"path_thumbnail":"https://cdn.example/s1.jpg","path_full":"https://cdn.example/s1f.jpg"}
              ],
              "supported_languages":{"english":"1","koreana":"1"},
              "mac_requirements":[],
              "pc_requirements":{"minimum":"<strong>OS:</strong> Win 10"}
            }}}
        """.trimIndent()
        val out = c.applySteamDetails(drafts[0], "123", detail)!!
        val snip = out.app.descriptionSnippet
        val desc = out.app.longDescription!!
        // 소개 발췌 = 짧은 설명, 전문 = README·SYSREQ 포함 전체
        assertTrue(snip == null || !snip.contains("— README —"))
        assertTrue(desc.contains("— README —"))
        assertTrue(desc.contains("Full body paragraph."))
        assertTrue(desc.contains("— SYSREQ —"))
        assertTrue(out.app.screenshotUrls!!.contains("s1.jpg"))
        assertEquals("en,ko", out.app.supportedLanguages)
        assertTrue(out.app.tags!!.contains("steam-appid:123"))
        // Epic 스토어 URL 유지
        assertEquals(
            "https://store.epicgames.com/en-US/p/weekly-free-game-61832d",
            out.mappings[0].sourceUrl,
        )
    }

    @Test
    fun `searchAppId_이름정확일치_app만`() {
        val search = """
            {"items":[
              {"type":"app","name":"Mindcop","id":1517180},
              {"type":"app","name":"Mindcop Demo","id":2804690}
            ]}
        """.trimIndent()
        assertEquals(1517180, com.borasarang.macjupjup.crawler.steam.SteamDetails.searchAppId(search, "Mindcop"))
        assertEquals(null, com.borasarang.macjupjup.crawler.steam.SteamDetails.searchAppId(search, "Other"))
    }
}
