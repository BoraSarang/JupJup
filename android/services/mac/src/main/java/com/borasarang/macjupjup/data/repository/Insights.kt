package com.borasarang.macjupjup.data.repository

/** 통계 인사이트 카드 1건 */
data class Insight(
    val icon: String,
    val title: String,
    val body: String,
)

/** 인사이트 조립 입력 (서버가 DB에서 모아 전달, 테스트는 고정값) */
data class InsightsInput(
    val totalApps: Int,
    val newLast7d: Int,
    val bumpsLast7d: Int,
    val topCategory: Pair<String, Int>?,
    val topCategorySharePct: Int,
    /** 7일간 발견 최다 소스 (이름, 발견, 신규) */
    val bestSource: Triple<String, Long, Long>?,
    /** 7일간 신규 최다 날 (날짜, 신규) */
    val bestDay: Pair<String, Long>?,
    /** 최근 24h 실패 소스명 */
    val failedSources24h: List<String>,
    val untranslated: Int,
)

/**
 * 트렌드 인사이트 조립 (순수 함수).
 * 규칙: 수집왕 → 신규 급증일 → 실패 경고 → 번역 잔량 → 버전 활동 → 카테고리 편중.
 */
fun buildInsights(i: InsightsInput): List<Insight> {
    val out = mutableListOf<Insight>()
    i.bestSource?.let { (name, found, newCount) ->
        if (found > 0) {
            out += Insight(
                icon = "🏆",
                title = "이번 주 수집왕",
                body = "${name} — 7일간 ${found}건 발견·${newCount}건 신규",
            )
        }
    }
    i.bestDay?.let { (day, newCount) ->
        if (newCount > 0) {
            out += Insight(
                icon = "📈",
                title = "신규 급증일",
                body = "${day}에 ${newCount}건 신규 (7일 신규 ${i.newLast7d}건)",
            )
        }
    }
    if (i.failedSources24h.isNotEmpty()) {
        out += Insight(
            icon = "⚠️",
            title = "수집 실패 주의",
            body = "최근 24시간 실패: ${i.failedSources24h.distinct().joinToString(", ")}",
        )
    }
    if (i.untranslated > 0) {
        out += Insight(
            icon = "🌐",
            title = "번역 대기",
            body = "${i.untranslated}건이 한글 번역 대기 중 (3시간마다 자동 처리)",
        )
    }
    if (i.bumpsLast7d > 0) {
        out += Insight(
            icon = "🆕",
            title = "버전 활동",
            body = "최근 7일 버전 bump ${i.bumpsLast7d}건 — Watchlist에서 확인",
        )
    }
    i.topCategory?.let { (cat, cnt) ->
        out += Insight(
            icon = "📊",
            title = "카테고리 편중",
            body = "전체 ${i.totalApps}건 중 ${cat} ${cnt}건(${i.topCategorySharePct}%)",
        )
    }
    return out
}
