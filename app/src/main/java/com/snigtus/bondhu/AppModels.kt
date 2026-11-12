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
    ,val catalogId: String = ""
)

data class SystemFriendListing(
    val id: String,
    val name: String,
    val age: String,
    val gender: String,
    val personality: String,
    val interests: String,
    val memories: String,
    val conversationStyle: String,
    val family: String,
    val familyMembers: String,
    val familyActivities: String,
    val financialCondition: String,
    val address: String,
    val height: String,
    val weight: String,
    val facialFeatures: String,
    val bodyFeatures: String,
    val busyStartHour: Int,
    val busyDurationHours: Int,
    val busyReason: String,
    val timeZoneId: String,
    val photoUrl: String
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

/**
 * Youngest self-reported age that may create an account.
 * Under 13 is refused (COPPA / Play Families). Ages 13–17 may use the app with romance locked.
 */
const val MINIMUM_ACCOUNT_AGE = 13

/** Love meter and the romantic prompt ladder require both the user and the friend to be at least this age. */
const val ROMANCE_MINIMUM_AGE = 18

const val MAX_PROFILE_AGE = 120

enum class AccountAgeResult { VALID, EMPTY, NOT_A_WHOLE_NUMBER, UNDER_MINIMUM, ABOVE_MAXIMUM }

fun accountAgeResult(age: String): AccountAgeResult {
    val years = profileAgeOrNull(age) ?: return if (age.isBlank()) AccountAgeResult.EMPTY else classifyUnusableAge(age)
    if (years < MINIMUM_ACCOUNT_AGE) return AccountAgeResult.UNDER_MINIMUM
    return AccountAgeResult.VALID
}

/** A friend may be under 18. The age still has to be a whole number from 1 to [MAX_PROFILE_AGE]. */
fun friendAgeResult(age: String): AccountAgeResult {
    profileAgeOrNull(age) ?: return if (age.isBlank()) AccountAgeResult.EMPTY else classifyUnusableAge(age)
    return AccountAgeResult.VALID
}

fun profileAgeOrNull(age: String): Int? {
    val years = age.trim().toIntOrNull() ?: return null
    return years.takeIf { it in 1..MAX_PROFILE_AGE }
}

/** Missing or unparseable ages are treated as under the romance minimum so the love meter stays off. */
fun isUnderRomanceAge(age: String): Boolean {
    val years = profileAgeOrNull(age) ?: return true
    return years < ROMANCE_MINIMUM_AGE
}

fun romanceAllowed(userAge: String, friendAge: String): Boolean =
    !isUnderRomanceAge(userAge) && !isUnderRomanceAge(friendAge)

fun Friend.withLoveMeterGated(userAge: String): Friend =
    if (romanceAllowed(userAge, age)) this else copy(loveScore = 0)

fun nextLoveScore(currentLove: Int, loveImpact: Int, friendshipScore: Int, userAge: String, friendAge: String): Int {
    if (!romanceAllowed(userAge, friendAge) || friendshipScore <= 70) return 0
    return (currentLove + loveImpact.coerceIn(-1, 2)).coerceIn(0, 100)
}

fun applyLoveImpactGate(reply: AiReply, userAge: String, friendAge: String): AiReply =
    if (romanceAllowed(userAge, friendAge)) reply else reply.copy(loveImpact = 0)

fun relationshipStatePrompt(friendshipScore: Int, loveScore: Int, userAge: String, friendAge: String): String {
    val friendship = friendshipScore.coerceIn(0, 100)
    val friendshipText = "Relationship state: friendship $friendship/100 (${friendshipLevel(friendship)})."
    if (!romanceAllowed(userAge, friendAge)) {
        return "$friendshipText Love meter: off. Do not mention, score, or develop romance, because the user or the friend is under 18 or an age is missing."
    }
    val loveText = if (friendship > 70) {
        val love = loveScore.coerceIn(0, 100)
        "$love/100 (${loveLevel(love)})"
    } else {
        "locked until friendship is above 70"
    }
    return "$friendshipText Love meter: $loveText."
}

fun relationshipImpactPrompt(userAge: String, friendAge: String): String = if (romanceAllowed(userAge, friendAge)) {
    "Rate the latest conversation's relationship impact. friendship_impact must be an integer from -1 to 2. love_impact must be an integer from -1 to 2 only when friendship is above 70 and the conversation is clearly flirty or romantic; otherwise use 0. Apply friendship impact to warmth, trust, respectful openness, and shared conversation. A private question from a complete stranger should be met with a gentle boundary such as \"Whoa, we just met\" rather than a direct answer, and should not earn positive points. Do not unlock private or intimate talk early. Personal talk becomes more open around friendship 41+, private talk around 61+, and romantic/intimate-but-non-explicit talk only when friendship is above 70 and love is above 30. Never generate explicit sexual content."
} else {
    "Rate the latest conversation's relationship impact. friendship_impact must be an integer from -1 to 2. love_impact must be 0. Do not request, score, or role-play romance, flirting, crushes, or intimacy. The love meter is off because the user or the friend is under 18, or an age is missing or not a whole number. Keep the conversation friendly and age-appropriate. Never generate sexual or romantic content. Apply friendship impact only to warmth, trust, respectful openness, and shared conversation. A private question from a complete stranger should be met with a gentle boundary such as \"Whoa, we just met\" rather than a direct answer, and should not earn positive points. Do not unlock private or intimate talk early. Personal talk becomes more open around friendship 41+ and private talk around 61+. Never generate explicit sexual content."
}

/** Extra profile-generation rule. Empty for adults so their prompt stays unchanged. */
fun profileRomanceClause(age: String): String = if (isUnderRomanceAge(age)) {
    " The character is under 18, or their age is not a valid whole number. Do not invent romance, flirting, crushes, or sexual traits."
} else {
    ""
}

private fun classifyUnusableAge(age: String): AccountAgeResult {
    val years = age.trim().toIntOrNull() ?: return AccountAgeResult.NOT_A_WHOLE_NUMBER
    return if (years > MAX_PROFILE_AGE) AccountAgeResult.ABOVE_MAXIMUM else AccountAgeResult.NOT_A_WHOLE_NUMBER
}

fun limitToSingleQuestion(text: String): String {
    val questionMarks = charArrayOf('?', '\uFF1F', '\u061F')
    val firstQuestion = text.indexOfAny(questionMarks)
    if (firstQuestion < 0) return text
    val secondQuestion = text.indexOfAny(questionMarks, firstQuestion + 1)
    return if (secondQuestion < 0) text else text.substring(0, firstQuestion + 1).trimEnd()
}