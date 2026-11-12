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

data class UserMemory(val category: String, val fact: String, val replaces: String = "")

const val MAX_STORED_USER_MEMORIES = 1000
const val MAX_INJECTED_USER_MEMORIES = 200

fun displayMemoryCategory(category: String): String {
    val cleaned = category.trim().replace('_', ' ').replace(Regex("\\s+"), " ").lowercase()
    if (cleaned.isEmpty()) return "Other"
    return cleaned.replaceFirstChar { it.titlecase() }
}

fun memoryCategoryLabel(category: String, language: AppLanguage): String {
    val key = category.trim().lowercase().replace(' ', '_')
    val translated = when (language) {
        AppLanguage.BANGLA -> MEMORY_CATEGORY_BANGLA[key]
        AppLanguage.HINDI -> MEMORY_CATEGORY_HINDI[key]
        AppLanguage.ENGLISH -> null
    }
    return translated ?: displayMemoryCategory(key)
}

fun sameUserMemory(left: UserMemory, right: UserMemory): Boolean =
    left.category.equals(right.category, ignoreCase = true) && left.fact.equals(right.fact, ignoreCase = true)

/** True when [replaces] names an existing fact closely enough to overwrite it. */
fun isCorrectionTarget(storedFact: String, replaces: String): Boolean {
    val stored = storedFact.trim()
    val target = replaces.trim()
    if (stored.isEmpty() || target.isEmpty()) return false
    if (stored.equals(target, ignoreCase = true)) return true
    val shorter = minOf(stored.length, target.length)
    val longer = maxOf(stored.length, target.length)
    if (shorter < 12 || shorter * 100 / longer < 70) return false
    return stored.contains(target, ignoreCase = true) || target.contains(stored, ignoreCase = true)
}

fun correctionIndex(memories: List<UserMemory>, incoming: UserMemory): Int {
    val target = incoming.replaces.trim()
    if (target.isBlank()) return -1
    val exact = memories.indexOfLast { it.fact.equals(target, ignoreCase = true) }
    if (exact >= 0) return exact
    return memories.indexOfLast { isCorrectionTarget(it.fact, target) }
}

fun mergeUserMemories(
    existing: List<UserMemory>,
    incoming: List<UserMemory>,
    limit: Int = MAX_STORED_USER_MEMORIES
): List<UserMemory> {
    val memories = existing.mapNotNull { normalizeStoredMemory(it) }.toMutableList()
    incoming.forEach { raw ->
        val memory = normalizeIncomingMemory(raw) ?: return@forEach
        val index = correctionIndex(memories, memory)
        if (index >= 0) {
            memories[index] = memory.copy(replaces = "")
            dropDuplicateMemories(memories, index)
            return@forEach
        }
        if (memories.none { sameUserMemory(it, memory) }) memories.add(memory.copy(replaces = ""))
    }
    return memories.takeLast(limit)
}

fun replaceUserMemory(existing: List<UserMemory>, original: UserMemory, updated: UserMemory): List<UserMemory> {
    val index = existing.indexOfFirst { sameUserMemory(it, original) }
    if (index < 0) return existing
    val fact = updated.fact.trim()
    if (fact.isBlank()) return existing
    val next = existing.toMutableList()
    next[index] = UserMemory(updated.category.trim().ifBlank { original.category.ifBlank { "other" } }, fact)
    dropDuplicateMemories(next, index)
    return next
}

fun withoutUserMemory(existing: List<UserMemory>, target: UserMemory): List<UserMemory> {
    val index = existing.indexOfFirst { sameUserMemory(it, target) }
    if (index < 0) return existing
    return existing.filterIndexed { itemIndex, _ -> itemIndex != index }
}

private fun normalizeStoredMemory(memory: UserMemory): UserMemory? {
    val fact = memory.fact.trim()
    if (fact.isBlank()) return null
    return UserMemory(memory.category.trim().ifBlank { "other" }, fact)
}

private fun normalizeIncomingMemory(memory: UserMemory): UserMemory? {
    val fact = memory.fact.trim()
    if (fact.isBlank()) return null
    return UserMemory(memory.category.trim().ifBlank { "other" }, fact, memory.replaces.trim())
}

private fun dropDuplicateMemories(memories: MutableList<UserMemory>, keepIndex: Int) {
    val kept = memories[keepIndex]
    for (index in memories.indices.reversed()) {
        if (index != keepIndex && sameUserMemory(memories[index], kept)) memories.removeAt(index)
    }
}

private val MEMORY_CATEGORY_BANGLA = mapOf(
    "family" to "পরিবার",
    "relatives" to "আত্মীয়",
    "address" to "ঠিকানা",
    "contact" to "যোগাযোগ",
    "profile" to "প্রোফাইল",
    "preferences" to "পছন্দ",
    "interests" to "আগ্রহ",
    "goals" to "লক্ষ্য",
    "routines" to "রুটিন",
    "important_dates" to "গুরুত্বপূর্ণ তারিখ",
    "relationships" to "সম্পর্ক",
    "health" to "স্বাস্থ্য",
    "work_or_study" to "কাজ বা পড়াশোনা",
    "education" to "শিক্ষা",
    "finances" to "আর্থিক",
    "travel" to "ভ্রমণ",
    "other" to "অন্যান্য"
)

private val MEMORY_CATEGORY_HINDI = mapOf(
    "family" to "परिवार",
    "relatives" to "रिश्तेदार",
    "address" to "पता",
    "contact" to "संपर्क",
    "profile" to "प्रोफ़ाइल",
    "preferences" to "पसंद",
    "interests" to "रुचियाँ",
    "goals" to "लक्ष्य",
    "routines" to "दिनचर्या",
    "important_dates" to "ज़रूरी तारीखें",
    "relationships" to "रिश्ते",
    "health" to "सेहत",
    "work_or_study" to "काम या पढ़ाई",
    "education" to "शिक्षा",
    "finances" to "पैसा",
    "travel" to "यात्रा",
    "other" to "अन्य"
)

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