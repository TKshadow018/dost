package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun verifiedFreeModelsAppearBeforeUnstableOnes() {
        val lastStableIndex = OpenRouterClient.freeModels.indexOfLast { !OpenRouterClient.isUnstableFreeModel(it) }
        val firstUnstableIndex = OpenRouterClient.freeModels.indexOfFirst { OpenRouterClient.isUnstableFreeModel(it) }
        assertTrue("stable models must be listed before unstable ones", firstUnstableIndex == -1 || lastStableIndex < firstUnstableIndex)
    }

    @Test
    fun fallbackModelIsFreeAndStable() {
        assertTrue(OpenRouterClient.FALLBACK_FREE_MODEL in OpenRouterClient.freeModels)
        assertFalse(OpenRouterClient.isUnstableFreeModel(OpenRouterClient.FALLBACK_FREE_MODEL))
    }

    @Test
    fun paidModelsRemainSelectableButAreNotFree() {
        assertTrue("openai/gpt-4o-mini" in OpenRouterClient.modelCatalog)
        assertTrue("openai/gpt-4o-mini" !in OpenRouterClient.freeModels)
    }

    @Test
    fun unavailableFreeModelsAreRemoved() {
        val removed = setOf(
            "z-ai/glm-5.2:free", "deepseek/deepseek-r1:free", "deepseek/deepseek-chat-v3-0324:free",
            "meta-llama/llama-3.3-70b-instruct:free", "mistralai/mistral-small-3.1-24b-instruct:free",
            "google/gemini-2.0-flash-exp:free", "qwen/qwen3-235b-a22b:free", "microsoft/mai-ds-r1:free"
        )
        assertEquals(emptyList<String>(), OpenRouterClient.freeModels.filter { it in removed })
    }

    @Test
    fun catalogHasNoDuplicates() {
        assertEquals(OpenRouterClient.modelCatalog.size, OpenRouterClient.modelCatalog.distinct().size)
    }
}
