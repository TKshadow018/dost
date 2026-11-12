package com.snigtus.dost

import org.junit.Assert.assertEquals
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
}
