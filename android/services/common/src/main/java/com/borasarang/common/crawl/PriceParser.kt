package com.borasarang.common.crawl

import com.borasarang.common.text.takeSafe

/** 핫딜/중고 제목·발췌에서 가격·할인율·상태 추출 (순수 함수) */
object PriceParser {

    data class DealInfo(
        val salePrice: Int? = null,
        val originalPrice: Int? = null,
        val discountRate: Int? = null,
        val isSoldOut: Boolean? = null,
        val dealStatus: String? = null,
        val mallName: String? = null,
    )

    private val priceRe = "([0-9][0-9,]*)\\s*원".toRegex()
    private val rateRe = "(\\d{1,3})\\s*%".toRegex()
    private val soldOutKeys = listOf("품절", "마감", "종료", "sold out", "soldout")
    private val sellingKeys = listOf("판매중", "판매 중")
    private val soldKeys = listOf("판매완료", "판매 완료", "거래완료")
    private val reservedKeys = listOf("예약중", "예약 중")
    private val mallRe = "\\[(.+?)]".toRegex()

    fun parse(title: String, snippet: String? = null): DealInfo {
        val text = (title + " " + (snippet ?: "")).trim()
        val prices = priceRe.findAll(text).mapNotNull {
            it.groupValues[1].replace(",", "").toIntOrNull()
        }.toList()
        val salePrice = prices.minOrNull()
        val originalPrice = prices.maxOrNull()?.takeIf { prices.size > 1 }
        val discountRate = rateRe.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..99 }
        val lower = text.lowercase()
        val isSoldOut = soldOutKeys.any { lower.contains(it.lowercase()) }.takeIf { it }
        val dealStatus = when {
            reservedKeys.any { text.contains(it) } -> "reserved"
            soldKeys.any { text.contains(it) } -> "sold"
            sellingKeys.any { text.contains(it) } -> "selling"
            isSoldOut == true -> "sold"
            else -> null
        }
        val mallName = mallRe.find(text)?.groupValues?.get(1)?.trim()?.takeSafe(30)
        return DealInfo(
            salePrice = salePrice,
            originalPrice = originalPrice,
            discountRate = discountRate,
            isSoldOut = isSoldOut,
            dealStatus = dealStatus,
            mallName = mallName,
        )
    }
}
