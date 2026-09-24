package com.borasarang.planjupjup.crawler

import com.borasarang.common.crawl.CrawlHttpClient
import com.borasarang.common.crawl.HttpResult
import com.borasarang.planjupjup.util.Constants
import com.borasarang.planjupjup.util.DebugLogger

/** plan 전용 파사드 — common CrawlHttp 슈퍼셋 위임 (쿠키·ko-KR·postForm) */
object CrawlHttp {
    private val client = CrawlHttpClient(
        service = "plan",
        userAgent = Constants.USER_AGENT,
        acceptLanguage = "ko-KR,ko;q=0.9",
        acceptHtml = "text/html,application/json,*/*",
        defaultTimeoutSec = Constants.CRAWL_TIMEOUT_SEC,
        installGlobalCookies = true,
        logWarn = { DebugLogger.w("수집", it) },
    )

    fun get(url: String, timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC): HttpResult =
        client.get(url, timeoutSec)

    fun postForm(
        url: String,
        params: Map<String, String>,
        timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC,
    ): HttpResult = client.postForm(url, params, timeoutSec)
}
