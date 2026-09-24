package com.borasarang.macjupjup.util.category

import com.borasarang.macjupjup.util.Constants

/**
 * 맥 게임 장르 — Steam tagid ↔ 한글 라벨 (PLAN_v21).
 * tags 컬럼에 `game,{한글장르}` 형태로 저장, 필터도 한글 장르 사용.
 */
object GameGenres {

    /** Steam tagid → 한글 */
    val BY_TAG_ID: Map<Int, String> = mapOf(
        19 to "액션",
        21 to "어드벤처",
        122 to "RPG",
        9 to "전략",
        599 to "시뮬레이션",
        1664 to "퍼즐",
        597 to "캐주얼",
        492 to "인디",
        128 to "멀티",
        699 to "레이싱·스포츠",
        701 to "레이싱·스포츠",
        1667 to "호러·서바이벌",
        1662 to "호러·서바이벌",
    )

    /** 사이드바/칩 표시 순서 (전체 제외) */
    val ORDER: List<String> = listOf(
        "액션", "어드벤처", "RPG", "전략", "시뮬레이션", "퍼즐",
        "캐주얼", "인디", "멀티", "레이싱·스포츠", "호러·서바이벌",
    )

    /** Steam 장르명(description) → 한글 (appdetails 보강용) */
    private val BY_EN_NAME: Map<String, String> = mapOf(
        "Action" to "액션",
        "Adventure" to "어드벤처",
        "RPG" to "RPG",
        "Role-playing" to "RPG",
        "Strategy" to "전략",
        "Simulation" to "시뮬레이션",
        "Puzzle" to "퍼즐",
        "Casual" to "캐주얼",
        "Indie" to "인디",
        "Massively Multiplayer" to "멀티",
        "Racing" to "레이싱·스포츠",
        "Sports" to "레이싱·스포츠",
        "Horror" to "호러·서바이벌",
        "Survival" to "호러·서바이벌",
    )

    fun fromTagIds(tagIds: List<Int>): String? =
        tagIds.firstNotNullOfOrNull { BY_TAG_ID[it] }

    fun fromEnName(name: String): String? = BY_EN_NAME[name]

    /** URL 슬러그/러시아 카테고리 → 한글 (AppStorrent 등) */
    private val BY_SLUG: Map<String, String> = mapOf(
        "action" to "액션", "экшен" to "액션",
        "adventure" to "어드벤처", "приключения" to "어드벤처",
        "rpg" to "RPG", "role-playing" to "RPG",
        "strategy" to "전략", "стратегии" to "전략",
        "simulation" to "시뮬레이션", "симуляторы" to "시뮬레이션",
        "puzzle" to "퍼즐", "головоломки" to "퍼즐",
        "casual" to "캐주얼", "казуальные" to "캐주얼",
        "indie" to "인디",
        "racing" to "레이싱·스포츠", "sports" to "레이싱·스포츠",
        "спорт" to "레이싱·스포츠",
        "horror" to "호러·서바이벌", "survival" to "호러·서바이벌",
        "хоррор" to "호러·서바이벌",
        // AppStorrent URL 슬러그 (arcade/platformer/simulator/shooter 등)
        "arcade" to "캐주얼", "аркады" to "캐주얼",
        "platformer" to "어드벤처", "платформеры" to "어드벤처",
        "simulator" to "시뮬레이션",
        "shooter" to "액션", "fighter" to "액션", "stealth" to "액션",
        "roguelite" to "RPG", "roguelike" to "RPG",
        "craft" to "어드벤처", "story" to "어드벤처",
        "гонки" to "레이싱·스포츠",
        "sandbox" to "인디",
    )

    fun fromSlug(slug: String?): String? {
        if (slug.isNullOrBlank()) return null
        val key = slug.trim().lowercase().replace('_', '-').trim('/')
        return BY_SLUG[key] ?: BY_SLUG[key.substringBefore('/')]
    }

    /** 태그 CSV에 게임 장르 콤마 구분으로 넣기 위한 헬퍼 */
    fun gameTags(genre: String?, store: String): List<String> =
        listOfNotNull(com.borasarang.macjupjup.util.Constants.TAG_GAME, genre, store).filter { it.isNotBlank() }
}
