package com.borasarang.planjupjup.crawler

import com.borasarang.planjupjup.util.Constants

/**
 * 순수 코틀린 병합·정규화 로직 (Android 의존성 없음 → 단위 테스트 대상).
 * 중복 병합 키: carrierName + planName 정규화.
 */
object MergeUtils {

    fun normalizeKey(carrierName: String, planName: String): String {
        val raw = "${carrierName.trim().lowercase()}_${planName.trim().lowercase()}"
            .replace(Regex("[^a-z0-9_가-힣]"), "")
        return raw.take(64)
    }

    fun generateId(carrierName: String, planName: String): String = normalizeKey(carrierName, planName)

    /** 통신사명으로 원 통신망 유추 */
    fun inferNetwork(carrierName: String): String {
        val n = carrierName.replace(" ", "").uppercase()
        return when {
            "SK" in n || "세븐모바일" in carrierName || "모빙" in carrierName ||
                "이야기" in carrierName || "찬스모바일" in carrierName -> "SKT"
            "KT" in n || "엠모바일" in carrierName || "리브" in carrierName ||
                "아이즈" in carrierName || "M모바일" in carrierName -> "KT"
            "LG" in n || "헬로" in carrierName || "유모바일" in carrierName ||
                "프리티" in carrierName || "티플러스" in carrierName -> "LGU+"
            else -> "UNKNOWN"
        }
    }

    /** 모요 "LG U+망" 표기 등 정규화. SKT를 KT보다 먼저 판정 (부분문자열 충돌 방지) */
    fun normalizeMoyoNetwork(text: String): String {
        val n = text.replace(" ", "").uppercase()
        return when {
            "LGU+" in n || "LG유플러스" in text -> "LGU+"
            "SKT" in n || "SK망" in n -> "SKT"
            n == "KT망" || n.startsWith("KT") -> "KT"
            else -> "UNKNOWN"
        }
    }

    fun inferNetworkType(planName: String, hint: String? = null): String {
        val h = hint?.uppercase() ?: ""
        if ("5G" in h) return "5G"
        if ("LTE" in h) return "LTE"
        val n = planName.uppercase()
        if ("5G" in n) return "5G"
        return "LTE"
    }

    /** "월 12,700원", "15,400", "2,900원" 등에서 숫자 추출 */
    fun parsePrice(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return text.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    }

    /** "6개월 이후 24,200원/월" → 6 */
    fun parseDiscountMonths(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return Regex("""(\d+)\s*개월""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** "15GB", "10GB+1Mbps" → 15. GB 없으면 0, 단독 "무제한"은 Int.MAX_VALUE */
    fun parseDataGb(text: String): Int {
        val t = text.replace(" ", "").uppercase()
        if (t.startsWith("무제한") && !t.contains("GB")) return Int.MAX_VALUE
        return Regex("""(\d+(?:\.\d+)?)\s*GB""").find(t)
            ?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 0
    }

    /** 큐레이션 태그 자동 부여 (CSV) */
    fun generateTags(price: Int, dataAmount: String): String {
        val tags = mutableListOf<String>()
        val dataGb = parseDataGb(dataAmount)
        if (price in 1..10000 && dataGb >= 10) tags += Constants.TAG_VALUE_YOUTH
        if (dataGb >= 100) tags += Constants.TAG_HEAVY
        if (price in 1..5000 && dataGb in 0..5) tags += Constants.TAG_SENIOR
        if (dataGb >= 50 && dataGb < Int.MAX_VALUE) tags += Constants.TAG_VIDEO
        return tags.joinToString(",")
    }
}
