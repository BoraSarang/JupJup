package com.borasarang.communityjupjup.crawler

import com.borasarang.common.util.net.NetMeter
import com.borasarang.communityjupjup.util.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** JDK HttpURLConnection 기반 얇은 HTTP 계층. 브라우저 UA + gzip + 선택 헤더 */
object CrawlHttp {
    private val CHARSET_RE = Regex("charset=([^;\\s\"']+)", RegexOption.IGNORE_CASE)
    private val META_CHARSET_RE = Regex(
        "<meta[^>]+charset\\s*=\\s*[\"']?([^\"'\\s/>;]+)",
        RegexOption.IGNORE_CASE,
    )

    fun get(url: String, timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC): HttpResult =
        getWithHeaders(url, emptyMap(), timeoutSec)

    fun getWithHeaders(
        url: String,
        headers: Map<String, String>,
        timeoutSec: Long = Constants.CRAWL_TIMEOUT_SEC,
    ): HttpResult {        var connection: HttpURLConnection? = null
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
                NetMeter.record("community", it.rxBytes, it.txBytes)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 썸네일 프록시용 바이트 조회. Referer를 보내지 않아 CDN 핫링크 차단을 우회.
     * 이미지가 아니면(isImage=false) 바디를 버리고 타입만 반환.
     */
    fun getBytes(
        url: String,
        timeoutSec: Long = 10L,
        maxBytes: Int = 3 * 1024 * 1024,
    ): HttpBytes {
        var connection: HttpURLConnection? = null
        val txEstimate = url.toByteArray(Charsets.UTF_8).size.toLong() + 200L
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Constants.USER_AGENT)
                setRequestProperty("Accept", "image/*,*/*")
                // Referer 일부러 미전송 (CDN 403 회피 실측)
            }
            val code = try {
                connection.responseCode
            } catch (e: Exception) {
                return HttpBytes(-1, null, null, e.message, 0L, txEstimate).also {
                    NetMeter.record("community", 0L, txEstimate)
                }
            }
            if (code !in 200..299) {
                return HttpBytes(code, null, null, "HTTP $code", 0L, txEstimate).also {
                    NetMeter.record("community", 0L, txEstimate)
                }
            }
            val contentType = connection.contentType?.substringBefore(";")?.trim().orEmpty()
            if (!contentType.startsWith("image/")) {
                return HttpBytes(code, null, contentType, "not an image: $contentType", 0L, txEstimate).also {
                    NetMeter.record("community", 0L, txEstimate)
                }
            }
            val bytes = try {
                connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) {
                            return HttpBytes(code, null, contentType, "too large", total.toLong(), txEstimate).also {
                                NetMeter.record("community", total.toLong(), txEstimate)
                            }
                        }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (e: Exception) {
                return HttpBytes(code, null, contentType, e.message, 0L, txEstimate).also {
                    NetMeter.record("community", 0L, txEstimate)
                }
            }
            HttpBytes(code, bytes, contentType, null, bytes.size.toLong(), txEstimate).also {
                NetMeter.record("community", bytes.size.toLong(), txEstimate)
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
            val bytes = stream.use { it.readBytes() }
            // 구형 게시판(뽐뿌 등)은 EUC-KR — 헤더/meta에서 charset 판정, 기본 UTF-8
            val charset = detectCharset(connection.contentType, bytes)
            HttpResult(code, bytes.toString(charset), null, bytes.size.toLong(), txBytes)
        } catch (e: Exception) {
            HttpResult(code, "", e.message, 0L, txBytes)
        }
    }

    internal fun detectCharset(contentType: String?, bytes: ByteArray): java.nio.charset.Charset {        contentType?.let { ct ->
            CHARSET_RE.find(ct)
                ?.groupValues?.get(1)?.let { name ->
                    runCatching { charset(name) }.getOrNull()?.let { return it }
                }
        }
        // meta 태그는 ASCII 구간이라 바이트 그대로 판독 가능
        val head = bytes.take(4096).toByteArray().toString(Charsets.US_ASCII)
        META_CHARSET_RE.find(head)?.groupValues?.get(1)?.let { name ->
            // EUC-KR 별칭 (euc_kr, korean 등)
            val normalized = if (name.equals("euc_kr", ignoreCase = true)) "EUC-KR" else name
            runCatching { charset(normalized) }.getOrNull()?.let { return it }
        }
        return Charsets.UTF_8
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

data class HttpBytes(
    val code: Int,
    val bytes: ByteArray?,
    val contentType: String?,
    val error: String?,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
) {
    val isOk: Boolean get() = code in 200..299 && error == null && bytes != null
}
