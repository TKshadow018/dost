package com.snigtus.dost

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AppScreen { SPLASH, AUTH, REGISTER, HOME, FRIEND_PROFILE, SETTINGS, SYSTEM_FRIEND_PICKER, CHAT, KNOWLEDGE }
enum class AppLanguage { ENGLISH, BANGLA, HINDI }
enum class AuthMode { SIGN_IN, CREATE_ACCOUNT, FORGOT_PASSWORD, VERIFY_EMAIL }

sealed interface AuthRequestState {
    data object Idle : AuthRequestState
    data object Loading : AuthRequestState
    data class Error(val message: String) : AuthRequestState
    data class Success(val message: String) : AuthRequestState
}

data class AuthAccount(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean = false,
    val googleAccount: Boolean = false
) {
    val needsEmailVerification: Boolean
        get() = requiresEmailVerification(emailVerified, googleAccount)
}

fun requiresEmailVerification(emailVerified: Boolean, googleAccount: Boolean): Boolean =
    !emailVerified && !googleAccount

fun authenticationStartScreen(
    hasSignedInUser: Boolean,
    hasEligibleLocalProfile: Boolean,
    authenticationRequired: Boolean,
    signedInUserId: String? = null,
    localAccountId: String? = null
): AppScreen = when {
    hasSignedInUser && !hasEligibleLocalProfile -> AppScreen.REGISTER
    hasSignedInUser && !signedInUserId.isNullOrBlank() && signedInUserId == localAccountId -> AppScreen.HOME
    hasSignedInUser -> AppScreen.AUTH
    hasEligibleLocalProfile && !authenticationRequired && localAccountId.isNullOrBlank() -> AppScreen.HOME
    else -> AppScreen.AUTH
}

internal const val MAX_CUSTOM_FRIENDS = 3
internal const val MAX_SYSTEM_FRIENDS = 7
internal const val MAX_TOTAL_FRIENDS = MAX_CUSTOM_FRIENDS + MAX_SYSTEM_FRIENDS
internal const val GLOBAL_PROACTIVE_SPACING_MILLIS = 15 * 60_000L

internal fun stableIdempotencyKey(value: String): String =
    java.util.UUID.nameUUIDFromBytes(value.toByteArray(Charsets.UTF_8)).toString()

enum class BusyReplyBehavior { WAIT, IMMEDIATE }
enum class ProactiveTrigger { MORNING_GREETING, FOLLOW_UP, USER_REMINDER }
enum class ProactiveDeliveryState { SCHEDULED, SENDING, COMPLETED, CANCELLED }
enum class PlannedEventState { UPCOMING, COMPLETED, DISMISSED }

internal fun staggerProactiveTime(candidateMillis: Long, reservedDueTimesMillis: List<Long>): Long {
    var candidate = candidateMillis
    reservedDueTimesMillis.sorted().forEach { reserved ->
        if (kotlin.math.abs(reserved - candidate) < GLOBAL_PROACTIVE_SPACING_MILLIS) {
            candidate = reserved + GLOBAL_PROACTIVE_SPACING_MILLIS
        }
    }
    return candidate
}

internal fun proactiveStateAfterFinish(
    current: ProactiveDeliveryState,
    requested: ProactiveDeliveryState
): ProactiveDeliveryState {
    require(requested in setOf(
        ProactiveDeliveryState.COMPLETED,
        ProactiveDeliveryState.CANCELLED,
        ProactiveDeliveryState.SCHEDULED
    ))
    return if (current in setOf(ProactiveDeliveryState.COMPLETED, ProactiveDeliveryState.CANCELLED)) current else requested
}

internal fun shouldApplyRelationshipOutcome(outcomeSessionIds: Collection<String>, sessionId: String): Boolean =
    sessionId.isNotBlank() && sessionId !in outcomeSessionIds

