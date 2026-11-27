package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimingTest {

    @Test
    fun followUpDelayIsThirtyMinutes() {
        assertEquals(30 * 60_000L, ConversationManager.DEFAULT_FOLLOW_UP_MILLIS)
    }

    @Test
    fun sessionInactivityIsSixtyMinutes() {
        assertEquals(60L * 60L * 1000L, ConversationManager.SESSION_INACTIVITY_MILLIS)
    }

    @Test
    fun defaultFollowUpSchedulesOnlyWhenTheReplyHasNoQuestion() {
        assertTrue(ConversationManager.shouldScheduleDefaultFollowUp("Glad you made it home.", ""))
        assertFalse(ConversationManager.shouldScheduleDefaultFollowUp("How are you?", ""))
        assertFalse(ConversationManager.shouldScheduleDefaultFollowUp("Hello", "What did you eat?"))
        assertFalse(ConversationManager.shouldScheduleDefaultFollowUp("Really?", " "))
    }

    @Test
    fun staleFollowUpIsRejectedAfterANewerUserMessage() {
        val latestMessage = ChatMessage("user", "A newer message", timestampMillis = 200L)

        assertFalse(isScheduledFollowUpCurrent(100L, latestMessage))
        assertFalse(isScheduledFollowUpCurrent(100L, null))
        assertTrue(isScheduledFollowUpCurrent(200L, latestMessage))
    }

    @Test
    fun pausedFollowUpRestoresOnlyForItsOriginalCurrentConversation() {
        val followUp = ProactiveMessage(
            id = "follow-up",
            friendId = "friend",
            trigger = ProactiveTrigger.FOLLOW_UP,
            sessionId = "session",
            dueAtMillis = 500L,
            reason = "conversation_follow_up",
            idempotencyKey = "stable-key",
            expectedUserMessageTimestamp = 200L,
            state = ProactiveDeliveryState.CANCELLED
        )
        val latestUserMessage = ChatMessage("user", "Still current", sessionId = "session", timestampMillis = 200L)

        assertTrue(canRestoreScheduledFollowUp(followUp, "session", latestUserMessage))
        assertFalse(canRestoreScheduledFollowUp(followUp, "another-session", latestUserMessage))
        assertFalse(canRestoreScheduledFollowUp(followUp, "session", latestUserMessage.copy(timestampMillis = 300L)))
        assertFalse(
            canRestoreScheduledFollowUp(
                followUp.copy(state = ProactiveDeliveryState.COMPLETED),
                "session",
                latestUserMessage
            )
        )
    }
}
