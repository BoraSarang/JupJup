package com.borasarang.communityjupjup.util.category

/** 통합 카테고리 10종 (V2 §3). 고정 시드 — Room 엔티티 없이 정적 제공 */
data class CommunityCategory(
    val id: Int,
    val name: String,
    val slug: String,
    val description: String,
)

object CommunityCategories {
    const val BREAKING = 1
    const val HUMOR = 2
    const val TECH = 3
    const val NEW_PRODUCT = 4
    const val GAME = 5
    const val SPORTS_CAR = 6
    const val HOTDEAL = 7
    const val USED = 8
    const val LIFE = 9
    const val ECONOMY = 10

    val ALL = listOf(
        CommunityCategory(1, "속보/인기", "breaking", "각 커뮤니티 BEST/HOT/포텐 모음 (메인)"),
        CommunityCategory(2, "유머/이슈", "humor", "유머, 짤, 이슈"),
        CommunityCategory(3, "IT/테크", "tech", "PC, 모바일, Apple"),
        CommunityCategory(4, "새로운소식/신제품", "new", "IT 신제품, 출시 소식"),
        CommunityCategory(5, "게임", "game", "콘솔, PC, 스팀"),
        CommunityCategory(6, "스포츠/자동차", "sports", "축구, 야구, 자동차"),
        CommunityCategory(7, "핫딜/알뜰구매/지름", "hotdeal", "새제품 할인 정보"),
        CommunityCategory(8, "중고장터", "used", "개인간 중고 거래"),
        CommunityCategory(9, "생활/문화", "life", "요리, 육아, 음악, 영화, 여행"),
        CommunityCategory(10, "경제/주식", "economy", "주식, 코인, 부동산"),
    )

    fun byId(id: Int): CommunityCategory? = ALL.find { it.id == id }

    fun nameOf(id: Int): String = byId(id)?.name ?: "미분류"
}
