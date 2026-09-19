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
        val p = AiProvider.GOOGLE_AI_STUDIO
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
        val p = AiProvider.OPENCODE_ZEN
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

    @Test
    fun 정적목록_시드기본포함() {
        val orIds = ModelCatalog.allModels(AiProvider.OPENROUTER).map { it.id }.toSet()
        assertTrue(orIds.contains("nvidia/nemotron-3-super-120b-a12b:free"))
        val zenIds = ModelCatalog.allModels(AiProvider.OPENCODE_ZEN).map { it.id }.toSet()
        assertTrue(zenIds.contains("big-pickle"))
        assertTrue(zenIds.contains("nemotron-3-ultra-free"))
    }

    @Test
    fun merge_정적삭제금지() = runBlocking {
        val p = AiProvider.OPENROUTER
        val baseIds = ModelCatalog.allModels(p).map { it.id }.toSet()
        // 원격이 비어도 정적 base는 유지
        ModelCatalog.merge(p, emptyList())
        assertEquals(baseIds, ModelCatalog.allModels(p).map { it.id }.toSet())
    }

    @Test
    fun merge_참조보호유지_미보호삭제() = runBlocking {
        val p = AiProvider.OPENCODE_ZEN
        val ghost = "test/ghost-model-9"
        // 1차: 원격에 있던 모델 (자동 투입됨)
        ModelCatalog.merge(p, listOf(AiClient.ModelInfo(id = ghost, name = ghost)))
        assertTrue(ModelCatalog.allModels(p).any { it.id == ghost })
        // 2차: 원격에서 사라짐 + 미보호 → 목록에서 제거
        ModelCatalog.merge(p, emptyList())
        assertFalse(ModelCatalog.allModels(p).any { it.id == ghost })
        // 3차: 다시 추가 후, 참조 보호 상태로 사라짐 → 유지 + 투입 유지
        ModelCatalog.merge(p, listOf(AiClient.ModelInfo(id = ghost, name = ghost)))
        ModelCatalog.merge(p, emptyList(), protectedIds = setOf(ghost))
        assertTrue("참조 보호 모델 유지", ModelCatalog.allModels(p).any { it.id == ghost })
        assertTrue(
            "참조 보호 모델 투입 유지",
            ModelCatalog.enabledModelsFor(p).any { it.id == ghost },
        )
    }
}
