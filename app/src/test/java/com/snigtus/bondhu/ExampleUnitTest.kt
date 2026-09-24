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
    fun busyScheduleReturnsEndTimeWhenCurrentlyBusy() {
        val friend = Friend(id = "school", name = "A", age = "15", gender = "female")
        val now = ZonedDateTime.of(2025, 1, 2, 10, 0, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertEquals(now.withHour(14), friend.busyUntil(now))
    }

    @Test
    fun busyScheduleReturnsNullOutsideBusyHours() {
        val friend = Friend(id = "work", name = "B", age = "30", gender = "male")
        val now = ZonedDateTime.of(2025, 1, 2, 18, 0, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertNull(friend.busyUntil(now))
    }

    @Test
    fun busyScheduleHandlesOvernightWindow() {
        val friend = Friend(id = "night", name = "C", age = "30", gender = "male", busyStartHour = 22, busyDurationHours = 6)
        val now = ZonedDateTime.of(2025, 1, 3, 1, 0, 0, 0, ZoneId.of("Asia/Dhaka"))

        assertEquals(now.withHour(4), friend.busyUntil(now))
    }
}