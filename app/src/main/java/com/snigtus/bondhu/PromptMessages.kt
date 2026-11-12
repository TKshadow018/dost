package com.snigtus.dost

internal const val ERROR_MESSAGE_CATEGORY = "error"
internal const val MAX_PROMPT_HISTORY_CHARACTERS = 25_000

/**
 * History that may be sent to the model or folded into a session summary.
 * Error bubbles stay in the chat UI and are left out of both. Keep the newest
 * complete messages that fit within the prompt history budget.
 */
internal fun messagesForPrompt(messages: List<ChatMessage>): List<ChatMessage> {
    val included = mutableListOf<ChatMessage>()
    var characterCount = 0
    for (message in messages.asReversed()) {
        if (message.category == ERROR_MESSAGE_CATEGORY) continue
        if (characterCount + message.content.length > MAX_PROMPT_HISTORY_CHARACTERS) break
        included.add(message)
        characterCount += message.content.length
    }
    return included.asReversed()
}
