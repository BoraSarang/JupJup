package com.borasarang.promptjournaljupjup.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** R20: ModelCatalog 투입 상태 단위 테스트 (영속 store 미부착 = 메모리 전용) */
class ModelCatalogTest {

    @Before
    fun setUp() {
        ModelCatalog.init()
    }

    @Test
    fun init_기본_전체투입() {
        for (p in AiProvider.entries) {
            val all = ModelCatalog.allModels(p).map { it.id }.toSet()
            val enabled = ModelCatalog.enabledModelsFor(p).map { it.id }.toSet()
            assertFalse("기본 목록 비어있음: $p", all.isEmpty())
            assertEquals(p.toString(), all, enabled)
        }
    }

    @Test
    fun 개별해제_해당모델만제외() = runBlocking {
        val p = AiProvider.NIM
        val first = ModelCatalog.allModels(p).first().id
        ModelCatalog.setModelEnabled(p, first, false)
        val enabled = ModelCatalog.enabledModelsFor(p).map { it.id }.toSet()
        assertFalse(enabled.contains(first))
        assertEquals(ModelCatalog.allModels(p).size - 1, enabled.size)
        ModelCatalog.setModelEnabled(p, first, true)
        assertTrue(ModelCatalog.enabledModelsFor(p).any { it.id == first })
    }

    @Test
    fun 모두해제_후_모두투입() = runBlocking {
        val p = AiProvider.GOOGLE_AI_STUDIO
        ModelCatalog.setAllEnabled(p, false)
        assertTrue(ModelCatalog.enabledModelsFor(p).isEmpty())
        ModelCatalog.setAllEnabled(p, true)
        assertEquals(
            ModelCatalog.allModels(p).map { it.id }.toSet(),
            ModelCatalog.enabledModelsFor(p).map { it.id }.toSet()
        )
    }

    @Test
    fun merge_명시해제보존_신규자동투입() = runBlocking {
        val p = AiProvider.NIM
        val baseX = ModelCatalog.allModels(p).first().id
        ModelCatalog.setModelEnabled(p, baseX, false)

        val remote = listOf(
            AiClient.ModelInfo(id = baseX, name = baseX),
            AiClient.ModelInfo(id = "test/new-model-1", name = "new-model-1"),
        )
        val result = ModelCatalog.merge(p, remote)

        val enabled = ModelCatalog.enabledModelsFor(p).map { it.id }.toSet()
        assertFalse("해제한 모델이 갱신으로 부활", enabled.contains(baseX))
        assertTrue("신규 모델 자동 투입", enabled.contains("test/new-model-1"))
        assertEquals(1, result.added)
    }
}
