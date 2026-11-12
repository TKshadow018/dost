package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundSendQueueTest {

    @Test
    fun secondSendIsQueuedAndOmittedFromTheInFlightHistory() {
        val queue = OutboundSendQueue()
        val first = message("m1", "session-a", "Hello")
        val second = message("m2", "session-a", "Still here")
        assertTrue(queue.enqueue("friend", second.messageId, second.sessionId, AppLanguage.ENGLISH))

        val inFlight = queue.visibleHistory("friend", listOf(first, second))
        assertEquals(listOf("m1"), inFlight.map { it.messageId })
    }

    @Test
    fun promptExcludesErrorsAndPendingMessagesUntilQueueDrains() {
        val queue = OutboundSendQueue()
        val first = message("m1", "session-a", "Hello")
        val error = message("error", "session-a", "Unreadable reply").copy(role = "assistant", category = ERROR_MESSAGE_CATEGORY)
        val second = message("m2", "session-a", "Still here")
        val transcript = listOf(first, error, second)
        queue.enqueue("friend", second.messageId, second.sessionId, AppLanguage.ENGLISH)

        assertEquals(listOf(first), messagesForPrompt(queue.visibleHistory("friend", transcript)))
        queue.takeReady("friend")
        assertEquals(listOf(first, second), messagesForPrompt(queue.visibleHistory("friend", transcript)))
    }

    @Test
    fun queuedMessagesArePersistedInArrivalOrderAndSentOnce() {
        val queue = OutboundSendQueue()
        assertTrue(queue.enqueue("friend", "m2", "session-a", AppLanguage.ENGLISH))
        assertTrue(queue.enqueue("friend", "m3", "session-a", AppLanguage.BANGLA))
        assertFalse(queue.enqueue("friend", "m2", "session-a", AppLanguage.ENGLISH))

        val transcript = listOf(
            message("m1", "session-a", "Hello"),
            message("m2", "session-a", "Still here"),
            message("m3", "session-a", "One more")
        )
        assertEquals(listOf("m2", "m3"), queue.takeReady("friend").map { it.messageId })
        assertEquals(listOf("m1", "m2", "m3"), queue.visibleHistory("friend", transcript).map { it.messageId })
        assertTrue(queue.takeReady("friend").isEmpty())
    }

    @Test
    fun differentSessionsStayQueuedUntilTheirOwnFollowUp() {
        val queue = OutboundSendQueue()
        queue.enqueue("friend", "early", "session-a", AppLanguage.ENGLISH)
        queue.enqueue("friend", "late", "session-b", AppLanguage.HINDI)

        assertEquals(listOf("late"), queue.takeReady("friend").map { it.messageId })
        val sessionA = queue.visibleHistory("friend", listOf(message("early", "session-a", "first")))
        assertTrue(sessionA.isEmpty())
        assertEquals("session-a", queue.takeReady("friend").single().sessionId)
    }

    @Test
    fun friendsDoNotShareAQueue() {
        val queue = OutboundSendQueue()
        queue.enqueue("a", "m-a", "session", AppLanguage.ENGLISH)
        queue.enqueue("b", "m-b", "session", AppLanguage.ENGLISH)
        assertEquals("m-a", queue.takeReady("a").single().messageId)
        assertEquals("m-b", queue.takeReady("b").single().messageId)
    }

    private fun message(id: String, sessionId: String, content: String) = ChatMessage(
        role = "user",
        content = content,
        sessionId = sessionId,
        messageId = id,
        timestampMillis = 1L
    )
}
