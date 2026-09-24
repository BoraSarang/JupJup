package com.borasarang.macjupjup.crawler.epic

import com.borasarang.macjupjup.crawler.AppDraft
import com.borasarang.macjupjup.crawler.BaseCrawler
import com.borasarang.macjupjup.crawler.steam.SteamDetails
import com.borasarang.macjupjup.crawler.str
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants
import com.borasarang.macjupjup.util.DebugLogger
import com.borasarang.macjupjup.util.category.GameGenres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Epic 주간 무료 게임 (PLAN_v21).
 * store.epicgames.com browse는 Cloudflare 403 — freeGamesPromotions 정적 API 사용.
 * 맥 OS 전용 필터는 API에 없음 → 주간 무료 전체를 수집하고 UI에 스토어 호환 안내.
 * 프로모션 discountPrice==0 또는 promotions가 있고 original>discount 인 것만 채택.
 * 본문: 프로모션 짧은 소개 + Steam 동명 appdetails README·스크린샷·언어·요건 보강.
 */
class EpicFreeGamesCrawler(
    source: CrawlSource,
) : BaseCrawler(source) {

    override suspend fun crawl(): Result<List<AppDraft>> = runCatching {
        val url = "${source.baseUrl}/freeGamesPromotions?locale=en-US&country=US"
        val drafts = parsePromotions(fetchGet(url))
        politenessDelay()
        // Epic 프로모션은 짧은 소개만 → Steam 동명 게임 상세로 본문·스크린샷·언어·요건 보강
        // (Epic 스토어 페이지는 Cloudflare 403, content API는 404)
        val enriched = drafts.map { d ->
            try {
                enrichFromSteam(d)
            } catch (e: Exception) {
                DebugLogger.w("수집", "Epic Steam 본문 보강 스킵 ${d.app.name}: ${e.message}")
                d
            }
        }
        DebugLogger.i("수집", "[FEATURE] Epic 주간 무료 found=${enriched.size}")
        enriched
    }

    /**
     * Steam storesearch 이름 일치 → appdetails에서 README 본문·스크린샷·언어·요건 구성.
     * mac 플랫폼 필터 없음(Epic 무료는 Steam mac 여부와 무관). 스토어 URL·태그는 Epic 유지.
     */
    internal suspend fun enrichFromSteam(draft: AppDraft): AppDraft {
        val term = java.net.URLEncoder.encode(draft.app.name, Charsets.UTF_8)
        val searchBody = fetchGet("${SteamDetails.STORESEARCH_URL}$term&l=english&cc=US")
        politenessDelay()
        val appid = SteamDetails.searchAppId(searchBody, draft.app.name) ?: return draft
        val detail = fetchGet(SteamDetails.DETAIL_URL + appid)
        politenessDelay()
        return applySteamDetails(draft, appid.toString(), detail) ?: draft
    }

    /** appdetails 본문 → descriptionSnippet(README·SYSREQ)·스크린샷·언어 (Epic URL 유지) */
    internal fun applySteamDetails(draft: AppDraft, appid: String, detailBody: String): AppDraft? {
        val data = SteamDetails.dataOf(detailBody, appid) ?: return draft
        val aboutHtml = data["about_the_game"]?.jsonPrimitive?.content
            ?: data["detailed_description"]?.jsonPrimitive?.content
        val shortDesc = data["short_description"]?.jsonPrimitive?.content
        val desc = SteamDetails.composeDescription(
            shortDesc = shortDesc,
            aboutMd = SteamDetails.aboutHtmlToMarkdown(aboutHtml),
            sysReqHtml = SteamDetails.buildSysReqHtml(data["mac_requirements"], data["pc_requirements"]),
            fallback = draft.app.descriptionSnippet,
        ) ?: draft.app.descriptionSnippet
        val shots = SteamDetails.parseScreenshots(data["screenshots"])
        val langs = SteamDetails.parseSupportedLanguages(data["supported_languages"])
        val steamTags = (draft.app.tags.orEmpty().split(",").filter { it.isNotBlank() })
            .plus("steam-appid:$appid")
            .plus(listOfNotNull(data["header_image"]?.jsonPrimitive?.content?.let { "steam-header" }))
            .distinct()
            .joinToString(",")
            .ifBlank { null }
        val app = draft.app.copy(
            descriptionSnippet = shortDesc?.take(Constants.APP_SUMMARY_LEN)
                ?: desc?.take(Constants.APP_SUMMARY_LEN),
            longDescription = desc?.take(Constants.APP_BODY_MAX) ?: draft.app.longDescription,
            screenshotUrls = shots ?: draft.app.screenshotUrls,
            supportedLanguages = langs ?: draft.app.supportedLanguages,
            tags = steamTags,
        )
        return draft.copy(app = app)
    }

    internal fun parsePromotions(body: String): List<AppDraft> {
        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            parseFail("Epic 프로모션 응답")
        }
        val elements = try {
            root["data"]?.jsonObject?.get("Catalog")?.jsonObject
                ?.get("searchStore")?.jsonObject?.get("elements")?.jsonArray
                ?: return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return elements.mapNotNull { el ->
            try {
                val o = el as? JsonObject ?: return@mapNotNull null
                val title = o.str("title") ?: return@mapNotNull null
                val free = isFreeOffer(o)
                if (!free) return@mapNotNull null
                val storeUrl = pageStoreUrl(o)
                val seller = try {
                    o["seller"]?.jsonObject?.str("name")
                } catch (_: Exception) {
                    null
                }
                val description = o.str("description")
                val icon = pickImage(o)
                val promoEnd = promoEndDate(o)
                val genre = GameGenres.fromEnName("Free To Play") // 기본값 — Epic은 장르 미제공
                val tags = GameGenres.gameTags(genre, STORE_TAG)
                    .plus("epic-weekly-free")
                    .joinToString(",")
                val draft = buildDraft(
                    name = title,
                    developer = seller?.takeIf { it.isNotBlank() } ?: DEVELOPER,
                    descriptionSnippet = description
                        ?.take(com.borasarang.macjupjup.util.Constants.APP_SUMMARY_LEN),
                    longDescription = description
                        ?.take(com.borasarang.macjupjup.util.Constants.APP_BODY_MAX),
                    homepageUrl = storeUrl,
                    releaseDate = promoEnd,
                    iconUrl = icon,
                    topics = listOf("game", "epic", "epic-weekly-free"),
                    detailUrl = storeUrl,
                ) ?: return@mapNotNull null
                draft.copy(
                    app = draft.app.copy(
                        category = Constants.CATEGORY_GAME,
                        tags = tags,
                        license = Constants.LICENSE_FREE,
                        price = 0.0,
                        sellerName = seller,
                    ),
                )
            } catch (_: Exception) {
                null
            }
        }.let { dedupById(it) }
    }

    /**
     * 스토어 제품 페이지 URL.
     * 우선: offerMappings/catalogNs.mappings의 productHome pageSlug
     * (urlSlug는 offer 해시일 수 있어 /p/{해시} 404 유발) → productSlug(/home 제거) → urlSlug → 무료 목록.
     */
    internal fun pageStoreUrl(o: JsonObject): String {
        val pageSlug = listOfNotNull(
            o["offerMappings"], o["catalogNs"]?.let { try { it.jsonObject["mappings"] } catch (_: Exception) { null } },
        ).mapNotNull { el ->
            try {
                (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { m ->
                    val mo = m as? JsonObject ?: return@mapNotNull null
                    if (mo.str("pageType") != "productHome") return@mapNotNull null
                    mo.str("pageSlug")?.takeIf { it.isNotBlank() }
                }?.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }.firstOrNull()
        if (!pageSlug.isNullOrBlank()) return "https://store.epicgames.com/en-US/p/$pageSlug"
        val product = o.str("productSlug")?.substringBefore('/')?.takeIf { it.isNotBlank() && it != "home" }
        if (product != null) return "https://store.epicgames.com/en-US/p/$product"
        val urlSlug = o.str("urlSlug")?.takeIf { it.isNotBlank() }
        if (urlSlug != null) return "https://store.epicgames.com/en-US/p/$urlSlug"
        return "https://store.epicgames.com/free-games"
    }

    /** discountPrice==0 또는 promotions 안에 discountOffer 존재 */
    internal fun isFreeOffer(o: JsonObject): Boolean {
        val tp = try {
            o["price"]?.jsonObject?.get("totalPrice")?.jsonObject
        } catch (_: Exception) {
            null
        } ?: return false
        val discount = tp["discount"]?.jsonPrimitive?.content?.toIntOrNull()
        val original = tp["originalPrice"]?.jsonPrimitive?.content?.toIntOrNull()
        if (discount != null && original != null && original > 0 && discount == original) {
            // 100% 할인(주간 무료) — Mindcop류 discount==original도 free로 보는 케이스
            // 실제로 discount==0 && original>0 인 Mindcop은 프로모션으로 무료
        }
        val hasPromo = try {
            val promos = o["promotions"] ?: return (discount == 0 && (original ?: 0) > 0)
            val section = promos.jsonObject["promotionalOffers"]?.jsonArray ?: return false
            section.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        // 주간 무료: discount==0 이고 promotions 있음, 또는 프로모션 100% (discount==original)
        if (hasPromo && discount == 0 && (original ?: 0) > 0) return true
        if (hasPromo && discount != null && original != null && discount == original) return true
        // promotions 없는 freegames 카테고리 + discount 0 원가 ( Them's Fightin' Herds류는 promotions null이어도 무료 피드에 포함)
        val cats = try {
            o["categories"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return "freegames" in cats && discount == 0 && (original ?: 0) > 0
    }

    internal fun pickImage(o: JsonObject): String? {
        val images = try {
            o["keyImages"]?.jsonArray ?: return null
        } catch (_: Exception) {
            return null
        }
        val order = listOf("Thumbnail", "OfferImageTall", "OfferImageWide", "featuredMedia")
        for (type in order) {
            val hit = images.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    if (obj.str("type") == type) obj.str("url") else null
                } catch (_: Exception) {
                    null
                }
            }.firstOrNull()
            if (hit != null) return hit
        }
        return null
    }

    internal fun promoEndDate(o: JsonObject): Long? {
        val promos = try {
            o["promotions"]?.jsonObject ?: return null
        } catch (_: Exception) {
            return null
        }
        return try {
            promos["promotionalOffers"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("promotionalOffers")?.jsonArray?.firstOrNull()?.jsonObject
                ?.str("endDate")?.let { ep ->
                    java.time.Instant.parse(ep).toEpochMilli()
                }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEVELOPER = "Epic Games"
        const val STORE_TAG = "epic"
    }
}
