package com.borasarang.communityjupjup.crawler

import com.borasarang.communityjupjup.data.db.CommunityDatabase
import com.borasarang.communityjupjup.data.db.entity.CrawlSource
import com.borasarang.communityjupjup.util.Constants

/** 소스 type으로 크롤러 구현체 분기 (MVP: BOARD 단일) */
class CrawlerFactory(
    private val db: CommunityDatabase,
) {
    fun create(source: CrawlSource): CommunityCrawler {
        return when (source.type) {
            Constants.TYPE_BOARD -> BoardCrawler(source, db)
            else -> throw IllegalArgumentException("미지원 소스 type=${source.type}")
        }
    }
}
