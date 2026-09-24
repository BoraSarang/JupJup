package com.borasarang.planjupjup.crawler

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 사이트별 파싱 설정. 코드 기본값 + DB selectorConfigJson 오버라이드.
 * kotlinx-serialization은 JsonElement 런타임 파싱만 사용(컴파일러 플러그인 불필요).
 */
data class FieldSelectors(
    val carrierName: String = "",
    val planName: String = "",
    val price: String = "",
    val priceAfterDiscount: String? = null,
    val discountMonths: String? = null,
    val dataAmount: String = "",
    val voice: String = "",
    val sms: String = "",
    val networkType: String? = null,
    val eventBadge: String? = null,
    val detailUrl: String = "",
    val network: String? = null,
)

data class CrawlerSelectorConfig(
    val listPageUrl: String,
    val itemSelector: String,
    val fields: FieldSelectors = FieldSelectors(),
    val maxPages: Int = 10,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(raw: String?): CrawlerSelectorConfig? {
            if (raw.isNullOrBlank()) return null
            return try {
                val obj = json.parseToJsonElement(raw) as? JsonObject ?: return null
                fun opt(key: String): String? =
                    obj[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val fieldsObj = obj["fields"] as? JsonObject
                fun f(key: String): String? =
                    fieldsObj?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                CrawlerSelectorConfig(
                    listPageUrl = opt("listPageUrl") ?: return null,
                    itemSelector = opt("itemSelector") ?: return null,
                    fields = FieldSelectors(
                        carrierName = f("carrierName") ?: "",
                        planName = f("planName") ?: "",
                        price = f("price") ?: "",
                        priceAfterDiscount = f("priceAfterDiscount"),
                        discountMonths = f("discountMonths"),
                        dataAmount = f("dataAmount") ?: "",
                        voice = f("voice") ?: "",
                        sms = f("sms") ?: "",
                        networkType = f("networkType"),
                        eventBadge = f("eventBadge"),
                        detailUrl = f("detailUrl") ?: "",
                        network = f("network"),
                    ),
                    maxPages = obj["maxPages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 10,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