internal fun shouldApplyRelationshipOutcome(
    outcomeSessionIds: Collection<String>,
    outcomeFingerprints: Collection<String>,
    sessionId: String,
    fingerprint: String?
): Boolean = shouldApplyRelationshipOutcome(outcomeSessionIds, sessionId)
    && (fingerprint == null || fingerprint !in outcomeFingerprints)

internal fun relationshipOutcomeFingerprint(userMessage: String): String? {
    val normalized = userMessage.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    return stableIdempotencyKey("relationship-turn:$normalized")
}

data class RoutineBlock(
    val id: String,
    val reason: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val daysOfWeek: Set<Int>
)

fun conversationStarters(friend: Friend, language: AppLanguage): List<String> {
    val interests = friend.interests
        .split(',', ';', '\n')
        .map { it.trim().trimEnd('.') }
        .filter { it.length >= 2 }
        .distinctBy { it.lowercase() }
        .take(3)
    if (interests.isEmpty()) {
        return when (language) {
            AppLanguage.BANGLA -> listOf("আজকের দিনের সেরা অংশ কী ছিল?", "সম্প্রতি কী শিখেছ?", "এখন কোন বিষয়টি নিয়ে ভাবছ?")
            AppLanguage.HINDI -> listOf("आज के दिन का सबसे अच्छा हिस्सा क्या था?", "हाल में क्या नया सीखा?", "इन दिनों किस बारे में सोच रहे हो?")
            AppLanguage.ENGLISH -> listOf("What was the best part of your day?", "What have you learned recently?", "What have you been thinking about lately?")
        }
    }
    return interests.map { interest ->
        when (language) {
            AppLanguage.BANGLA -> "$interest নিয়ে তোমার সবচেয়ে ভালো লাগার বিষয় কী?"
            AppLanguage.HINDI -> "$interest के बारे में तुम्हें सबसे ज़्यादा क्या पसंद है?"
            AppLanguage.ENGLISH -> "What do you enjoy most about $interest?"
        }
    }
}

