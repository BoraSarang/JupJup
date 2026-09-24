package com.borasarang.common.crawl

import com.borasarang.common.util.net.NetMeter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * JDK HttpURLConnection 기반 얇은 HTTP 계층 (mac·plan·community 통합 슈퍼셋).
 * - 브라우저 UA + gzip + 선택 헤더 + 304 정상 취급
 * - 헤더/meta charset 판정 (EUC-KR 구형 게시판)
 * - 썸네일용 getBytes (Referer 미전송, image 콘텐츠만)
 * - 세션 사이트용 postForm + 전역 CookieManager (plan)
 * NetMeter 라벨·UA·Accept-Language는 서비스별 인스턴스 주입.
 */
class CrawlHttpClient(
    private val service: String,
    private val userAgent: String,
    private val acceptLanguage: String = "en-US,en;q=0.9",
    private val acceptHtml: String = "text/html,application/json,application/atom+xml,*/*",
    private val defaultTimeoutSec: Long = DEFAULT_TIMEOUT_SEC,
    private val installGlobalCookies: Boolean = false,
    private val logWarn: (String) -> Unit = {},
) {
    private val charsetRe = Regex("charset=([^;\\s\"']+)", RegexOption.IGNORE_CASE)
    private val metaCharsetRe = Regex(
        "<meta[^>]+charset\\s*=\\s*[\"']?([^\"'\\s/>;]+)",
        RegexOption.IGNORE_CASE,
    )

    init {
        if (installGlobalCookies) {
            try {
                if (java.net.CookieHandler.getDefault() == null) {
                    java.net.CookieHandler.setDefault(
                        java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL),
                    )
                }
            } catch (e: Exception) {
                logWarn("CookieManager 설정 실패: ${e.message}")
            }
        }
    }

    fun get(url: String, timeoutSec: Long = defaultTimeoutSec): HttpResult =
        getWithHeaders(url, emptyMap(), timeoutSec)

    fun getWithHeaders(
        url: String,
        headers: Map<String, String>,
        timeoutSec: Long = defaultTimeoutSec,
    ): HttpResult {
        var connection: HttpURLConnection? = null
        val txEstimate = estimateTx(url, headers)
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", acceptHtml)
                setRequestProperty("Accept-Language", acceptLanguage)
                setRequestProperty("Accept-Encoding", "gzip")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            readResult(connection, txEstimate).also {
                NetMeter.record(service, it.rxBytes, it.txBytes)
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
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "image/*,*/*")
            }
            val code = try {
                connection.responseCode
            } catch (e: Exception) {
                return HttpBytes(-1, null, null, e.message, 0L, txEstimate).also {
                    NetMeter.record(service, 0L, txEstimate)
                }
            }
            if (code !in 200..299) {
                return HttpBytes(code, null, null, "HTTP $code", 0L, txEstimate).also {
                    NetMeter.record(service, 0L, txEstimate)
                }
            }
            val contentType = connection.contentType?.substringBefore(";")?.trim().orEmpty()
            if (!contentType.startsWith("image/")) {
                return HttpBytes(code, null, contentType, "not an image: $contentType", 0L, txEstimate).also {
                    NetMeter.record(service, 0L, txEstimate)
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
                                NetMeter.record(service, total.toLong(), txEstimate)
                            }
                        }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (e: Exception) {
                return HttpBytes(code, null, contentType, e.message, 0L, txEstimate).also {
                    NetMeter.record(service, 0L, txEstimate)
                }
            }
            HttpBytes(code, bytes, contentType, null, bytes.size.toLong(), txEstimate).also {
                NetMeter.record(service, bytes.size.toLong(), txEstimate)
            }
        } finally {
            connection?.disconnect()
        }
    }

    fun postForm(
        url: String,
        params: Map<String, String>,
        timeoutSec: Long = defaultTimeoutSec,
    ): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            val body = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }.toByteArray(Charsets.UTF_8)
            val txEstimate = url.toByteArray(Charsets.UTF_8).size.toLong() + 300L + body.size
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = (timeoutSec * 1000).toInt()
                readTimeout = (timeoutSec * 1000).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Accept", "application/json, text/html, */*")
                setRequestProperty("Accept-Language", acceptLanguage)
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Content-Length", body.size.toString())
                outputStream.use { it.write(body) }
            }
            readResult(connection, txEstimate).also {
                NetMeter.record(service, it.rxBytes, it.txBytes)
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun estimateTx(url: String, headers: Map<String, String>): Long =
        url.toByteArray(Charsets.UTF_8).size.toLong() + 300L +
            headers.entries.sumOf { it.key.toByteArray().size + it.value.toByteArray().size }

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
            val charset = detectCharset(connection.contentType, bytes)
            HttpResult(code, bytes.toString(charset), null, bytes.size.toLong(), txBytes)
        } catch (e: Exception) {
            HttpResult(code, "", e.message, 0L, txBytes)
        }
    }

    internal fun detectCharset(contentType: String?, bytes: ByteArray): java.nio.charset.Charset {
        contentType?.let { ct ->
            charsetRe.find(ct)
                ?.groupValues?.get(1)?.let { name ->
                    runCatching { charset(name) }.getOrNull()?.let { return it }
                }
        }
        // meta 태그는 ASCII 구간이라 바이트 그대로 판독 가능
        val head = bytes.take(4096).toByteArray().toString(Charsets.US_ASCII)
        metaCharsetRe.find(head)?.groupValues?.get(1)?.let { name ->
            val normalized = if (name.equals("euc_kr", ignoreCase = true)) "EUC-KR" else name
            runCatching { charset(normalized) }.getOrNull()?.let { return it }
        }
        return Charsets.UTF_8
    }

    companion object {
        const val DEFAULT_TIMEOUT_SEC = 10L
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpBytes) return false
        return code == other.code &&
            bytes.contentEquals(other.bytes) &&
            contentType == other.contentType &&
            error == other.error &&
            rxBytes == other.rxBytes &&
            txBytes == other.txBytes
    }

    override fun hashCode(): Int {
        var result = code
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + rxBytes.hashCode()
        result = 31 * result + txBytes.hashCode()
        return result
    }
}
