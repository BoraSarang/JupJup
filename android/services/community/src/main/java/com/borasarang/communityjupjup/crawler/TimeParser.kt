package com.borasarang.communityjupjup.crawler

import java.util.Calendar

/** 게시 시각 관대 파서. 실패 시 null (Worker가 collectedAt으로 대체) */
object TimeParser {

    private val absoluteDateTime = listOf(
        "\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}\\s+\\d{1,2}:\\d{1,2}".toRegex(),
        "\\d{1,2}[./-]\\d{1,2}\\s+\\d{1,2}:\\d{1,2}".toRegex(),
    )
    private val absoluteDate = "\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}".toRegex()
    private val shortDate = "\\b(\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})\\b".toRegex()
    private val monthDay = "\\b(\\d{1,2})\\.(\\d{1,2})\\b".toRegex()
    private val timeOnly = "(\\d{1,2}):(\\d{2})".toRegex()
    private val relativeMin = "(\\d+)\\s*분\\s*전".toRegex()
    private val relativeHour = "(\\d+)\\s*시간\\s*전".toRegex()
    private val relativeDay = "(\\d+)\\s*일\\s*전".toRegex()
    /** 행마다 재컴파일되던 split 정규식 */
    private val WS_SPLIT_RE = "\\s+".toRegex()
    private val DATE_SEP_RE = "[./-]".toRegex()

    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        val now = System.currentTimeMillis()
        relativeMin.find(text)?.let { return now - (it.groupValues[1].toLongOrNull() ?: return null) * 60_000L }
        relativeHour.find(text)?.let { return now - (it.groupValues[1].toLongOrNull() ?: return null) * 3_600_000L }
        relativeDay.find(text)?.let { return now - (it.groupValues[1].toLongOrNull() ?: return null) * 86_400_000L }
        if (text == "방금" || text.startsWith("방금")) return now
        for (re in absoluteDateTime) {
            re.find(text)?.let { m -> return parseDateTime(m.value) }
        }
        absoluteDate.find(text)?.let { m -> return parseDate(m.value) }
        // 2자리 연도 (오유 26/09/21, 뽐뿌 24/11/14, 더쿠 24.04.09)
        shortDate.find(text)?.let { m ->
            return try {
                val cal = Calendar.getInstance()
                cal.set(
                    2000 + m.groupValues[1].toInt(),
                    m.groupValues[2].toInt() - 1,
                    m.groupValues[3].toInt(), 0, 0, 0,
                )
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis.takeIf { it <= now }
            } catch (_: Exception) {
                null
            }
        }
        timeOnly.find(text)?.let { m ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, m.groupValues[1].toInt().coerceIn(0, 23))
            cal.set(Calendar.MINUTE, m.groupValues[2].toInt().coerceIn(0, 59))
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            var ts = cal.timeInMillis
            if (ts > now) ts -= 86_400_000L
            return ts
        }
        return null
    }

    private fun parseDateTime(text: String): Long? {
        return try {
            val parts = text.split(WS_SPLIT_RE)
            val d = parts[0].split(DATE_SEP_RE).map { it.toInt() }
            val t = parts.getOrNull(1)?.split(":")?.map { it.toInt() } ?: listOf(0, 0)
            val cal = Calendar.getInstance()
            if (d.size == 3) {
                cal.set(d[0], d[1] - 1, d[2], t[0], t[1], 0)
            } else {
                cal.set(Calendar.MONTH, d[0] - 1)
                cal.set(Calendar.DAY_OF_MONTH, d[1])
                cal.set(Calendar.HOUR_OF_DAY, t[0])
                cal.set(Calendar.MINUTE, t[1])
                cal.set(Calendar.SECOND, 0)
            }
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDate(text: String): Long? {
        return try {
            val d = text.split(DATE_SEP_RE).map { it.toInt() }
            val cal = Calendar.getInstance()
            cal.set(d[0], d[1] - 1, d[2], 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
