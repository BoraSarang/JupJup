package com.borasarang.communityjupjup.data.seed

import com.borasarang.communityjupjup.util.CommunityCategories

/**
 * 사이트별 추천 게시판 카탈로그 (V2 명세 기반).
 * 자동 탐색 대신 이 목록 + URL 직접 입력을 제공. 수집 미검증 항목 포함 가능.
 */
data class CatalogBoard(
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val categoryId: Int,
)

object BoardCatalog {

    private fun b(id: String, name: String, url: String, cat: Int) =
        CatalogBoard(id, name, url, cat)

    private val clienBase = "https://www.clien.net/service/board/"
    private val ppomBase = "https://www.ppomppu.co.kr/zboard/zboard.php?id="

    val BY_DOMAIN: Map<String, List<CatalogBoard>> = mapOf(
        "clien.net" to listOf(
            b("park", "모두의공원", "${clienBase}park", CommunityCategories.HUMOR),
            b("jirum", "알뜰구매", "${clienBase}jirum", CommunityCategories.HOTDEAL),
            b("sold", "중고장터", "${clienBase}sold", CommunityCategories.USED),
        ),
        "ppomppu.co.kr" to listOf(
            b("ppomppu", "국내뽐뿌", "${ppomBase}ppomppu", CommunityCategories.HOTDEAL),
            b("ppomppu_overseas", "해외뽐뿌", "${ppomBase}ppomppu_overseas", CommunityCategories.HOTDEAL),
        ),
        "fmkorea.com" to listOf(
            b("poten", "포텐", "https://www.fmkorea.com/index.php?mid=poten", CommunityCategories.BREAKING),
            b("humor", "유머", "https://www.fmkorea.com/index.php?mid=humor", CommunityCategories.HUMOR),
            b("soccer", "축구", "https://www.fmkorea.com/index.php?mid=soccer", CommunityCategories.SPORTS_CAR),
        ),
        "ruliweb.com" to listOf(
            b("best", "베스트", "https://bbs.ruliweb.com/best", CommunityCategories.BREAKING),
        ),
        "dcinside.com" to listOf(
            b("dcbest", "실시간베스트", "https://gall.dcinside.com/board/lists/?id=dcbest", CommunityCategories.BREAKING),
        ),
        "bobaedream.co.kr" to listOf(
            b("best", "베스트", "https://www.bobaedream.co.kr/list?code=best", CommunityCategories.HUMOR),
        ),
        "theqoo.net" to listOf(
            b("hot", "HOT", "https://theqoo.net/hot", CommunityCategories.BREAKING),
        ),
        "todayhumor.co.kr" to listOf(
            b("bestofbest", "베스트오브베스트", "http://todayhumor.co.kr/board/list.php?table=bestofbest", CommunityCategories.HUMOR),
        ),
    )

    fun forDomain(domain: String): List<CatalogBoard> = BY_DOMAIN[domain].orEmpty()
}
