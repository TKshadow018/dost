package com.snigtus.dost

/**
 * Messages accepted for one friend while a reply job is already running.
 * Pending ids stay out of the in-flight model request. Draining a session
 * removes them exactly once so the follow-up request includes them a single time.
 */
internal class OutboundSendQueue {
    data class Pending(
        val messageId: String,
        val sessionId: String,
        val language: AppLanguage
    )

    private val pending = HashMap<String, ArrayDeque<Pending>>()

    @Synchronized
    fun enqueue(friendId: String, messageId: String, sessionId: String, language: AppLanguage): Boolean {
        if (messageId.isBlank() || sessionId.isBlank()) return false
        val queue = pending.getOrPut(friendId) { ArrayDeque() }
        if (queue.any { it.messageId == messageId }) return false
        queue.addLast(Pending(messageId, sessionId, language))
        return true
    }

    @Synchronized
    fun clear(friendId: String) {
        pending.remove(friendId)
    }

    /**
     * Takes every queued message that shares the newest message's session, in arrival order.
     * Messages for a different session stay queued for a later follow-up.
     */
    @Synchronized
    fun takeReady(friendId: String): List<Pending> {
        val queue = pending[friendId] ?: return emptyList()
        if (queue.isEmpty()) {
            pending.remove(friendId)
            return emptyList()
        }
        val sessionId = queue.last().sessionId
        val ready = ArrayList<Pending>(queue.size)
        val later = ArrayDeque<Pending>()
        queue.forEach { item ->
            if (item.sessionId == sessionId) ready.add(item) else later.addLast(item)
        }
        if (later.isEmpty()) pending.remove(friendId) else pending[friendId] = later
        return ready
    }

    @Synchronized
    fun visibleHistory(friendId: String, sessionMessages: List<ChatMessage>): List<ChatMessage> {
        val excluded = pending[friendId]?.map { it.messageId }?.toSet().orEmpty()
        if (excluded.isEmpty()) return sessionMessages
        return sessionMessages.filter { it.messageId !in excluded }
    }
}
