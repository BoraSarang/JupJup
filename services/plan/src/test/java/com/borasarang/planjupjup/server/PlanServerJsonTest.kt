package com.borasarang.planjupjup.server

import com.borasarang.planjupjup.data.db.entity.Plan
import com.borasarang.planjupjup.data.repository.Insight
import com.borasarang.planjupjup.data.repository.InsightType
import com.borasarang.planjupjup.data.repository.PlanWithSources
import com.borasarang.planjupjup.data.repository.SettingsData
import com.borasarang.planjupjup.data.repository.ValueRankItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R4: 분리된 서버 JSON 매퍼 golden 테스트 (이동 검증용, 동작 동결) */
class PlanServerJsonTest {

    private fun plan() = Plan(
        id = "p1", carrierName = "테스트통신", mvnoNetwork = "SKT", planName = "요금제A",
        price = 10000, priceAfterDiscount = null, discountMonths = null,
        dataAmount = "15GB", voice = "무제한", sms = "기본제공", networkType = "5G",
        eventBadge = null, collectedAt = 1000L, firstCollectedAt = 500L,
        isNew = true, sourceId = null, tags = null,
    )

    @Test
    fun plansJson_빈목록_envelope() {
        assertEquals(
            """{"plans":[],"total":0,"page":1,"pageSize":50}""",
            plansJson(emptyList(), 0, 1, 50),
        )
    }

    @Test
    fun planFields_널할인_생략() {
        val json = Json.parseToJsonElement(
            planJson(PlanWithSources(plan(), emptyList())),
        ).toString()
        assertTrue(json.contains(""""planName":"요금제A""""))
        assertTrue(json.contains(""""sources":[]"""))
        assertFalse(json.contains("priceAfterDiscount"))
        assertFalse(json.contains("discountMonths"))
    }

    @Test
    fun settingsJson_키7종_그대로() {
        val json = settingsJson(
            SettingsData(port = 3001, retentionDays = 30, autoStart = true,
                watchdogIntervalSec = 60),
        )
        assertTrue(json.contains(""""port":3001"""))
        assertTrue(json.contains(""""notifNewPlan":true"""))
        assertFalse(json.contains("githubToken"))
    }

    @Test
    fun insightElement_타입매핑() {
        val json = insightElement(Insight(InsightType.WARNING, "제목", "본문")).toString()
        assertTrue(json.contains(""""type":"warning""""))
    }

    @Test
    fun valueItemElement_순위포함() {
        val json = valueItemElement(
            ValueRankItem("p1", "통신", "A", 10000, "15GB", "무제한", "기본", "5G", 15, 9.5, "최고"),
            1,
        ).toString()
        assertTrue(json.contains(""""rank":1"""))
        assertTrue(json.contains(""""score":9.5"""))
    }
}
