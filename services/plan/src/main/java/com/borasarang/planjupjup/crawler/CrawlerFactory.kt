package com.borasarang.planjupjup.crawler

import com.borasarang.planjupjup.crawler.brand.BrandListCrawler
import com.borasarang.planjupjup.crawler.carrier.KtmMobileCrawler
import com.borasarang.planjupjup.crawler.carrier.LiivMCrawler
import com.borasarang.planjupjup.crawler.compare.MoyoCrawler
import com.borasarang.planjupjup.crawler.compare.MvnohubCrawler
import com.borasarang.planjupjup.data.db.PlanDatabase
import com.borasarang.planjupjup.data.db.entity.CrawlSource
import com.borasarang.planjupjup.util.Constants

/** 소스 type+id → 크롤러 구현체 분기. 미지원 소스는 예외 (Worker가 격리) */
class CrawlerFactory(private val db: PlanDatabase) {

    fun create(source: CrawlSource): PlanCrawler {
        return when (source.type) {
            Constants.TYPE_COMPARE_SITE -> when (source.id) {
                Constants.SOURCE_MVNOHUB -> MvnohubCrawler(source)
                Constants.SOURCE_MOYO -> MoyoCrawler(source)
                else -> throw IllegalArgumentException("미지원 비교사이트: ${source.id}")
            }
            Constants.TYPE_CARRIER_SITE -> when (source.id) {
                Constants.SOURCE_KTMMOBILE -> KtmMobileCrawler(source)
                Constants.SOURCE_LIIVM -> LiivMCrawler(source)
                else -> throw IllegalArgumentException("미지원 통신사 사이트: ${source.id}")
            }
            Constants.TYPE_BRAND_LIST -> BrandListCrawler(source, db)
            else -> throw IllegalArgumentException("미지원 소스 타입: ${source.type}")
        }
    }
}
