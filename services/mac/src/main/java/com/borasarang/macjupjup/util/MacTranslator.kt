package com.borasarang.macjupjup.util

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * 다국어 → 한글 번역 (T-100: 소스 언어 Auto).
 * 기존 영문 고정이던 소스를 ML Kit 언어 감지로 자동 판별(영·중·일·러 등)하고,
 * 1순위 ML Kit 온디바이스(무료·외부 전송 없음), 실패 시 gtx(sl=auto) 폴백.
 * 불가 시 null (fail-open). 이미 한글 위주 텍스트는 스킵.
 */
object MacTranslator {

    private val langIdClient by lazy { LanguageIdentification.getClient() }

    private val translatorCache = mutableMapOf<String, Translator>()
    private val readyModels = mutableSetOf<String>()

    /** BCP-47 감지 → ML Kit 번역 코드. 미지원·불명 시 영어 폴백 */
    suspend fun identifySource(text: String): String {
        val tag = try {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    langIdClient.identifyLanguage(text.take(1000))
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resume("und") }
                }
            }
        } catch (_: Exception) {
            null
        } ?: "und"
        if (tag == "und") return TranslateLanguage.ENGLISH
        return try {
            TranslateLanguage.fromLanguageTag(tag) ?: TranslateLanguage.ENGLISH
        } catch (_: Exception) {
            TranslateLanguage.ENGLISH
        }
    }

    @Synchronized
    private fun translatorFor(source: String): Translator {
        return translatorCache.getOrPut(source) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(TranslateLanguage.KOREAN)
                    .build(),
            )
        }
    }

    /** 모델 준비 (20초 상한). 미터드망 대기 무한 방지 */
    suspend fun ensureModel(source: String, timeoutMs: Long = 20_000): Boolean {
        if (readyModels.contains(source)) return true
        val ok = try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    translatorFor(source)
                        .downloadModelIfNeeded(DownloadConditions.Builder().build())
                        .addOnSuccessListener { cont.resume(true) }
                        .addOnFailureListener { cont.resume(false) }
                }
            } ?: false
        } catch (e: Exception) {
            DebugLogger.d("번역", "모델 준비 예외 ($source): ${e.message}")
            false
        }
        if (ok) readyModels.add(source)
        else DebugLogger.d("번역", "ML Kit 모델 미준비 ($source) — gtx 폴백 사용")
        return ok
    }

    /** 어떤 언어든 → 한글. 자동 감지 + ML Kit 우선, gtx(sl=auto) 폴백.
     *  T-131: 개행 보존 — 여러 줄이면 줄 단위로 나눠 번역 후 `\n` 결합.
     *  T-141: 마크다운 보호 — 코드펜스 스킵 + 줄앞 마커 분리 + 링크 URL·인라인코드 미번역. */
    suspend fun translateAutoToKo(text: String): String? {
        val t = text.trim()
        if (t.isBlank() || !needsTranslation(t)) return null
        if (!t.contains("\n")) return translateMdLine(t)
        val lines = t.split("\n")
        var inFence = false
        var done = 0
        val out = lines.toMutableList()
        for ((i, rawLine) in lines.withIndex()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
                continue
            }
            if (inFence || trimmed.isBlank() || isReadmeMarker(trimmed)) continue
            if (!needsTranslation(rawLine)) continue
            val tr = translateMdLine(rawLine)
            if (tr != null && tr != rawLine.trim()) {
                val (marker, _) = stripLeadingMarker(rawLine)
                out[i] = marker + tr
                done++
            }
            kotlinx.coroutines.delay(200)
        }
        DebugLogger.i("번역", "[FEATURE] 마크다운 줄단위 번역 ${lines.size}줄 중 ${done}줄")
        if (done == 0) return null
        return out.joinToString("\n").trim().ifBlank { null }
    }

    /** 마크다운 한 줄 번역: 앞마커 분리 → 본문 조각별 번역 → 재결합 */
    private suspend fun translateMdLine(rawLine: String): String? {
        val (marker, body) = stripLeadingMarker(rawLine)
        if (body.isBlank() || !needsTranslation(body)) return null
        val sb = StringBuilder()
        var changed = false
        for ((translatable, seg) in splitMdLine(body)) {
            if (!translatable || !needsTranslation(seg)) {
                sb.append(seg)
                continue
            }
            val tr = translateOneBlock(seg.trim())
            if (tr != null) {
                sb.append(tr)
                changed = true
            } else {
                sb.append(seg)
            }
            kotlinx.coroutines.delay(200)
        }
        if (!changed) return null
        return marker + sb.toString()
    }

    /** 단일 블록(개행 없음) 번역 본체 */
    private suspend fun translateOneBlock(t: String): String? {
        val source = identifySource(t)
        if (source == TranslateLanguage.KOREAN) return null
        DebugLogger.i("번역", "[FEATURE] 자동번역 감지언어=$source ${t.take(40)}")
        if (ensureModel(source)) {
            try {
                val out: String? = suspendCancellableCoroutine { cont ->
                    translatorFor(source).translate(t.take(2000))
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { e ->
                            DebugLogger.d("번역", "ML Kit 실패 ($source), 폴백: ${e.message}")
                            cont.resume(null)
                        }
                }
                if (!out.isNullOrBlank()) return out
            } catch (e: Exception) {
                DebugLogger.d("번역", "ML Kit 예외 ($source), 폴백: ${e.message}")
            }
        }
        return translateGtx(t)
    }

    /** 기존 영→한 진입점 (호환 유지, 내부는 자동 감지) */
    suspend fun translateEnToKo(text: String): String? = translateAutoToKo(text)

    /** gtx 폴백 (sl=auto, 키 불필요, 실패·429 시 null) */
    internal suspend fun translateGtx(text: String): String? {
        return try {
            val q = java.net.URLEncoder.encode(text.take(1500), "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=auto&tl=ko&dt=t&q=$q"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MacJupJup/1.1")
                if (conn.responseCode != 200) {
                    DebugLogger.d("번역", "gtx HTTP ${conn.responseCode}")
                    return null
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseGtx(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            DebugLogger.d("번역", "gtx 예외: ${e.message}")
            null
        }
    }

    /** [[["번역문","원문",...]]] → 번역문 결합 */
    internal fun parseGtx(body: String): String? {
        return try {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonArray
            val sentences = root[0].jsonArray
            val out = sentences.mapNotNull { s ->
                s.jsonArray.getOrNull(0)?.jsonPrimitive?.content
            }.joinToString("")
            out.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 번역 필요 판정. 한글 비중 50% 초과면 이미 한국어 → 스킵.
     * 영·중·일·러 등 그 외는 번역 대상 (기존 isEnglish는 ASCII만 통과시켜 중·러·일을 버렸음).
     */
    fun needsTranslation(text: String): Boolean {
        if (text.isBlank()) return false
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val ko = text.count { it in '가'..'힣' }
        return ko.toDouble() / letters <= 0.5
    }

    /** 포털 소개/상세 분리 마커줄 판정 (T-073 규격, 수집·웹과 동일 문자열) */
    fun isReadmeMarker(line: String): Boolean {
        return line.trim() == "— README —"
    }

    /** 줄 앞 마크다운 마커 분리 ("## Title" → "## " + "Title"). 마커는 번역 제외 */
    internal fun stripLeadingMarker(line: String): Pair<String, String> {
        val m = Regex("^(\\s*-\\s\\[[ xX]\\]\\s+|#{1,6}\\s+|>\\s?|\\s*(?:[-*+]|\\d+[.)])\\s+)").find(line)
        return if (m != null) m.value to line.substring(m.value.length) else "" to line
    }

    /** 마크다운 본문을 번역가능/보존 조각으로 분할. true=번역 대상.
     *  인라인코드(`code`)·대괄호·링크URL(`](url)`)은 보존, 링크 텍스트만 번역. */
    internal fun splitMdLine(line: String): List<Pair<Boolean, String>> {
        val out = mutableListOf<Pair<Boolean, String>>()
        val buf = StringBuilder()
        fun flushText() {
            if (buf.isNotEmpty()) {
                out += true to buf.toString()
                buf.clear()
            }
        }
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '`') {
                val end = line.indexOf('`', i + 1)
                flushText()
                if (end < 0) {
                    out += false to line.substring(i)
                    break
                }
                out += false to line.substring(i, end + 1)
                i = end + 1
            } else if (c == '[') {
                val close = line.indexOf(']', i + 1)
                val hasParen = close + 1 < line.length && line[close + 1] == '('
                val closeParen = if (hasParen) line.indexOf(')', close + 2) else -1
                if (close > i + 1 && hasParen && closeParen > close) {
                    var bang = ""
                    if (buf.isNotEmpty() && buf.last() == '!') {
                        buf.deleteCharAt(buf.length - 1)
                        bang = "!"
                    }
                    flushText()
                    out += false to (bang + "[")
                    out += true to line.substring(i + 1, close)
                    out += false to line.substring(close, closeParen + 1)
                    i = closeParen + 1
                } else {
                    buf.append(c)
                    i++
                }
            } else {
                buf.append(c)
                i++
            }
        }
        flushText()
        return out.filter { it.second.isNotEmpty() }
    }

    /** 한글 없음 + ASCII 문자 포함이면 영문으로 간주 (레거시, 호환 유지) */
    fun isEnglish(text: String): Boolean {
        if (text.contains(Regex("[가-힣]"))) return false
        return text.contains(Regex("[A-Za-z]"))
    }

    fun close() {
        try {
            translatorCache.values.forEach { runCatching { it.close() } }
            runCatching { langIdClient.close() }
        } catch (_: Exception) {
        } finally {
            translatorCache.clear()
            readyModels.clear()
        }
    }
}
