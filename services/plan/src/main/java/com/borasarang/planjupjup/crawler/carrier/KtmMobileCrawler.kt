package com.borasarang.planjupjup.crawler.carrier

import com.borasarang.planjupjup.crawler.BaseCrawler
import com.borasarang.planjupjup.crawler.CrawlerSelectorConfig
import com.borasarang.planjupjup.crawler.MergeUtils
import com.borasarang.planjupjup.crawler.PlanDraft
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * KT엠모바일 공식 크롤러. 실측 플로우(2026-09-06) 기반 2단계 AJAX:
 * 1) GET rateList.do (세션 수립) → POST getCtgXmlAllListAjax.do {rateAdsvcDivCd=RATE}
 *    → 카테고리 목록 (리프 = 자식이 없는 코드)
 * 2) 리프마다 POST rateContentAjax.do {rateAdsvcCtgCd} → 요금제 JSON 배열
 * 키: rateAdsvcNm / promotionAmtVatDesc(프로모가) / mmBasAmtVatDesc(정가) /
 *     bnfitData·Voice·Sms / rateAdsvcCd·CtgCd·GdncSeq(상세 URL) /
 *     rateGiftPrmtListDTO.mainGiftPrmtList[0].giftText(특전)
 * 망타입: depth-2 조상 카테고리명(LTE/5G/3G) 우선, 없으면 요금제명 추론
 */
class KtmMobileCrawler(source: CrawlSource) : BaseCrawler(source) {

    override val sourceName: String = "KT엠모바일 공식"

    override fun defaultConfig(): CrawlerSelectorConfig = CrawlerSelectorConfig(
        listPageUrl = "https://www.ktmmobile.com/rate/rateList.do",
        itemSelector = "",
        maxPages = 1,
    )

    override suspend fun crawl(): Result<List<PlanDraft>> {
        return try {
            logStart()
            val config = config()
            val base = "https://www.ktmmobile.com"

            // 1) 세션 수립
            fetchGet(config.listPageUrl)
            politenessDelay()

            // 2) 카테고리 목록
            val ctgBody = fetchPost(
                "$base/rate/getCtgXmlAllListAjax.do",
                mapOf("rateAdsvcDivCd" to "RATE"),
            )
            val categories = parseArray(ctgBody)
            if (categories.isEmpty()) {
                throw IllegalStateException("카테고리 목록이 비어 있음")
            }
            val leaves = findLeaves(categories)

            // 3) 리프별 요금제 수집
            val drafts = mutableListOf<PlanDraft>()
            leaves.forEach { leaf ->
                val leafCode = leaf.str("rateAdsvcCtgCd") ?: return@forEach
                val plansBody = try {
                    fetchPost("$base/rate/rateContentAjax.do", mapOf("rateAdsvcCtgCd" to leafCode))
                } catch (e: Exception) {
                    return@forEach
                }
                val netHint = ancestorDepth2Name(categories, leafCode)
                parseArray(plansBody).forEach { item ->
                    toDraft(item, netHint, base)?.let { drafts += it }
                }
                politenessDelay()
            }
            logDone(drafts.size)
            Result.success(drafts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 자식이 없는 리프 카테고리만 추출 (단위 테스트 대상) */
    internal fun findLeaves(categories: List<JsonObject>): List<JsonObject> {
        val childCodes = categories.mapNotNull { it.str("upRateAdsvcCtgCd") }.toSet()
        return categories.filter { cat ->
            val code = cat.str("rateAdsvcCtgCd") ?: return@filter false
            code !in childCodes && cat.str("useYn") != "N"
        }
    }

    /** depth-2 조상 카테고리명 (LTE/5G 힌트, 단위 테스트 대상) */
    internal fun ancestorDepth2Name(categories: List<JsonObject>, code: String): String? {
        var cur = categories.firstOrNull { it.str("rateAdsvcCtgCd") == code }
        repeat(4) {
            val up = cur?.str("upRateAdsvcCtgCd") ?: return null
            cur = categories.firstOrNull { it.str("rateAdsvcCtgCd") == up } ?: return null
            if (cur?.str("depthKey") == "2") return cur?.str("rateAdsvcCtgNm")
        }
        return null
    }

    internal fun toDraft(item: JsonObject, netHint: String?, base: String): PlanDraft? {
        val planName = item.str("rateAdsvcNm") ?: return null
        val promo = MergeUtils.parsePrice(item.str("promotionAmtVatDesc"))
        val base_amt = MergeUtils.parsePrice(item.str("mmBasAmtVatDesc"))
        val price = if (promo > 0) promo else base_amt
        if (price <= 0) return null
        val priceAfter = if (promo > 0 && base_amt > 0) base_amt else null
        val code = item.str("rateAdsvcCd") ?: return null
        val ctg = item.str("rateAdsvcCtgCd") ?: ""
        val seq = item.str("rateAdsvcGdncSeq") ?: ""
        val detailUrl = "$base/rate/rateLayer.do?rateAdsvcCtgCd=$ctg&rateAdsvcGdncSeq=$seq&rateAdsvcCd=$code"
        val gift = item["rateGiftPrmtListDTO"] as? JsonObject
        val mains = gift?.get("mainGiftPrmtList") as? JsonArray
        val frees = gift?.get("freeRateGiftPrmtList") as? JsonArray
        val eventBadge = (mains?.firstOrNull() as? JsonObject)?.str("giftText")
            ?: (frees?.firstOrNull() as? JsonObject)?.str("giftText")
        return buildDraft(
            carrierName = "KT엠모바일",
            planName = planName,
            price = price,
            priceAfterDiscount = priceAfter,
            discountMonths = null,
            dataAmount = item.str("bnfitData") ?: "",
            voice = item.str("bnfitVoice") ?: "",
            sms = item.str("bnfitSms") ?: "",
            networkType = MergeUtils.inferNetworkType(planName, netHint),
            mvnoNetwork = "KT",
            eventBadge = eventBadge,
            detailUrl = detailUrl,
            rawHtml = null,
        )
    }

    private fun parseArray(body: String): List<JsonObject> {
        return try {
            (json.parseToJsonElement(body) as? JsonArray)
                ?.mapNotNull { it as? JsonObject } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun JsonObject.str(key: String): String? {
        val prim = this[key]?.jsonPrimitive ?: return null
        if (prim.isString) return prim.content.takeIf { it.isNotBlank() && it != "null" }
        return try {
            prim.content
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
