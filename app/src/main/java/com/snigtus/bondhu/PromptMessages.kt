package com.snigtus.dost

internal const val ERROR_MESSAGE_CATEGORY = "error"

/**
 * History that may be sent to the model or folded into a session summary.
 * Error bubbles stay in the chat UI and are left out of both.
 */
internal fun messagesForPrompt(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filterNot { it.category == ERROR_MESSAGE_CATEGORY }
