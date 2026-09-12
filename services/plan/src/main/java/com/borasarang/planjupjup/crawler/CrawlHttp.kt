package com.borasarang.planjupjup.crawler

import com.borasarang.planjupjup.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** JDK HttpURLConnection 기반 얇은 HTTP 계층. 쿠키 유지 + 브라우저 UA + gzip 처리 */
object CrawlHttp {
    init {
        // KT엠모바일 등 세션 기반 AJAX 사이트 대응: 프로세스 전역 CookieManager
        try {
            if (java.net.CookieHandler.getDefault() == null) {
                java.net.CookieHandler.setDefault(
                    java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL),
                )
            }
        } catch (_: Exception) {
        }
    }

    fun get(url: String, timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Constants.USER_AGENT)
                setRequestProperty("Accept", "text/html,application/json,*/*")
                setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            readResult(connection)
        } finally {
            connection?.disconnect()
        }
    }

    fun postForm(
        url: String,
        params: Map<String, String>,
        timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC,
    ): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            val body = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }.toByteArray(Charsets.UTF_8)
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Constants.USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Accept", "application/json, text/html, */*")
                setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Content-Length", body.size.toString())
                outputStream.use { it.write(body) }
            }
            readResult(connection)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResult(connection: HttpURLConnection): HttpResult {
        val code = try {
            connection.responseCode
        } catch (e: Exception) {
            return HttpResult(-1, "", e.message)
        }
        if (code !in 200..299) {
            return HttpResult(code, "", "HTTP $code")
        }
        return try {
            val encoding = connection.contentEncoding ?: ""
            val raw = connection.inputStream
            val stream = if (encoding.contains("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            HttpResult(code, text, null)
        } catch (e: Exception) {
            HttpResult(code, "", e.message)
        }
    }
}

data class HttpResult(
    val code: Int,
    val body: String,
    val error: String?,
) {
    val isOk: Boolean get() = code in 200..299 && error == null
}
