package com.borasarang.planjupjup.util

/**
 * 요금제 원문(데이터량/음성/문자) 구조화 파서 — 통계·인사이트의 근간.
 * 실패 시 기본값 반환 + isParsed=false (분포에서 제외, 원문은 그대로 보존).
 */
object PlanMetrics {

    /** 무제한 요금제의 가정 데이터 GB (단가 비교용 가정치, UI에 "가정치" 라벨) */
    const val UNLIMITED_GB_ASSUMED = 100
    /** 무제한/기본제공 음성·문자의 가정 가중값 (분·건) */
    const val UNLIMITED_VOICE_SMS_ASSUMED = 2000

    data class DataAmount(
        val dataGb: Int,
        val isUnlimited: Boolean,
        val qosMbps: Int?,
        val isParsed: Boolean,
    )

    /** "15GB", "무제한", "무제한+5Mbps", "100GB+3Mbps", "15.5GB" 파싱 */
    fun parseDataAmount(text: String?): DataAmount {
        val t = (text ?: "").replace(" ", "").uppercase()
        if (t.isBlank()) return DataAmount(0, false, null, false)
        val unlimited = t.contains("무제한")
        val gb = Regex("""(\d+(?:\.\d+)?)\s*GB""")
            .find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
        val qos = Regex("""(\d+(?:\.\d+)?)\s*MBPS""")
            .find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()

        return when {
            // 무제한(단독 또는 +QoS): GB 가정치 사용 (단가 비교용)
            unlimited && gb == null -> DataAmount(
                dataGb = UNLIMITED_GB_ASSUMED,
                isUnlimited = true,
                qosMbps = qos,
                isParsed = true,
            )
            // 숫자 GB 존재 (무제한 포함 가능: 100GB+1Mbps)
            gb != null -> DataAmount(
                dataGb = gb,
                isUnlimited = unlimited,
                qosMbps = qos,
                isParsed = gb > 0,
            )
            else -> DataAmount(0, false, null, false)
        }
    }

    /** "무제한"/"기본제공" → 가정 2천분, "300분" → 300, 숫자 없으면 0 */
    fun parseVoiceMinutes(text: String?): Int {
        val t = (text ?: "").replace(" ", "").uppercase()
        if (t.isBlank()) return 0
        val hasUnlimited = t.contains("무제한") || t.contains("기본제공")
        val min = Regex("""(\d+)\s*분""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            hasUnlimited && (min == null || min == 0) -> UNLIMITED_VOICE_SMS_ASSUMED
            hasUnlimited -> maxOf(min ?: 0, UNLIMITED_VOICE_SMS_ASSUMED)
            else -> min ?: 0
        }
    }

    /** "무제한"/"기본제공" → 가정 2천건, "300건" → 300, 숫자 없으면 0 */
    fun parseSmsCount(text: String?): Int {
        val t = (text ?: "").replace(" ", "").uppercase()
        if (t.isBlank()) return 0
        val hasUnlimited = t.contains("무제한") || t.contains("기본제공")
        val count = Regex("""(\d+)\s*건""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            hasUnlimited && (count == null || count == 0) -> UNLIMITED_VOICE_SMS_ASSUMED
            hasUnlimited -> maxOf(count ?: 0, UNLIMITED_VOICE_SMS_ASSUMED)
            else -> count ?: 0
        }
    }

    /**
     * 가성비 스코어 = (dataGb + voiceMin×0.3 + smsCount×0.1) ÷ (price/1000).
     * 0원 요금제·파싱 실패·가격 0은 랭킹에서 제외 (score <= 0).
     */
    fun valueScore(dataGb: Int, voiceMinutes: Int, smsCount: Int, price: Int): Double {
        if (price <= 0) return 0.0
        val benefit = dataGb.toDouble() + voiceMinutes * 0.3 + smsCount * 0.1
        if (benefit <= 0) return 0.0
        return (benefit / (price / 1000.0)).coerceAtLeast(0.0)
    }
}