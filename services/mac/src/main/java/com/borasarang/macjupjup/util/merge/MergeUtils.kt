package com.borasarang.macjupjup.util.merge

/** 앱 중복 병합용 정규화 키 생성. PLAN 5장 규칙 */
object MergeUtils {
    /**
     * name + developer 정규화: 공백/특수문자 제거, 소문자화, 64자 절단.
     * 동일 키 다출처 수집 시 1개 App으로 병합.
     */
    fun generateId(name: String, developer: String): String {
        return "${normalizeName(developer)}-${normalizeName(name)}".trim('-').take(128)
    }

    /**
     * 이름·개발사 정규화 단일 진출 (R1-11). 크롤러·서버에서 재사용.
     * 라틴 외(키릴·CJK·히라가나 등) 문자 보존 — 전부 제거하면 빈/id 충돌로 상호 덮어쓰기 발생
     * (예: 러시아어 제목 + developer "AppStorrent" → 모두 "appstorrent" 단일 id).
     */
    fun normalizeName(s: String): String {
        val cleaned = s.lowercase()
            .replace(KEEP_SCRIPT_REGEX, "")
            .take(64)
        if (cleaned.isNotBlank()) return cleaned
        // 전부 특수문자가 제거되어 빈 결과 → 원문 hex suffix로 충돌 회피
        val hex = s.trim().lowercase().encodeToByteArray()
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return if (hex.isBlank()) "x" else "x$hex"
    }

    /**
     * 유지할 문자: 영숫자 + 한글 + 키릴 + CJK(간체·번체·일본어) 등.
     * 기존 `[^a-z0-9가-힣]`는 라틴 외 전부 삭제 → id 충돌의 근원.
     */
    private val KEEP_SCRIPT_REGEX = Regex("""[^a-z0-9가-힣Ѐ-ӿ぀-ヿ一-鿿]""")

    /** 버전 동등 판정 단일 진출 (T-132). 앞뒤 공백 차이("4.3.4 " vs "4.3.4")는 동일 버전. */
    fun sameVersion(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        return a.trim() == b.trim()
    }
}
