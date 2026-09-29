package com.snigtus.dost

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AppScreen { SPLASH, REGISTER, HOME, CHAT, KNOWLEDGE }
enum class AppLanguage { ENGLISH, BANGLA, HINDI }

data class Friend(
    val id: String,
    val name: String,
    val age: String,
    val gender: String,
    val personality: String = "Friendly and curious",
    val interests: String = "",
    val memories: String = "",
    val conversationStyle: String = "Warm, natural, and concise"
    ,val family: String = ""
    ,val familyMembers: String = ""
    ,val familyActivities: String = ""
    ,val financialCondition: String = ""
    ,val address: String = ""
    ,val height: String = ""
    ,val weight: String = ""
    ,val facialFeatures: String = ""
    ,val bodyFeatures: String = ""
    ,val friendshipScore: Int = 0
    ,val loveScore: Int = 0
    ,val mood: String = ""
    ,val photoUri: String = ""
    ,val busyStartHour: Int = if ((age.toIntOrNull() ?: 18) < 18) 8 else 9
    ,val busyDurationHours: Int = if ((age.toIntOrNull() ?: 18) < 18) 6 else 8
    ,val busyReason: String = if ((age.toIntOrNull() ?: 18) < 18) "School" else "Work"
    ,val timeZoneId: String = ZoneId.systemDefault().id
)

fun validZoneId(timeZoneId: String): ZoneId = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())

fun Friend.replyAvailableAt(now: ZonedDateTime): ZonedDateTime? {
    val friendNow = now.withZoneSameInstant(validZoneId(timeZoneId))
    val startTime = LocalTime.of(busyStartHour.coerceIn(0, 23), 0)
    val duration = busyDurationHours.coerceIn(4, 8).toLong()
    return listOf(friendNow.toLocalDate().minusDays(1), friendNow.toLocalDate())
        .firstNotNullOfOrNull { date ->
            val start = date.atTime(startTime).atZone(friendNow.zone)
            val release = start.plusHours(duration).plusMinutes(30)
            release.takeIf { !friendNow.isBefore(start) && friendNow.isBefore(it) }
        }
}

data class FriendProfile(
    val personality: String,
    val interests: String,
    val family: String,
    val familyMembers: String,
    val familyActivities: String,
    val financialCondition: String,
    val address: String,
    val height: String,
    val weight: String,
    val facialFeatures: String,
    val bodyFeatures: String,
    val conversationStyle: String
)

data class ChatMessage(
    val role: String,
    val content: String,
    val category: String = "conversation",
    val sessionId: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val messageId: String = java.util.UUID.randomUUID().toString(),
    val modelId: String = "",
    val imagePath: String = ""
)

data class UserMemory(val category: String, val fact: String)

data class SessionSummary(val sessionId: String, val startedAtMillis: Long, val endedAtMillis: Long, val summary: String)

data class AiReply(
    val text: String,
    val memories: List<UserMemory> = emptyList(),
    val followUpQuestion: String = "",
    val followUpDelayMinutes: Int? = null,
    val friendshipImpact: Int = 0,
    val loveImpact: Int = 0,
    val serverMessageId: String? = null
)

fun friendshipLevel(score: Int): String = when (score.coerceIn(0, 100)) {
    in 0..10 -> "Complete stranger"
    in 11..20 -> "Stranger"
    in 21..30 -> "Acquaintance"
    in 31..40 -> "Casual friend"
    in 41..50 -> "Friend"
    in 51..60 -> "Good friend"
    in 61..70 -> "Close friend"
    in 71..80 -> "Trusted friend"
    in 81..90 -> "Very close friend"
    else -> "Best friend"
}

fun loveLevel(score: Int): String = when (score.coerceIn(0, 100)) {
    in 0..10 -> "A spark is not there yet"
    in 11..20 -> "Curious feelings"
    in 21..30 -> "A gentle crush"
    in 31..40 -> "Growing affection"
    in 41..50 -> "Romantic interest"
    in 51..60 -> "Strong affection"
    in 61..70 -> "Deep feelings"
    in 71..80 -> "Falling in love"
    in 81..90 -> "Deeply in love"
    else -> "Beloved"
}

fun limitToSingleQuestion(text: String): String {
    val questionMarks = charArrayOf('?', '\uFF1F', '\u061F')
    val firstQuestion = text.indexOfAny(questionMarks)
    if (firstQuestion < 0) return text
    val secondQuestion = text.indexOfAny(questionMarks, firstQuestion + 1)
    return if (secondQuestion < 0) text else text.substring(0, firstQuestion + 1).trimEnd()
}