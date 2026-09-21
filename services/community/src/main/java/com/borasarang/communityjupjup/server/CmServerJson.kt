package com.borasarang.communityjupjup.server

import com.borasarang.common.server.putIfNotNull
import com.borasarang.communityjupjup.crawler.decodeImageUrls
import com.borasarang.communityjupjup.data.repository.PagedPosts
import com.borasarang.communityjupjup.data.repository.PostListItem
import com.borasarang.communityjupjup.data.repository.PostWithSourceList
import com.borasarang.communityjupjup.data.repository.SettingsView
import com.borasarang.communityjupjup.data.repository.SourceStatus
import com.borasarang.communityjupjup.util.CommunityCategories
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 서버 JSON 매퍼. 순수 함수 — 단위 테스트 가능.
 */
internal fun postsJson(paged: PagedPosts): String {
    return buildJsonObject {
        put("posts", buildJsonArray {
            paged.posts.forEach { item ->
                add(postElement(item))
            }
        })
        put("total", paged.total)
        put("page", paged.page)
        put("pageSize", paged.pageSize)
    }.toString()
}

internal fun postElement(item: PostListItem): JsonObject {
    val p = item.post
    return buildJsonObject {
        put("id", p.id)
        put("title", p.title)
        putIfNotNull("summary", p.summary)
        putIfNotNull("author", p.authorName)
        put("originalUrl", p.originalUrl)
        putIfNotNull("thumbnailUrl", p.thumbnailUrl)
        putIfNotNull("viewCount", p.viewCount)
        putIfNotNull("likeCount", p.likeCount)
        putIfNotNull("commentCount", p.commentCount)
        putIfNotNull("mallName", p.mallName)
        putIfNotNull("salePrice", p.salePrice)
        putIfNotNull("originalPrice", p.originalPrice)
        putIfNotNull("discountRate", p.discountRate)
        p.isSoldOut?.let { put("isSoldOut", it) }
        putIfNotNull("dealStatus", p.dealStatus)
        putIfNotNull("dealLocation", p.dealLocation)
        putIfNotNull("publishedAt", p.publishedAt)
        put("collectedAt", p.collectedAt)
        put("sourceId", p.sourceId)
        put("categoryId", p.categoryId)
        put("categoryName", item.categoryName ?: CommunityCategories.nameOf(p.categoryId))
        putIfNotNull("sourceName", item.sourceName)
        putIfNotNull("boardName", item.boardName)
        put("imageCount", decodeImageUrls(p.imageUrls).size)
    }
}

internal fun postDetailJson(item: PostWithSourceList): String {
    val p = item.post
    return buildJsonObject {
        put("id", p.id)
        put("title", p.title)
        putIfNotNull("summary", p.summary)
        putIfNotNull("author", p.authorName)
        put("originalUrl", p.originalUrl)
        putIfNotNull("thumbnailUrl", p.thumbnailUrl)
        putIfNotNull("viewCount", p.viewCount)
        putIfNotNull("likeCount", p.likeCount)
        putIfNotNull("commentCount", p.commentCount)
        putIfNotNull("mallName", p.mallName)
        putIfNotNull("salePrice", p.salePrice)
        putIfNotNull("originalPrice", p.originalPrice)
        putIfNotNull("discountRate", p.discountRate)
        p.isSoldOut?.let { put("isSoldOut", it) }
        putIfNotNull("dealStatus", p.dealStatus)
        putIfNotNull("dealLocation", p.dealLocation)
        putIfNotNull("publishedAt", p.publishedAt)
        put("collectedAt", p.collectedAt)
        put("sourceId", p.sourceId)
        put("categoryId", p.categoryId)
        put("categoryName", CommunityCategories.nameOf(p.categoryId))
        val images = decodeImageUrls(p.imageUrls)
        put("images", buildJsonArray { images.forEach { add(JsonPrimitive(it)) } })
        put("imageCount", images.size)
        item.source?.let { s ->
            put("source", buildJsonObject {
                put("id", s.id)
                put("name", s.name)
                put("domain", s.domain)
            })
        }
        item.board?.let { b ->
            put("board", buildJsonObject {
                put("id", b.id)
                put("boardId", b.boardId)
                put("boardName", b.boardName)
                put("boardUrl", b.boardUrl)
            })
        }
    }.toString()
}

internal fun categoriesJson(): String {
    return buildJsonObject {
        put("categories", buildJsonArray {
            CommunityCategories.ALL.forEach { c ->
                add(buildJsonObject {
                    put("id", c.id)
                    put("name", c.name)
                    put("slug", c.slug)
                    put("description", c.description)
                })
            }
        })
    }.toString()
}

internal fun sourcesJson(items: List<SourceStatus>, counts: Map<String, Int>): String {
    return buildJsonObject {
        put("sources", buildJsonArray {
            items.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    put("type", s.type)
                    put("domain", s.domain)
                    put("baseUrl", s.baseUrl)
                    put("enabled", s.enabled)
                    put("intervalMinutes", s.intervalMinutes)
                    putIfNotNull("lastRunAt", s.lastRunAt)
                    put("lastStatus", s.lastStatus)
                    putIfNotNull("errorMessage", s.errorMessage)
                    put("postCount", counts[s.id] ?: 0)
                })
            }
        })
    }.toString()
}

internal fun settingsJson(s: SettingsView): String {
    return buildJsonObject {
        put("port", s.port)
        put("retentionDays", s.retentionDays)
        put("autoStart", s.autoStart)
        put("watchdogIntervalSec", s.watchdogIntervalSec)
        put("notifCrawlComplete", s.notifCrawlComplete)
        put("notifNewPost", s.notifNewPost)
        put("notifFailure", s.notifFailure)
    }.toString()
}
