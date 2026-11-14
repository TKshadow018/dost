package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptMessageTest {

    @Test
    fun noticesAreLeftOutOfPromptHistory() {
        val history = listOf(
            ChatMessage("user", "hi"),
            ChatMessage("assistant", "I couldn't read that reply.", category = ERROR_MESSAGE_CATEGORY),
            ChatMessage("system", "A system notice"),
            ChatMessage("assistant", "Another notice", category = "system"),
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

    @Test
    fun oversizedLatestMessageIsTruncatedInsteadOfDropped() {
        val oversized = ChatMessage("user", "latest " + "x".repeat(MAX_PROMPT_HISTORY_CHARACTERS))

        val prompt = messagesForPrompt(listOf(ChatMessage("user", "older"), oversized))

        assertEquals(1, prompt.size)
        assertTrue(prompt.single().content.startsWith("latest "))
        assertTrue(prompt.single().content.endsWith("[truncated]"))
        assertTrue(prompt.sumOf { it.content.length } <= MAX_PROMPT_HISTORY_CHARACTERS)
    }
}
