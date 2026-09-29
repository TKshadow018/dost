package com.snigtus.dost

import org.junit.Test

import org.junit.Assert.*
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun busyScheduleUsesFriendTimezoneAndThirtyMinuteCooldown() {
        val friend = Friend(id = "school", name = "A", age = "15", gender = "female", timeZoneId = "Asia/Dhaka")
        val now = ZonedDateTime.of(2025, 1, 2, 4, 0, 0, 0, ZoneId.of("UTC"))

        assertEquals(ZonedDateTime.of(2025, 1, 2, 14, 30, 0, 0, ZoneId.of("Asia/Dhaka")), friend.replyAvailableAt(now))
    }

    @Test
    fun busyScheduleReturnsNullOutsideBusyHours() {
        val friend = Friend(id = "work", name = "B", age = "30", gender = "male")
        val now = ZonedDateTime.of(2025, 1, 2, 18, 0, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertNull(friend.replyAvailableAt(now))
    }

    @Test
    fun busyScheduleHandlesOvernightWindow() {
        val now = ZonedDateTime.of(2025, 1, 3, 1, 0, 0, 0, ZoneId.of("Asia/Dhaka"))
        val friend = Friend(id = "night", name = "C", age = "30", gender = "male", busyStartHour = 22, busyDurationHours = 6, timeZoneId = now.zone.id)

        assertEquals(now.withHour(4).plusMinutes(30), friend.replyAvailableAt(now))
    }

    @Test
    fun responseContainsAtMostOneQuestion() {
        assertEquals("How are you?", limitToSingleQuestion("How are you? What did you do today?"))
        assertEquals("What about tomorrow？", limitToSingleQuestion("What about tomorrow？ هل أنت بخير؟"))
    }

    @Test
    fun responseWithoutQuestionRemainsUnchanged() {
        assertEquals("Sounds good.", limitToSingleQuestion("Sounds good."))
    }
}