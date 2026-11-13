package com.snigtus.dost

/**
 * The morning-greeting instruction is not a user turn. The server archives user
 * and assistant messages from the chat request, so the instruction stays beside
 * the system prompt. The assistant line is stored on the session id the worker scheduled.
 */
internal object MorningGreeting {
    const val INSTRUCTION = "Say a warm good-morning greeting to me as if you just woke up and thought of me."

    data class Plan(
        val sessionId: String,
        val history: List<ChatMessage>,
        val instruction: String
    )

    fun plan(scheduledSessionId: String, existingMessages: List<ChatMessage>): Plan {
        val history = existingMessages.filter { it.sessionId == scheduledSessionId }
        return Plan(
            sessionId = scheduledSessionId,
            history = history,
            instruction = if (history.isEmpty()) INSTRUCTION else ""
        )
    }
}
