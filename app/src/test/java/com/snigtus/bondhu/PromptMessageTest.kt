package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptMessageTest {

    @Test
    fun errorBubblesAreLeftOutOfPromptHistory() {
        val history = listOf(
            ChatMessage("user", "hi"),
            ChatMessage("assistant", "I couldn't read that reply.", category = ERROR_MESSAGE_CATEGORY),
            ChatMessage("user", "try again")
        )
        val prompt = messagesForPrompt(history)
        assertEquals(listOf("hi", "try again"), prompt.map { it.content })
        assertEquals(listOf("user", "user"), prompt.map { it.role })
    }

    @Test
    fun ordinaryMessagesStayInPromptHistory() {
        val history = listOf(
            ChatMessage("user", "hello"),
            ChatMessage("assistant", "Hi there")
        )
        assertEquals(history, messagesForPrompt(history))
    }

    @Test
    fun promptHistoryKeepsNewestMessagesWithinCharacterBudget() {
        val history = listOf(
            ChatMessage("user", "a".repeat(15_000)),
            ChatMessage("assistant", "b".repeat(15_000)),
            ChatMessage("user", "latest")
        )

        val prompt = messagesForPrompt(history)

        assertEquals(listOf(history[1], history[2]), prompt)
        assertTrue(prompt.sumOf { it.content.length } <= MAX_PROMPT_HISTORY_CHARACTERS)
    }
}