data class ProactiveMessage(
    val id: String,
    val friendId: String,
    val trigger: ProactiveTrigger,
    val sessionId: String,
    val dueAtMillis: Long,
    val reason: String,
    val idempotencyKey: String,
    val expectedUserMessageTimestamp: Long = 0L,
    val eventId: String = "",
    val state: ProactiveDeliveryState = ProactiveDeliveryState.SCHEDULED,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class PlannedEvent(
    val id: String,
    val description: String,
    val dueAtMillis: Long,
    val confidence: String,
    val followUpRequested: Boolean,
    val state: PlannedEventState = PlannedEventState.UPCOMING
)

data class SharedMoment(
    val id: String,
    val title: String,
    val detail: String,
    val createdAtMillis: Long,
    val kind: String,
    val pinned: Boolean = false,
    val stageIndex: Int? = null
)

internal fun completedEventMoment(event: PlannedEvent, completedAtMillis: Long = System.currentTimeMillis()): SharedMoment =
    SharedMoment(
        id = stableIdempotencyKey("completed-event:${event.id}"),
        title = "Completed plan",
        detail = event.description,
        createdAtMillis = completedAtMillis,
        kind = "completed_event"
    )

internal data class RelationshipApplication(val friend: Friend, val applied: Boolean)

internal fun parseExplicitReminderRequest(
    text: String,
    nowMillis: Long,
    timeZoneId: String
): PlannedEvent? {
    val match = Regex(
        """^\s*(?:remind\s+me(?:\s+to)?|ask\s+me|check\s+in\s+with\s+me(?:\s+about)?)\s+(.+?)\s+(?:on\s+(\d{4}-\d{2}-\d{2})|(today|tomorrow))(?:\s+at\s+(\d{1,2}:\d{2}))?[.!?]?\s*$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(text) ?: return null
    val description = match.groupValues[1].trim().removePrefix("about ").removePrefix("About ")
    if (description.isBlank() || description.length > 500) return null
    val zone = validZoneId(timeZoneId)
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val date = when {
        match.groupValues[2].isNotBlank() -> runCatching { java.time.LocalDate.parse(match.groupValues[2]) }.getOrNull()
        match.groupValues[3].equals("today", ignoreCase = true) -> today
        match.groupValues[3].equals("tomorrow", ignoreCase = true) -> today.plusDays(1)
        else -> null
    } ?: return null
    val timeText = match.groupValues[4]
    val time = if (timeText.isBlank()) {
        java.time.LocalTime.of(9, 0)
    } else {
        val parts = timeText.split(':').mapNotNull(String::toIntOrNull)
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
        java.time.LocalTime.of(parts[0], parts[1])
    }
    val dueAtMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    if (dueAtMillis <= nowMillis) return null
    return PlannedEvent(
        id = java.util.UUID.randomUUID().toString(),
        description = description,
        dueAtMillis = dueAtMillis,
        confidence = if (timeText.isBlank()) "uncertain" else "confirmed",
        followUpRequested = true
    )
}

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
    ,val profilePending: Boolean = false
    ,val greeting: String = ""
    ,val relationship: String = ""
    ,val routineBlocks: List<RoutineBlock> = emptyList()
    ,val busyReplyBehavior: BusyReplyBehavior = BusyReplyBehavior.WAIT
    ,val busyReplyDelayMinutes: Int = 30
    ,val favorite: Boolean = false
)

data class ProfileFailure(val message: String, val detail: String?)

fun profileFailureText(language: AppLanguage, causeMessage: String?, detailLimit: Int = 120): ProfileFailure {
    val message = when (language) {
        AppLanguage.BANGLA -> "প্রোফাইল তৈরি করা যায়নি"
        AppLanguage.HINDI -> "प्रोफ़ाइल नहीं बन सकी"
        AppLanguage.ENGLISH -> "Couldn't create their profile"
    }
    val collapsed = causeMessage?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    val detail = collapsed.takeIf { it.isNotBlank() && it != "null" }?.take(detailLimit)
    return ProfileFailure(message, detail)
}

fun Friend.withGeneratedProfile(profile: FriendProfile): Friend = copy(
    personality = if (personality.isBlank() || personality == "Friendly and curious") profile.personality else personality,
    interests = profile.interests,
    family = profile.family,
    familyMembers = profile.familyMembers,
    familyActivities = profile.familyActivities,
    financialCondition = profile.financialCondition,
    address = profile.address,
    height = profile.height,
    weight = profile.weight,
    facialFeatures = profile.facialFeatures,
    bodyFeatures = profile.bodyFeatures,
    conversationStyle = profile.conversationStyle,
    profilePending = false
)

data class SystemFriendListing(
    val id: String,
    val name: String,
    val age: String,
    val gender: String,
    val category: String,
    val description: String,
    val tags: String,
    val relationship: String,
    val greeting: String,
    val photoUrl: String
)

fun availableSystemFriendDeck(catalog: List<SystemFriendListing>, addedCatalogIds: Set<String>): List<SystemFriendListing> =
    catalog.filterNot { it.id in addedCatalogIds }

fun initialGreetingMessage(friend: Friend, history: List<ChatMessage>, sessionId: String): ChatMessage? {
    val greeting = friend.greeting.trim()
    if (greeting.isBlank() || history.isNotEmpty()) return null
    return ChatMessage("assistant", greeting, category = "greeting", sessionId = sessionId)
}

fun validZoneId(timeZoneId: String): ZoneId = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())

internal fun isQuietTime(time: LocalTime, quietStartMinute: Int, quietEndMinute: Int): Boolean {
    val start = LocalTime.of(quietStartMinute.coerceIn(0, 1439) / 60, quietStartMinute.coerceIn(0, 1439) % 60)
    val end = LocalTime.of(quietEndMinute.coerceIn(0, 1439) / 60, quietEndMinute.coerceIn(0, 1439) % 60)
    return if (start < end) time >= start && time < end else time >= start || time < end
}

internal fun nextTimeAfterQuietHours(
    now: ZonedDateTime,
    quietStartMinute: Int,
    quietEndMinute: Int
): ZonedDateTime {
    if (!isQuietTime(now.toLocalTime(), quietStartMinute, quietEndMinute)) return now
    val endMinute = quietEndMinute.coerceIn(0, 1439)
    var end = now.withHour(endMinute / 60).withMinute(endMinute % 60).withSecond(0).withNano(0)
    if (!end.isAfter(now)) end = end.plusDays(1)
    return end
}

internal fun Friend.effectiveRoutineBlocks(): List<RoutineBlock> =
    routineBlocks.ifEmpty {
        listOf(
            RoutineBlock(
                id = "legacy",
                reason = busyReason,
                startMinute = busyStartHour.coerceIn(0, 23) * 60,
                durationMinutes = busyDurationHours.coerceIn(4, 8) * 60,
                daysOfWeek = (1..7).toSet()
            )
        )
    }

internal fun Friend.busyRoutineAt(now: ZonedDateTime): RoutineBlock? {
    val friendNow = now.withZoneSameInstant(validZoneId(timeZoneId))
    return effectiveRoutineBlocks().firstOrNull { block ->
        (0L..7L).any { daysBack ->
            val date = friendNow.toLocalDate().minusDays(daysBack)
            if (date.dayOfWeek.value !in block.daysOfWeek) {
                false
            } else {
                    val startMinute = block.startMinute.coerceIn(0, 1439)
                    val start = date.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(friendNow.zone)
                    val end = start.toLocalDateTime()
                        .plusMinutes(block.durationMinutes.coerceIn(1, 24 * 60).toLong())
                        .atZone(friendNow.zone)
                    !friendNow.isBefore(start) && friendNow.isBefore(end)
            }
        }
    }
}

internal fun shouldDelayBusyReply(friend: Friend, now: ZonedDateTime, sendImmediately: Boolean = false): Boolean =
    !sendImmediately && friend.busyReplyBehavior == BusyReplyBehavior.WAIT && friend.replyAvailableAt(now) != null

fun Friend.replyAvailableAt(now: ZonedDateTime): ZonedDateTime? {
    val friendNow = now.withZoneSameInstant(validZoneId(timeZoneId))
    val delayMinutes = busyReplyDelayMinutes.coerceIn(0, 120).toLong()
    return (0L..7L).asSequence()
        .map { friendNow.toLocalDate().minusDays(it) }
        .flatMap { date ->
            effectiveRoutineBlocks().asSequence()
                .filter { date.dayOfWeek.value in it.daysOfWeek }
                .mapNotNull { block ->
                    val startMinute = block.startMinute.coerceIn(0, 1439)
                    val start = date.atStartOfDay().plusMinutes(startMinute.toLong()).atZone(friendNow.zone)
                    val end = start.toLocalDateTime()
                        .plusMinutes(block.durationMinutes.coerceIn(1, 24 * 60).toLong())
                        .atZone(friendNow.zone)
                    val release = end.plusMinutes(delayMinutes)
                    release.takeIf { !friendNow.isBefore(start) && friendNow.isBefore(it) }
                }
        }
        .maxOrNull()
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

data class StructuredUserProfile(
    val name: String = "",
    val people: List<String> = emptyList(),
    val preferences: List<String> = emptyList()
)

const val MAX_STORED_USER_MEMORIES = 1000
const val MAX_PROFILE_FACTS = 20
const val MAX_RETRIEVED_USER_MEMORIES = 6
const val MAX_RETRIEVED_SESSION_SUMMARIES = 3

fun structuredProfileFromMemories(name: String, memories: List<UserMemory>): StructuredUserProfile {
    val peopleCategories = setOf("family", "relatives", "relationships", "people")
    val preferenceCategories = setOf("preferences", "interests", "routines")
    fun facts(categories: Set<String>) = memories
        .filter { it.category.trim().lowercase().replace(' ', '_') in categories }
        .map { it.fact.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .takeLast(MAX_PROFILE_FACTS)

    return StructuredUserProfile(name.trim(), facts(peopleCategories), facts(preferenceCategories))
}

fun retrieveRelevantUserMemories(
    query: String,
    memories: List<UserMemory>,
    profile: StructuredUserProfile,
    limit: Int = MAX_RETRIEVED_USER_MEMORIES
): List<UserMemory> {
    val candidates = (memories +
        profile.people.map { UserMemory("people", it) } +
        profile.preferences.map { UserMemory("preferences", it) })
        .filter { it.fact.isNotBlank() }
        .distinctBy { it.fact.trim().lowercase() }
    val terms = memorySearchTerms(query)
    if (terms.isEmpty() || limit <= 0) return emptyList()

    return candidates.withIndex()
        .mapNotNull { (index, memory) ->
            val score = memorySearchTerms("${memory.category} ${memory.fact}").count { it in terms }
            if (score == 0) null else Triple(index, score, memory)
        }
        .sortedWith(compareByDescending<Triple<Int, Int, UserMemory>> { it.second }.thenByDescending { it.first })
        .take(limit)
        .map { it.third }
}

fun retrieveRelevantSessionSummaries(
    query: String,
    summaries: List<SessionSummary>,
    limit: Int = MAX_RETRIEVED_SESSION_SUMMARIES
): List<SessionSummary> {
    val terms = memorySearchTerms(query)
    if (terms.isEmpty() || limit <= 0) return emptyList()

    return summaries.withIndex()
        .mapNotNull { (index, summary) ->
            val score = memorySearchTerms(summary.summary).count { it in terms }
            if (score == 0) null else Triple(index, score, summary)
        }
        .sortedWith(compareByDescending<Triple<Int, Int, SessionSummary>> { it.second }.thenByDescending { it.first })
        .take(limit)
        .map { it.third }
}

internal fun boundedPromptContext(items: List<String>, maxCharacters: Int, separator: String = "\n"): String {
    require(maxCharacters >= 0) { "Prompt context budget cannot be negative." }
    val result = StringBuilder()
    items.filter { it.isNotBlank() }.forEach { item ->
        if (result.length >= maxCharacters) return result.toString()
        val delimiter = if (result.isEmpty()) "" else separator
        val available = maxCharacters - result.length - delimiter.length
        if (available <= 0) return result.toString()
        result.append(delimiter)
        if (item.length <= available) {
            result.append(item)
        } else {
            if (available > 3) {
                result.append(item.take(available - 3)).append("...")
            } else {
                result.append(item.take(available))
            }
            return result.toString()
        }
    }
    return result.toString()
}

private val MEMORY_SEARCH_STOP_WORDS = setOf(
    "about", "after", "again", "and", "are", "because", "before", "been", "being", "but",
    "can", "could", "did", "does", "doing", "for", "from", "have", "her", "here", "him",
    "his", "how", "into", "its", "just", "like", "more", "most", "not", "our", "out",
    "over", "she", "should", "that", "the", "their", "them", "then", "there", "these",
    "they", "this", "those", "through", "too", "under", "very", "was", "were", "what",
    "when", "where", "which", "who", "why", "will", "with", "would", "you", "your"
)

private fun memorySearchTerms(text: String): Set<String> = Regex("[\\p{L}\\p{N}]{2,}")
    .findAll(text.lowercase())
    .map { it.value }
    .filterNot { it in MEMORY_SEARCH_STOP_WORDS }
    .toSet()

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
    val serverMessageId: String? = null,
    val parseFailed: Boolean = false
)

data class RelationshipScores(val friendship: Int, val love: Int)

data class RelationshipUpdate(val friendId: String, val friendshipScore: Int, val loveScore: Int)

/** Applies one reply's clamped delta (−1..2) to the scores currently stored for the friend. */
fun applyRelationshipDelta(friendshipScore: Int, loveScore: Int, friendshipImpact: Int, loveImpact: Int): RelationshipScores {
    val friendship = (friendshipScore + friendshipImpact.coerceIn(-1, 2)).coerceIn(0, 100)
    val love = if (friendship > 70) (loveScore + loveImpact.coerceIn(-1, 2)).coerceIn(0, 100) else 0
    return RelationshipScores(friendship, love)
}

/** Regenerating replaces the reply text. The new impacts are not applied again. */
fun applyRegeneratedReply(
    current: RelationshipScores,
    @Suppress("UNUSED_PARAMETER") friendshipImpact: Int,
    @Suppress("UNUSED_PARAMETER") loveImpact: Int
): RelationshipScores = current

fun friendshipLevel(score: Int): String = friendshipLevel(score, AppLanguage.ENGLISH)

fun friendshipStageIndex(score: Int): Int = when (score.coerceIn(0, 100)) {
    in 0..10 -> 0
    in 11..20 -> 1
    in 21..30 -> 2
    in 31..40 -> 3
    in 41..50 -> 4
    in 51..60 -> 5
    in 61..70 -> 6
    in 71..80 -> 7
    in 81..90 -> 8
    else -> 9
}

fun friendshipStageScore(stageIndex: Int): Int =
    listOf(0, 11, 21, 31, 41, 51, 61, 71, 81, 91).getOrElse(stageIndex) { 0 }

fun friendshipLevel(score: Int, language: AppLanguage): String = when (score.coerceIn(0, 100)) {
    in 0..10 -> when (language) { AppLanguage.ENGLISH -> "Complete stranger"; AppLanguage.BANGLA -> "সম্পূর্ণ অপরিচিত"; AppLanguage.HINDI -> "बिल्कुल अजनबी" }
    in 11..20 -> when (language) { AppLanguage.ENGLISH -> "Stranger"; AppLanguage.BANGLA -> "অপরিচিত"; AppLanguage.HINDI -> "अजनबी" }
    in 21..30 -> when (language) { AppLanguage.ENGLISH -> "Acquaintance"; AppLanguage.BANGLA -> "পরিচিত"; AppLanguage.HINDI -> "जान-पहचान" }
    in 31..40 -> when (language) { AppLanguage.ENGLISH -> "Casual friend"; AppLanguage.BANGLA -> "সাধারণ বন্ধু"; AppLanguage.HINDI -> "सामान्य दोस्त" }
    in 41..50 -> when (language) { AppLanguage.ENGLISH -> "Friend"; AppLanguage.BANGLA -> "বন্ধু"; AppLanguage.HINDI -> "दोस्त" }
    in 51..60 -> when (language) { AppLanguage.ENGLISH -> "Good friend"; AppLanguage.BANGLA -> "ভালো বন্ধু"; AppLanguage.HINDI -> "अच्छा दोस्त" }
    in 61..70 -> when (language) { AppLanguage.ENGLISH -> "Close friend"; AppLanguage.BANGLA -> "ঘনিষ্ঠ বন্ধু"; AppLanguage.HINDI -> "करीबी दोस्त" }
    in 71..80 -> when (language) { AppLanguage.ENGLISH -> "Trusted friend"; AppLanguage.BANGLA -> "বিশ্বস্ত বন্ধু"; AppLanguage.HINDI -> "भरोसेमंद दोस्त" }
    in 81..90 -> when (language) { AppLanguage.ENGLISH -> "Very close friend"; AppLanguage.BANGLA -> "খুব ঘনিষ্ঠ বন্ধু"; AppLanguage.HINDI -> "बहुत करीबी दोस्त" }
    else -> when (language) { AppLanguage.ENGLISH -> "Best friend"; AppLanguage.BANGLA -> "সবচেয়ে ভালো বন্ধু"; AppLanguage.HINDI -> "सबसे अच्छा दोस्त" }
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

fun Friend.withRelationshipDelta(friendshipImpact: Int, loveImpact: Int, userAge: String): Friend {
    val scores = applyRelationshipDelta(friendshipScore, loveScore, friendshipImpact, loveImpact)
    return copy(friendshipScore = scores.friendship, loveScore = scores.love).withLoveMeterGated(userAge)
}

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