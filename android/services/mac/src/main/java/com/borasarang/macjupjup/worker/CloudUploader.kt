package com.borasarang.macjupjup.worker

import com.borasarang.common.util.net.NetMeter
import com.borasarang.macjupjup.data.db.entity.App
import com.borasarang.macjupjup.data.db.entity.AppSourceMapping
import com.borasarang.macjupjup.data.db.entity.CommunityPost
import com.borasarang.macjupjup.data.db.entity.NewsAppRelation
import com.borasarang.macjupjup.data.db.entity.NewsArticle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 클라우드 push 업로더 (PLAN_v27 T-27-2).
 * Context 없음 — 순수 함수 + HttpURLConnection. 단위 테스트는 JDK HttpServer로.
 * 페이로드 키는 cloud ingest 스펙과 동일 (camelCase 엔티티 미러).
 */
object CloudUploader {

    data class PostResult(val ok: Boolean, val code: Int, val body: String)

    fun appsPayload(apps: List<App>, mappings: List<AppSourceMapping>): String {
        val byApp = mappings.groupBy { it.appId }
        return buildJsonObject {
            putJsonArray("drafts") {
                apps.forEach { a ->
                    add(buildJsonObject {
                        put("app", appJson(a))
                        putJsonArray("mappings") {
                            (byApp[a.id] ?: emptyList()).forEach { m ->
                                add(buildJsonObject {
                                    put("appId", m.appId)
                                    put("sourceName", m.sourceName)
                                    m.sourceUrl?.let { put("sourceUrl", it) }
                                    put("fetchedAt", m.fetchedAt)
                                })
                            }
                        }
                    })
                }
            }
        }.toString()
    }

    fun newsPayload(articles: List<NewsArticle>, relations: List<NewsAppRelation>): String {
        return buildJsonObject {
            putJsonArray("articles") { articles.forEach { add(newsJson(it)) } }
            putJsonArray("relations") {
                relations.forEach { r ->
                    add(buildJsonObject {
                        put("newsId", r.newsId)
                        put("appId", r.appId)
                    })
                }
            }
        }.toString()
    }

    fun communityPayload(posts: List<CommunityPost>): String {
        return buildJsonObject {
            putJsonArray("posts") {
                posts.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("sourceId", p.sourceId)
                        put("sourceName", p.sourceName)
                        put("main", p.main)
                        put("title", p.title)
                        p.summary?.let { put("summary", it) }
                        p.authorName?.let { put("authorName", it) }
                        put("originalUrl", p.originalUrl)
                        p.thumbnailUrl?.let { put("thumbnailUrl", it) }
                        p.commentCount?.let { put("commentCount", it) }
                        p.viewCount?.let { put("viewCount", it) }
                        p.contentHtml?.let { put("contentHtml", it) }
                        put("publishedAt", p.publishedAt)
                        put("collectedAt", p.collectedAt)
                    })
                }
            }
        }.toString()
    }

    fun post(baseUrl: String, token: String, kind: String, body: String, timeoutMs: Int = 20_000): PostResult {
        var conn: HttpURLConnection? = null
        return try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn = (URL("${baseUrl.trimEnd('/')}/ingest/$kind").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = (stream?.bufferedReader()?.readText() ?: "").take(2000)
            NetMeter.record("mac", resp.toByteArray().size.toLong(), bytes.size.toLong())
            PostResult(code in 200..299, code, resp)
        } catch (e: Exception) {
            PostResult(false, -1, (e.message ?: e.javaClass.simpleName).take(500))
        } finally {
            conn?.disconnect()
        }
    }

    internal fun appJson(a: App): JsonObject {
        return buildJsonObject {
            put("id", a.id)
            put("platform", a.platform)
            put("name", a.name)
            put("developer", a.developer)
            put("license", a.license)
            put("price", a.price)
            put("currency", a.currency)
            put("category", a.category)
            a.tags?.let { put("tags", it) }
            a.trackId?.let { put("trackId", it) }
            a.repoFullName?.let { put("repoFullName", it) }
            a.homepageUrl?.let { put("homepageUrl", it) }
            a.version?.let { put("version", it) }
            a.prevVersion?.let { put("prevVersion", it) }
            a.releaseNotesSummary?.let { put("releaseNotesSummary", it) }
            a.releaseDate?.let { put("releaseDate", it) }
            a.descriptionSnippet?.let { put("descriptionSnippet", it) }
            a.longDescription?.let { put("longDescription", it) }
            a.iconUrl?.let { put("iconUrl", it) }
            a.descriptionKo?.let { put("descriptionKo", it) }
            a.longDescriptionKo?.let { put("longDescriptionKo", it) }
            a.releaseNotes?.let { put("releaseNotes", it) }
            a.releaseNotesKo?.let { put("releaseNotesKo", it) }
            a.sellerName?.let { put("sellerName", it) }
            a.fileSize?.let { put("fileSize", it) }
            a.minOs?.let { put("minOs", it) }
            a.contentRating?.let { put("contentRating", it) }
            a.forks?.let { put("forks", it) }
            a.issues?.let { put("issues", it) }
            a.licenseName?.let { put("licenseName", it) }
            a.screenshotUrls?.let { put("screenshotUrls", it) }
            a.averageRating?.let { put("averageRating", it) }
            a.ratingCount?.let { put("ratingCount", it) }
            a.stars?.let { put("stars", it) }
            a.primaryLanguage?.let { put("primaryLanguage", it) }
            a.supportedLanguages?.let { put("supportedLanguages", it) }
            a.topics?.let { put("topics", it) }
            put("firstSeenAt", a.firstSeenAt)
            put("lastUpdatedAt", a.lastUpdatedAt)
            put("isNew", a.isNew)
            a.sourceId?.let { put("sourceId", it) }
            a.licenseOverride?.let { put("licenseOverride", it) }
        }
    }

    internal fun newsJson(a: NewsArticle): JsonObject {
        return buildJsonObject {
            put("id", a.id)
            put("sourceId", a.sourceId)
            put("sourceName", a.sourceName)
            put("main", a.main)
            put("sub", a.sub)
            put("title", a.title)
            a.summary?.let { put("summary", it) }
            a.titleKo?.let { put("titleKo", it) }
            a.summaryKo?.let { put("summaryKo", it) }
            a.contentHtml?.let { put("contentHtml", it) }
            put("originalUrl", a.originalUrl)
            a.thumbnailUrl?.let { put("thumbnailUrl", it) }
            put("publishedAt", a.publishedAt)
            put("collectedAt", a.collectedAt)
            a.tags?.let { put("tags", it) }
        }
    }
}
