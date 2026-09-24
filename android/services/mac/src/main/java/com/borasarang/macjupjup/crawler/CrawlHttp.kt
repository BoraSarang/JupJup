package com.borasarang.macjupjup.crawler

import com.borasarang.common.util.net.NetMeter
import com.borasarang.macjupjup.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** JDK HttpURLConnection 기반 얇은 HTTP 계층. 브라우저 UA + gzip + 선택 헤더 */
object CrawlHttp {
    fun get(url: String, timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC): HttpResult =
        getWithHeaders(url, emptyMap(), timeoutSec)

    fun getWithHeaders(
        url: String,
        headers: Map<String, String>,
        timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC,
    ): HttpResult {
        var connection: HttpURLConnection? = null
        val txEstimate = url.toByteArray(Charsets.UTF_8).size.toLong() + 300L +
            headers.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size }
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Constants.USER_AGENT)
                setRequestProperty("Accept", "text/html,application/json,application/atom+xml,*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            readResult(connection, txEstimate).also {
                NetMeter.record("mac", it.rxBytes, it.txBytes)
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResult(connection: HttpURLConnection, txBytes: Long = 0L): HttpResult {
        val code = try {
            connection.responseCode
        } catch (e: Exception) {
            return HttpResult(-1, "", e.message, 0L, txBytes)
        }
        // 304 Not Modified는 정상(변경 없음)으로 취급, 바디는 빈 문자열
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return HttpResult(code, "", null, 0L, txBytes)
        }
        if (code !in 200..299) {
            return HttpResult(code, "", "HTTP $code", 0L, txBytes)
        }
        return try {
            val encoding = connection.contentEncoding ?: ""
            val raw = connection.inputStream
            val stream = if (encoding.contains("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            HttpResult(code, text, null, text.toByteArray(Charsets.UTF_8).size.toLong(), txBytes)
        } catch (e: Exception) {
            HttpResult(code, "", e.message, 0L, txBytes)
        }
    }
}

data class HttpResult(
    val code: Int,
    val body: String,
    val error: String?,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
) {
    val isOk: Boolean get() = code in 200..299 && error == null
}
