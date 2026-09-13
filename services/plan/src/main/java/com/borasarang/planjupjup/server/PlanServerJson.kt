package com.borasarang.planjupjup.server

import com.borasarang.common.server.putIfNotNull
import com.borasarang.planjupjup.data.repository.BrandStats
import com.borasarang.planjupjup.data.repository.CollectionHealth
import com.borasarang.planjupjup.data.repository.DistributionBucket
import com.borasarang.planjupjup.data.repository.Insight
import com.borasarang.planjupjup.data.repository.InsightType
import com.borasarang.planjupjup.data.repository.NetworkStats
import com.borasarang.planjupjup.data.repository.OverviewStats
import com.borasarang.planjupjup.data.repository.PlanWithSources
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.data.repository.SourceHealth
import com.borasarang.planjupjup.data.repository.TrendPoint
import com.borasarang.planjupjup.data.repository.ValueRankItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 서버 JSON 매퍼 (R4). 순수 함수 — 단위 테스트 가능.
 * 동작 동결: HttpServerService에서 이동만 (escape 절단 정책 포함).
 */
internal fun plansJson(plans: List<PlanWithSources>, total: Int, page: Int, pageSize: Int): String {
    return buildJsonObject {
        put("plans", buildJsonArray { plans.forEach { add(planElement(it)) } })
        put("total", total)
        put("page", page)
        put("pageSize", pageSize)
    }.toString()
}

internal fun planJson(item: PlanWithSources): String = planElement(item).toString()

internal fun planElement(item: PlanWithSources): JsonElement {
    val fields = planFields(item)
    return buildJsonObject {
        fields.entries.forEach { (key, value) -> put(key, value) }
        put("sources", buildJsonArray {
            item.sources.forEach { s ->
                add(
                    buildJsonObject {
                        put("sourceName", s.sourceName)
                        put("sourceUrl", s.sourceUrl)
                    },
                )
            }
        })
    }
}

internal fun planFields(item: PlanWithSources): kotlinx.serialization.json.JsonObject {
    val p = item.plan
    return buildJsonObject {
        put("id", p.id)
        put("carrierName", p.carrierName)
        put("mvnoNetwork", p.mvnoNetwork)
        put("planName", p.planName)
        put("price", p.price)
        putIfNotNull("priceAfterDiscount", p.priceAfterDiscount)
        putIfNotNull("discountMonths", p.discountMonths)
        put("dataAmount", p.dataAmount)
        put("voice", p.voice)
        put("sms", p.sms)
        put("networkType", p.networkType)
        putIfNotNull("eventBadge", p.eventBadge)
        put("collectedAt", p.collectedAt)
        put("firstCollectedAt", p.firstCollectedAt)
        put("isNew", p.isNew)
        putIfNotNull("tags", p.tags)
    }
}

internal fun settingsJson(s: SettingsData): String {
    return buildJsonObject {
        put("port", s.port)
        put("retentionDays", s.retentionDays)
        put("autoStart", s.autoStart)
        put("watchdogIntervalSec", s.watchdogIntervalSec)
        put("notifCrawlComplete", s.notifCrawlComplete)
        put("notifNewPlan", s.notifNewPlan)
        put("notifFailure", s.notifFailure)
    }.toString()
}

// ---------- 통계 JSON 직렬화 ----------

internal fun overviewJson(o: OverviewStats): String {
    return buildJsonObject {
        put("generatedAt", System.currentTimeMillis())
        put("cache", "memory")
        put("totalPlans", o.totalPlans)
        put("brandCount", o.brandCount)
        put("networkCount", o.networkCount)
        put("newThisWeek", o.newThisWeek)
        put("avgPrice", o.avgPrice)
        put("minPrice", o.minPrice)
        put("maxPrice", o.maxPrice)
        put("unlimitedRatio", o.unlimitedRatio)
        put("g5Ratio", o.g5Ratio)
        put("avgDataGb", o.avgDataGb)
        put("crawlCountToday", o.crawlCountToday)
        put("crawlFail24h", o.crawlFail24h)
        putIfNotNull("lastCollectedAt", o.lastCollectedAt)
    }.toString()
}

internal fun brandElement(b: BrandStats): JsonElement {
    return buildJsonObject {
        put("brand", b.brand)
        put("mvnoNetwork", b.mvnoNetwork)
        put("planCount", b.planCount)
        put("newCount", b.newCount)
        put("avgPrice", b.avgPrice)
        put("minPrice", b.minPrice)
        put("maxPrice", b.maxPrice)
        put("avgDataGb", b.avgDataGb)
        put("g5Count", b.g5Count)
        putIfNotNull("pricePerGb", b.pricePerGb)
    }
}

internal fun networkElement(n: NetworkStats): JsonElement {
    return buildJsonObject {
        put("network", n.network)
        put("planCount", n.planCount)
        put("avgPrice", n.avgPrice)
        put("minPrice", n.minPrice)
        put("maxPrice", n.maxPrice)
        put("avgDataGb", n.avgDataGb)
        put("g5Ratio", n.g5Ratio)
        put("unlimitedRatio", n.unlimitedRatio)
        putIfNotNull("pricePerGb", n.pricePerGb)
    }
}

internal fun bucketElement(b: DistributionBucket): JsonElement {
    return buildJsonObject {
        put("label", b.label)
        put("count", b.count)
        putIfNotNull("min", b.min)
        putIfNotNull("max", b.max)
    }
}

internal fun pointElement(p: TrendPoint): JsonElement {
    return buildJsonObject {
        put("label", p.label)
        put("ts", p.ts)
        put("plansFound", p.plansFound)
        put("plansNew", p.plansNew)
            put("plansUpdated", p.plansUpdated)
        put("failCount", p.failCount)
    }
}

internal fun valueItemElement(item: ValueRankItem, rank: Int): JsonElement {
    return buildJsonObject {
        put("rank", rank)
        put("id", item.id)
        put("carrierName", item.carrierName)
        put("planName", item.planName)
        put("price", item.price)
        put("dataAmount", item.dataAmount)
        put("voice", item.voice)
        put("sms", item.sms)
        put("networkType", item.networkType)
        put("dataGb", item.dataGb)
        put("score", item.score)
        put("scoreLabel", item.scoreLabel)
    }
}

internal fun healthJson(h: CollectionHealth): String {
    return buildJsonObject {
        put("generatedAt", System.currentTimeMillis())
        put("cache", "memory")
        put("success24h", h.success24h)
        put("fail24h", h.fail24h)
        put("avgDurationSec", h.avgDurationSec)
        putIfNotNull("lastCollectedAt", h.lastCollectedAt)
        put("sources", buildJsonArray { h.sources.forEach { add(sourceHealthElement(it)) } })
    }.toString()
}

internal fun sourceHealthElement(s: SourceHealth): JsonElement {
    return buildJsonObject {
        put("sourceId", s.sourceId)
        put("sourceName", s.sourceName)
        put("lastStatus", s.lastStatus)
        putIfNotNull("lastRunAt", s.lastRunAt)
        putIfNotNull("errorMessage", s.errorMessage)
        put("successRate", s.successRate)
    }
}

internal fun insightElement(i: Insight): JsonElement {
    return buildJsonObject {
        put("type", when (i.type) {
            InsightType.POSITIVE -> "positive"
            InsightType.WARNING -> "warning"
            InsightType.INFO -> "info"
        })
        put("title", i.title)
        put("text", i.text)
    }
}
