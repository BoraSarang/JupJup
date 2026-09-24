package com.borasarang.macjupjup.crawler

import com.borasarang.common.crawl.CrawlHttpClient
import com.borasarang.common.crawl.HttpResult
import com.borasarang.macjupjup.util.Constants

/** mac 전용 파사드 — common CrawlHttp 슈퍼셋 위임 (서비스 라벨·UA 주입) */
object CrawlHttp {
    private val client = CrawlHttpClient(
        service = "mac",
        userAgent = Constants.USER_AGENT,
        acceptLanguage = "en-US,en;q=0.9",
        acceptHtml = "text/html,application/json,application/atom+xml,*/*",
        defaultTimeoutSec = Constants.CRAWL_TIMEOUT_SEC,
    )

    fun get(url: String, timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC): HttpResult =
        client.get(url, timeoutSec)

    fun getWithHeaders(
        url: String,
        headers: Map<String, String>,
        timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC,
    ): HttpResult = client.getWithHeaders(url, headers, timeoutSec)
}
