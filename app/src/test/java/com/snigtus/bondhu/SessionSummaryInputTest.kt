package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSummaryInputTest {

    @Test
    fun summaryInputUsesTheLastHundredMessages() {
        val messages = List(120) { index ->
            ChatMessage(
                role = if (index % 2 == 0) "user" else "assistant",
                content = "m$index",
                timestampMillis = index.toLong()
            )
        }
        val included = OpenRouterClient.summaryMessages(messages)
        assertEquals(OpenRouterClient.MAX_SUMMARY_MESSAGES, included.size)
        assertEquals(100, included.size)
        assertEquals("m20", included.first().content)
        assertEquals("m119", included.last().content)
    }

    @Test
    fun shortSessionIsSummarizedInFull() {
        val messages = List(7) { index -> ChatMessage("user", "m$index", timestampMillis = index.toLong()) }
        val included = OpenRouterClient.summaryMessages(messages)
        assertEquals(messages.map { it.content }, included.map { it.content })
    }

    @Test
    fun storedSummaryInjectionCapStaysAtSeven() {
        assertEquals(7, OpenRouterClient.MAX_SESSION_SUMMARY_ITEMS)
    }
}
