package com.borasarang.communityjupjup.util

/** 앱 중복 병합용 정규화 키 생성. PLAN 5장 규칙 */
object MergeUtils {
    /**
     * name + developer 정규화: 공백/특수문자 제거, 소문자화, 64자 절단.
     * 동일 키 다출처 수집 시 1개 App으로 병합.
     */
    fun generateId(name: String, developer: String): String {
        return "${normalizeName(developer)}-${normalizeName(name)}".trim('-').take(128)
    }

    /** 이름·개발사 정규화 단일 진실 (R1-11). 크롤러·서버에서 재사용 */
    fun normalizeName(s: String): String {
        return s.lowercase()
            .replace("[^a-z0-9가-힣]".toRegex(), "")
            .take(64)
    }

    /** 버전 동등 판정 단일 진실 (T-132). 앞뒤 공백 차이("4.3.4 " vs "4.3.4")는 동일 버전. */
    fun sameVersion(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a == b
        return a.trim() == b.trim()
    }
}
