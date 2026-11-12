package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimingTest {

    @Test
    fun followUpDelayIsOneMinute() {
        assertEquals(60_000L, ConversationManager.DEFAULT_FOLLOW_UP_MILLIS)
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
}
