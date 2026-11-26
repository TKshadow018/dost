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
    fun generatedProfileKeepsAnExplicitCustomPersonality() {
        val friend = Friend(
            id = "custom",
            name = "Mina",
            age = "24",
            gender = "female",
            personality = "Quiet and thoughtful",
            profilePending = true
        )
        val ready = friend.withGeneratedProfile(
            FriendProfile(
                personality = "Bold and outgoing",
                interests = "Painting",
                family = "",
                familyMembers = "",
                familyActivities = "",
                financialCondition = "",
                address = "",
                height = "",
                weight = "",
                facialFeatures = "",
                bodyFeatures = "",
                conversationStyle = "Warm"
            )
        )

        assertEquals("Quiet and thoughtful", ready.personality)
    }

    @Test
    fun busyScheduleIsAvailableAtTheEndOfItsReleaseBuffer() {
        val zone = ZoneId.of("Asia/Dhaka")
        val friend = Friend(id = "work", name = "B", age = "30", gender = "male", timeZoneId = zone.id)
        val releaseTime = ZonedDateTime.of(2025, 1, 2, 17, 30, 0, 0, zone)

        assertNull(friend.replyAvailableAt(releaseTime))
    }

    @Test
    fun routineBlocksCanUseDifferentWeekdayAndWeekendHours() {
        val zone = ZoneId.of("Asia/Dhaka")
        val friend = Friend(
            id = "routine",
            name = "A",
            age = "30",
            gender = "female",
            timeZoneId = zone.id,
            routineBlocks = listOf(
                RoutineBlock("work", "Work", 9 * 60, 8 * 60, setOf(1, 2, 3, 4, 5)),
                RoutineBlock("weekend", "Family time", 12 * 60, 3 * 60, setOf(6, 7))
            )
        )

        assertEquals(
            ZonedDateTime.of(2025, 1, 6, 17, 30, 0, 0, zone),
            friend.replyAvailableAt(ZonedDateTime.of(2025, 1, 6, 10, 0, 0, 0, zone))
        )
        assertEquals(
            ZonedDateTime.of(2025, 1, 11, 15, 30, 0, 0, zone),
            friend.replyAvailableAt(ZonedDateTime.of(2025, 1, 11, 13, 0, 0, 0, zone))
        )
        assertNull(friend.replyAvailableAt(ZonedDateTime.of(2025, 1, 11, 10, 0, 0, 0, zone)))
    }

    @Test
    fun routineBlockSupportsOvernightBusyPeriodAndCustomReplyDelay() {
        val zone = ZoneId.of("Asia/Dhaka")
        val friend = Friend(
            id = "night",
            name = "N",
            age = "30",
            gender = "male",
            timeZoneId = zone.id,
            routineBlocks = listOf(RoutineBlock("sleep", "Sleep", 22 * 60, 8 * 60, setOf(5))),
            busyReplyDelayMinutes = 15
        )

        assertEquals(
            ZonedDateTime.of(2025, 1, 4, 6, 15, 0, 0, zone),
            friend.replyAvailableAt(ZonedDateTime.of(2025, 1, 4, 2, 0, 0, 0, zone))
        )
    }

    @Test
    fun routineUsesFriendLocalWallTimeAcrossDaylightSavingTransition() {
        val zone = ZoneId.of("America/New_York")
        val friend = Friend(
            id = "dst",
            name = "D",
            age = "30",
            gender = "female",
            timeZoneId = zone.id,
            routineBlocks = listOf(RoutineBlock("morning", "Routine", 90, 120, setOf(7))),
            busyReplyDelayMinutes = 30
        )
        val now = ZonedDateTime.of(2025, 3, 9, 1, 45, 0, 0, zone)

        assertEquals(ZonedDateTime.of(2025, 3, 9, 4, 0, 0, 0, zone), friend.replyAvailableAt(now))
    }

    @Test
    fun morningGreetingSlotsCanBeStaggered() {
        val zone = ZoneId.of("Asia/Dhaka")
        val now = ZonedDateTime.of(2025, 1, 2, 7, 0, 0, 0, zone)
        val friend = Friend(id = "morning", name = "M", age = "30", gender = "female", timeZoneId = zone.id)

        assertEquals(ZonedDateTime.of(2025, 1, 2, 8, 0, 0, 0, zone), nextMorningGreetingAt(now, friend))
        assertEquals(ZonedDateTime.of(2025, 1, 2, 8, 15, 0, 0, zone), nextMorningGreetingAt(now, friend, 15))
    }

    @Test
    fun quietHoursReleaseAtTheConfiguredEnd() {
        val zone = ZoneId.of("Asia/Dhaka")
        val quietTime = ZonedDateTime.of(2025, 1, 2, 23, 0, 0, 0, zone)
        val quietEnd = ZonedDateTime.of(2025, 1, 3, 8, 0, 0, 0, zone)

        assertEquals(quietEnd, nextTimeAfterQuietHours(quietTime, 22 * 60, 8 * 60))
        assertEquals(quietEnd, nextTimeAfterQuietHours(quietEnd, 22 * 60, 8 * 60))
    }

    @Test
    fun proactiveSlotsAreStaggeredAndTerminalDeliveryStatesStayTerminal() {
        val firstSlot = 1_000_000L
        val secondSlot = firstSlot + GLOBAL_PROACTIVE_SPACING_MILLIS

        assertEquals(secondSlot, staggerProactiveTime(firstSlot, listOf(firstSlot)))
        assertEquals(
            firstSlot + 2 * GLOBAL_PROACTIVE_SPACING_MILLIS,
            staggerProactiveTime(firstSlot, listOf(firstSlot, secondSlot))
        )
        assertEquals(
            ProactiveDeliveryState.CANCELLED,
            proactiveStateAfterFinish(ProactiveDeliveryState.CANCELLED, ProactiveDeliveryState.SCHEDULED)
        )
        assertEquals(
            ProactiveDeliveryState.COMPLETED,
            proactiveStateAfterFinish(ProactiveDeliveryState.COMPLETED, ProactiveDeliveryState.CANCELLED)
        )
        assertEquals(stableIdempotencyKey("friend:day"), stableIdempotencyKey("friend:day"))
        assertNotEquals(stableIdempotencyKey("friend:day"), stableIdempotencyKey("friend:next-day"))
    }

    @Test
    fun explicitReminderRequestsRequireConfirmationForMissingTime() {
        val zone = ZoneId.of("Asia/Dhaka")
        val now = ZonedDateTime.of(2025, 1, 2, 12, 0, 0, 0, zone)
        val reminder = parseExplicitReminderRequest(
            "Ask me how the interview went tomorrow",
            now.toInstant().toEpochMilli(),
            zone.id
        )

        assertNotNull(reminder)
        assertEquals("how the interview went", reminder?.description)
        assertEquals("uncertain", reminder?.confidence)
        assertEquals(
            ZonedDateTime.of(2025, 1, 3, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
            reminder?.dueAtMillis
        )
        assertNull(parseExplicitReminderRequest("I have an interview tomorrow", now.toInstant().toEpochMilli(), zone.id))
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
