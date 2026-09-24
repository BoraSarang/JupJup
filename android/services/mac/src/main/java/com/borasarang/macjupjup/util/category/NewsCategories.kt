package com.borasarang.macjupjup.util.category

/**
 * 뉴스 카테고리 (R32 PLAN_v17).
 * main 3종 + 서브 18종. "전체"는 필터 전용(미분류 저장값으로도 사용).
 * 외부 LLM 없이 키워드 규칙으로 1단계 분류 (2단계에서 LLM 교체 예정).
 */
object NewsCategories {

    const val MAIN_MAC = "mac"
    const val MAIN_AI = "ai"
    const val MAIN_SEC = "sec"

    const val SUB_ALL = "전체"

    val SUBS_MAC = listOf("전체", "macOS", "Apple Silicon", "앱·업데이트", "루머", "팁")
    val SUBS_AI = listOf("전체", "모델 출시", "연구/논문", "도구/서비스", "비즈니스", "정책")
    val SUBS_SEC = listOf("전체", "취약점/CVE", "랜섬웨어", "개인정보", "국내", "Apple보안")

    fun subsOf(main: String): List<String> = when (main) {
        MAIN_MAC -> SUBS_MAC
        MAIN_AI -> SUBS_AI
        MAIN_SEC -> SUBS_SEC
        else -> listOf(SUB_ALL)
    }

    fun isValid(main: String, sub: String): Boolean = sub in subsOf(main)

    /** 뉴스 뷰의 앱 필터 칩 11종 (목업 기준, 기존 앱 10종과 별개로 공존) */
    val APP_CHIPS = listOf(
        "전체", "신규 등록", "무료", "유료", "업데이트",
        "생산성", "개발자 도구", "유틸리티", "크리에이티브", "라이프스타일", "세일중",
    )

    /** 서브별 키워드 (소문자 매칭, 순서 = 우선순위) */
    private val KEYWORDS_MAC: List<Pair<String, List<String>>> = listOf(
        "루머" to listOf("rumor", "gurman", "kuo", "leak", "루머", "유출", "전망"),
        "Apple Silicon" to listOf("m1", "m2", "m3", "m4", "m5", "apple silicon", "chip", "칩", "실리콘", "soc"),
        "macOS" to listOf("macos", "sequoia", "sonoma", "ventura", "tahoe", "맥os", "운영체제", "beta", "베타"),
        "앱·업데이트" to listOf("app", "update", "release", "version", "앱", "업데이트", "출시", "버전", "download", "다운로드"),
        "팁" to listOf("how to", "tip", "guide", "tutorial", "팁", "방법", "가이드", "활용", "단축키", "shortcut"),
    )
    private val KEYWORDS_AI: List<Pair<String, List<String>>> = listOf(
        "모델 출시" to listOf("gpt", "claude", "gemini", "llama", "mistral", "model", "모델", "출시", "release", "checkpoint"),
        "연구/논문" to listOf("paper", "arxiv", "research", "study", "논문", "연구", "benchmark", "벤치마크"),
        "정책" to listOf("policy", "regulation", "law", "eu ai act", "정책", "규제", "법안", "가이드라인"),
        "비즈니스" to listOf("funding", "startup", "revenue", "enterprise", "비즈니스", "투자", "매출", "기업", "인수", "상장"),
        "도구/서비스" to listOf("tool", "service", "api", "copilot", "cursor", "도구", "서비스", "플랫폼"),
    )
    private val KEYWORDS_SEC: List<Pair<String, List<String>>> = listOf(
        "취약점/CVE" to listOf("cve-", "vulnerability", "exploit", "patch", "취약점", "익스플로잇", "패치", "보안 업데이트"),
        "랜섬웨어" to listOf("ransomware", "랜섬웨어", "lockbit", "blackcat", "rehvil"),
        "국내" to listOf("boannews", "dailysecu", "보안뉴스", "데일리시큐", "한국", "국내", "kisa", "과기정통부"),
        "Apple보안" to listOf("apple", "iphone", "mac", "ios", "xprotect", "gatekeeper", "애플"),
        "개인정보" to listOf("privacy", "breach", "leak", "개인정보", "유출", "프라이버시", "gdpr"),
    )

    /**
     * 1단계 분류: RSS 카테고리 우선 매칭 → 키워드 규칙 → "전체"(미분류).
     * [rssCategory]는 피드의 category/channel명.
     */
    fun classify(main: String, title: String, text: String, rssCategory: String? = null): String {
        val hay = "$title $text".lowercase()
        val rules = when (main) {
            MAIN_MAC -> KEYWORDS_MAC
            MAIN_AI -> KEYWORDS_AI
            MAIN_SEC -> KEYWORDS_SEC
            else -> return SUB_ALL
        }
        if (!rssCategory.isNullOrBlank()) {
            val rc = rssCategory.lowercase()
            for ((sub, kws) in rules) {
                if (kws.any { it in rc }) return sub
            }
        }
        for ((sub, kws) in rules) {
            if (kws.any { it in hay }) return sub
        }
        return SUB_ALL
    }

    /**
     * 1줄 요약: 본문 첫 문장, 최대 200자.
     * 한국어 마침표(./!/？/。/줄바꿈) 기준 절단.
     */
    fun summarize(text: String, maxLen: Int = 200): String? {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return null
        val end = clean.indexOfFirst { it in ".!?\n。！？" }
        val first = if (end in 0..600) clean.substring(0, end + 1).trim() else clean
        return first.take(maxLen).trim().ifBlank { null }
    }
}
