package com.borasarang.macjupjup.crawler

import com.borasarang.macjupjup.crawler.github.GitHubReleasesCrawler
import com.borasarang.macjupjup.crawler.github.GitHubSearchCrawler
import com.borasarang.macjupjup.crawler.itunes.ITunesLookupPoller
import com.borasarang.macjupjup.crawler.itunes.ITunesNameMatcher
import com.borasarang.macjupjup.crawler.chart.ChartRssCrawler
import com.borasarang.macjupjup.crawler.mas.MacStoreDiscoveryCrawler
import com.borasarang.macjupjup.data.db.MacDatabase
import com.borasarang.macjupjup.data.db.entity.CrawlSource
import com.borasarang.macjupjup.util.Constants

/** 소스 type으로 크롤러 구현체 분기 (6종, PH·HN·MMB 제거).
 * R32: NEWS_RSS는 NewsRssCrawler(별도 경로, CrawlWorker에서 직접 분기) */
class CrawlerFactory(
    private val db: MacDatabase,
    private val githubToken: String = "",
) {
    fun create(source: CrawlSource): AppCrawler {
        return when (source.type) {
            Constants.TYPE_GITHUB_SEARCH -> GitHubSearchCrawler(source, githubToken)
            Constants.TYPE_GITHUB_RELEASES -> GitHubReleasesCrawler(source, db, githubToken)
            Constants.TYPE_CHART_RSS -> ChartRssCrawler(source)
            Constants.TYPE_ITUNES_LOOKUP -> ITunesLookupPoller(source, db)
            Constants.TYPE_NAME_MATCH -> ITunesNameMatcher(source, db)
            Constants.TYPE_MAS_DISCOVERY -> MacStoreDiscoveryCrawler(source)
            else -> throw IllegalArgumentException("미지원 소스 type=${source.type}")
        }
    }
}
