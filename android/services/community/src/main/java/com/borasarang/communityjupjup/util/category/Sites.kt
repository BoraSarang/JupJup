package com.borasarang.communityjupjup.util.category

/** 사이트(1차) 정의. domain 기준 그룹, 화면 표시명 */
data class CommunitySite(
    val domain: String,
    val name: String,
    val short: String,
)

object CommunitySites {
    val ALL = listOf(
        CommunitySite("clien.net", "클리앙", "클"),
        CommunitySite("ppomppu.co.kr", "뽐뿌", "뽐"),
        CommunitySite("fmkorea.com", "에펨코리아", "에"),
        CommunitySite("ruliweb.com", "루리웹", "루"),
        CommunitySite("dcinside.com", "디시인사이드", "디"),
        CommunitySite("bobaedream.co.kr", "보배드림", "보"),
        CommunitySite("theqoo.net", "더쿠", "더"),
        CommunitySite("todayhumor.co.kr", "오늘의유머", "오"),
    )

    fun nameOf(domain: String): String = ALL.find { it.domain == domain }?.name ?: domain

    fun shortOf(domain: String): String = ALL.find { it.domain == domain }?.short ?: domain.take(1)
}
