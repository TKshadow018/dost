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
        // 18:00 is after the default 09:00–17:30 busy window. Pin the friend's zone so
        // the JVM default (UTC on CI, Asia/Dhaka on a local machine) cannot move that instant inside the window.
        val zone = ZoneId.of("Asia/Dhaka")
        val friend = Friend(id = "work", name = "B", age = "30", gender = "male", timeZoneId = zone.id)
        val now = ZonedDateTime.of(2025, 1, 2, 18, 0, 0, 0, zone)

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

    @Test
    fun generatedProfileReplacesDefaultsBeforeChatCanStart() {
        val friend = Friend(id = "new", name = "Rafi", age = "24", gender = "male", profilePending = true)
        assertTrue(friend.profilePending)
        assertEquals("Friendly and curious", friend.personality)

        val ready = friend.withGeneratedProfile(
            FriendProfile(
                personality = "Calm and witty",
                interests = "Tea and cricket",
                family = "Parents in Khulna",
                familyMembers = "Mother, father",
                familyActivities = "Friday lunch",
                financialCondition = "Modest salary",
                address = "A fictional lane in Dhaka",
                height = "170 cm",
                weight = "65 kg",
                facialFeatures = "Short hair",
                bodyFeatures = "Average build",
                conversationStyle = "Warm and brief"
            )
        )

        assertFalse(ready.profilePending)
        assertEquals("Calm and witty", ready.personality)
        assertEquals("Tea and cricket", ready.interests)
        assertEquals("Warm and brief", ready.conversationStyle)
        assertEquals("Rafi", ready.name)
        assertEquals("24", ready.age)
    }

    @Test
    fun profileFailureKeepsLocalizedMessageAndTruncatesDetail() {
        val body = "x".repeat(200)
        val failure = profileFailureText(AppLanguage.BANGLA, "  $body\nmore  ")
        assertEquals("প্রোফাইল তৈরি করা যায়নি", failure.message)
        assertEquals(120, failure.detail?.length)
        assertEquals("x".repeat(120), failure.detail)
        assertEquals(null, profileFailureText(AppLanguage.HINDI, "  ").detail)
        assertEquals("Couldn't create their profile", profileFailureText(AppLanguage.ENGLISH, null).message)
    }
}