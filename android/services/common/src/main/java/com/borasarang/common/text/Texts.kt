package com.borasarang.common.text

/**
 * 서로게이트 쌍을 가르고 자르면 깨진 문자(U+FFFD/물음표)로 표시된다.
 * 이모지·확장 한자가 많은 제목/본문 절단 시 필수.
 */
fun String.takeSafe(n: Int): String {
    if (length <= n) return this
    var end = n
    if (end > 0 && this[end - 1].isHighSurrogate()) end--
    return substring(0, end)
}

/** 고립 서로게이트 제거 (깨진 문자 방지) */
fun String.stripLoneSurrogates(): String {
    if (isEmpty()) return this
    val sb = StringBuilder(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate() -> {
                sb.append(c).append(this[i + 1]); i += 2
            }
            c.isLowSurrogate() || c.isHighSurrogate() -> i++ // 고립 → 버림
            else -> {
                sb.append(c); i++
            }
        }
    }
    return sb.toString()
}
