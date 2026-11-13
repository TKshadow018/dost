package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MorningGreetingTest {

    @Test
    fun seedIsNotArchivedAsAUserMessageAndUsesTheScheduledSession() {
        val openSession = ChatMessage("user", "still talking", sessionId = "open-session")
        val plan = MorningGreeting.plan("scheduled-session", listOf(openSession))

        assertEquals("scheduled-session", plan.sessionId)
        assertTrue(plan.history.isEmpty())
        assertEquals(MorningGreeting.INSTRUCTION, plan.instruction)
        assertTrue(plan.history.none { it.content == MorningGreeting.INSTRUCTION || it.role == "user" })
    }

    @Test
    fun anAlreadyStartedScheduledSessionDoesNotGetASecondSeed() {
        val existing = ChatMessage("user", "Already talking", sessionId = "scheduled-session")
        val plan = MorningGreeting.plan("scheduled-session", listOf(existing))

        assertEquals(listOf(existing), plan.history)
        assertEquals("", plan.instruction)
    }

    @Test
    fun adultGreetingStaysAtEightLocalBeforeBusyHours() {
        val friend = Friend(id = "a", name = "A", age = "30", gender = "male", timeZoneId = "Asia/Dhaka")
        val now = ZonedDateTime.of(2026, 3, 4, 7, 15, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertEquals(
            ZonedDateTime.of(2026, 3, 4, 8, 0, 0, 0, ZoneId.of("Asia/Dhaka")),
            nextMorningGreetingAt(now, friend)
        )
    }

    @Test
    fun greetingAfterEightLocalWaitsUntilTheNextMorning() {
        val friend = Friend(id = "a", name = "A", age = "30", gender = "male", timeZoneId = "Asia/Dhaka")
        val now = ZonedDateTime.of(2026, 3, 4, 9, 5, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertEquals(
            ZonedDateTime.of(2026, 3, 5, 8, 0, 0, 0, ZoneId.of("Asia/Dhaka")),
            nextMorningGreetingAt(now, friend)
        )
    }

    @Test
    fun schoolHoursPushTheEightOClockGreetingToTheEndOfTheBusyWindow() {
        val friend = Friend(id = "a", name = "A", age = "15", gender = "female", timeZoneId = "Asia/Dhaka")
        val now = ZonedDateTime.of(2026, 3, 4, 7, 0, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertEquals(
            ZonedDateTime.of(2026, 3, 4, 14, 30, 0, 0, ZoneId.of("Asia/Dhaka")),
            nextMorningGreetingAt(now, friend)
        )
    }
}
