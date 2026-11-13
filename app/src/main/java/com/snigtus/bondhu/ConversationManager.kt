package com.snigtus.dost

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

internal fun isScheduledFollowUpCurrent(expectedUserTimestamp: Long, latestUserMessage: ChatMessage?): Boolean =
    latestUserMessage?.role == "user" && latestUserMessage.timestampMillis == expectedUserTimestamp

internal fun canRestoreScheduledFollowUp(
    proactiveMessage: ProactiveMessage,
    currentSessionId: String?,
    latestUserMessage: ChatMessage?
): Boolean =
    proactiveMessage.trigger == ProactiveTrigger.FOLLOW_UP &&
        proactiveMessage.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.CANCELLED) &&
        currentSessionId == proactiveMessage.sessionId &&
        isScheduledFollowUpCurrent(proactiveMessage.expectedUserMessageTimestamp, latestUserMessage)

object ConversationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageState = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val unreadState = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val sendingState = MutableStateFlow<Set<String>>(emptySet())
    private val waitingForScheduleState = MutableStateFlow<Set<String>>(emptySet())
    private val relationshipState = MutableStateFlow<Map<String, RelationshipUpdate>>(emptyMap())
    private val jobs = ConcurrentHashMap.newKeySet<String>()
    private val outboundQueue = OutboundSendQueue()
    private val sendGuards = ConcurrentHashMap<String, Any>()
    private val dataImportGuard = Any()
    @Volatile private var dataImportGeneration = 0L
    @Volatile private var activeFriendId: String? = null
    private lateinit var appContext: Context
    private lateinit var store: DostStore

    val messages: StateFlow<Map<String, List<ChatMessage>>> = messageState.asStateFlow()
    val unread: StateFlow<Map<String, Int>> = unreadState.asStateFlow()
    val sending: StateFlow<Set<String>> = sendingState.asStateFlow()
    val waitingForSchedule: StateFlow<Set<String>> = waitingForScheduleState.asStateFlow()
    val relationshipUpdates: StateFlow<Map<String, RelationshipUpdate>> = relationshipState.asStateFlow()

    fun initialize(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            store = DostStore(appContext)
            val friends = store.loadFriends()
            unreadState.value = store.unreadCounts(friends.map { it.id })
            friends.forEach { friend ->
                if (store.hasScheduledReply(friend.id)) {
                    sendingState.update { it + friend.id }
                    waitingForScheduleState.update { it + friend.id }
                }
                val savedMessages = messagesFor(friend.id)
                val legacyMessages = savedMessages.filter { it.sessionId.isBlank() }
                val sessionId = store.currentSessionId(friend.id) ?: if (legacyMessages.isNotEmpty()) {
                    val migratedId = java.util.UUID.randomUUID().toString()
                    store.saveMessages(friend.id, savedMessages.map { message ->
                        if (message.sessionId.isBlank()) message.copy(sessionId = migratedId) else message
                    })
                    store.saveCurrentSessionId(friend.id, migratedId)
                    migratedId
                } else null
                val latestMessage = sessionId?.let { id -> messagesFor(friend.id).lastOrNull { it.sessionId == id } }
                if (sessionId != null && latestMessage != null) {
                    val remaining = SESSION_INACTIVITY_MILLIS - (System.currentTimeMillis() - latestMessage.timestampMillis)
                    scheduleSessionEnd(friend.id, sessionId, remaining.coerceAtLeast(0L))
                }
                messagesFor(friend.id).filterNot { store.isMessageLogged(it.messageId) }.forEach { message ->
                    ConversationWorkScheduler.scheduleMessageLog(appContext, friend.id, message.messageId)
                }
                publish(friend.id, messagesFor(friend.id))
            }
            refreshProactiveWork()
        }
    }

    fun messagesFor(friendId: String): List<ChatMessage> = messageState.value[friendId] ?: store.loadMessages(friendId)

    fun importLocalData(backup: JSONObject) {
        if (!::store.isInitialized) return
        synchronized(dataImportGuard) {
            dataImportGeneration++
            val friendIds = messageState.value.keys + store.loadFriends().map { it.id }
            friendIds.forEach { friendId ->
                ConversationWorkScheduler.cancelAllForFriend(appContext, friendId)
                outboundQueue.clear(friendId)
            }
            jobs.clear()
            sendingState.value = emptySet()
            waitingForScheduleState.value = emptySet()
            relationshipState.value = emptyMap()
            try {
                store.importLocalData(backup)
            } finally {
                val importedFriends = store.loadFriends()
                messageState.value = importedFriends.associate { it.id to store.loadMessages(it.id) }
                unreadState.value = store.unreadCounts(importedFriends.map { it.id })
                refreshProactiveWork()
            }
        }
    }

    private inline fun withCurrentImportGeneration(generation: Long, action: () -> Unit): Boolean =
        synchronized(dataImportGuard) {
            if (generation != dataImportGeneration) {
                false
            } else {
                action()
                true
            }
        }

    fun send(friend: Friend, text: String, language: AppLanguage, imagePath: String = "", sendImmediately: Boolean = false) {
        if (text.isBlank() && imagePath.isBlank()) return
        if (friend.profilePending) return
        val requestGeneration = dataImportGeneration
        lateinit var selectedModel: String
        val messageContent = text.ifBlank { "Shared an image" }
        val sessionId = synchronized(dataImportGuard) {
            if (requestGeneration != dataImportGeneration) return
            selectedModel = store.aiModel()
            if (store.hasScheduledReply(friend.id)) {
                val scheduledSessionId = store.currentSessionId(friend.id)
                if (scheduledSessionId == null) {
                    store.setScheduledReply(friend.id, false)
                    return
                }
                appendUserMessage(friend.id, messageContent, scheduledSessionId, selectedModel, imagePath)
                scheduleSessionEnd(friend.id, scheduledSessionId, SESSION_INACTIVITY_MILLIS)
                if (friend.id !in waitingForScheduleState.value) {
                    sendingState.update { it + friend.id }
                    waitingForScheduleState.update { it + friend.id }
                    ConversationWorkScheduler.scheduleBusyReply(appContext, friend, scheduledSessionId, language)
                }
                return
            }
            synchronized(sendGuard(friend.id)) {
                if (jobs.contains(friend.id)) {
                    queueOutbound(friend, messageContent, selectedModel, language, imagePath)
                    return
                }
                ConversationWorkScheduler.cancelCheckIn(appContext, friend.id)
                val id = store.currentSessionId(friend.id) ?: java.util.UUID.randomUUID().toString()
                store.saveCurrentSessionId(friend.id, id)
                appendUserMessage(friend.id, messageContent, id, selectedModel, imagePath)
                scheduleSessionEnd(friend.id, id, SESSION_INACTIVITY_MILLIS)
                if (shouldDelayBusyReply(friend, ZonedDateTime.now(), sendImmediately)) {
                    store.setScheduledReply(friend.id, true)
                    sendingState.update { it + friend.id }
                    waitingForScheduleState.update { it + friend.id }
                    ConversationWorkScheduler.scheduleBusyReply(appContext, friend, id, language)
                    return
                }
                jobs.add(friend.id)
                sendingState.update { it + friend.id }
                id
            }
        }
        scope.launch {
            try {
                delay(typingDelayMillis())
                val busyRoutine = friend.busyRoutineAt(ZonedDateTime.now())
                val busyInstruction = if (busyRoutine != null && (friend.busyReplyBehavior == BusyReplyBehavior.IMMEDIATE || sendImmediately)) {
                    "You are currently at ${busyRoutine.reason.ifBlank { friend.busyReason }}. Reply now and, only if natural, briefly mention why you are busy."
                } else ""
                val answer = requestReply(friend, sessionId, language, ephemeralInstruction = busyInstruction)
                if (!withCurrentImportGeneration(requestGeneration) {
                    if (answer.parseFailed) {
                        appendAssistant(friend.id, unreadableReply(language), modelId = selectedModel, category = ERROR_MESSAGE_CATEGORY)
                        return@withCurrentImportGeneration
                    }
                    store.addUserMemories(friend.id, answer.memories)
                    updateMood(friend, answer.friendshipImpact, gatedLoveImpact(friend, answer.loveImpact))
                    updateFriendship(friend.id, sessionId, answer.friendshipImpact, answer.loveImpact)
                    val response = listOf(
                        answer.text,
                        answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                    ).filter { it.isNotBlank() }.joinToString("\n\n")
                    appendAssistant(friend.id, response.ifBlank { unavailableReply(language) }, answer.serverMessageId, selectedModel)
                    when {
                        answer.followUpDelayMinutes != null -> scheduleInactivityQuestion(friend, language, sessionId, answer.followUpDelayMinutes * 60_000L)
                        shouldScheduleDefaultFollowUp(answer.text, answer.followUpQuestion) -> scheduleInactivityQuestion(friend, language, sessionId)
                    }
                }) {
                    return@launch
                }
            } catch (error: Throwable) {
                withCurrentImportGeneration(requestGeneration) {
                    appendAssistant(friend.id, failureReply(error, language), modelId = selectedModel, category = ERROR_MESSAGE_CATEGORY)
                }
            } finally {
                withCurrentImportGeneration(requestGeneration) {
                    finishJobAndResumeQueue(friend.id, requestGeneration)
                }
            }
        }
    }

    fun isSending(friendId: String): Boolean = jobs.contains(friendId)

    fun clearUnread(friendId: String) {
        store.clearUnread(friendId)
        unreadState.update { it + (friendId to 0) }
    }

    fun openChat(friendId: String) {
        activeFriendId = friendId
        clearUnread(friendId)
    }

    fun seedInitialGreeting(friend: Friend) {
        if (friend.greeting.isBlank()) return
        synchronized(sendGuard(friend.id)) {
            val history = messagesFor(friend.id)
            val sessionId = store.currentSessionId(friend.id) ?: java.util.UUID.randomUUID().toString()
            val greeting = initialGreetingMessage(friend, history, sessionId) ?: return
            store.saveCurrentSessionId(friend.id, sessionId)
            appendAssistant(friend.id, greeting.content, greeting.messageId, category = greeting.category)
        }
    }

    fun closeChat(friendId: String) {
        if (activeFriendId == friendId) activeFriendId = null
    }

    fun removeFriend(friendId: String) {
        // Delete images first while we still have the message list, then clear state.
        messagesFor(friendId).forEach { message ->
            message.imagePath.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).takeIf { it.isFile }?.delete() }
            }
        }
        publish(friendId, emptyList())
        unreadState.update { it - friendId }
        sendingState.update { it - friendId }
        waitingForScheduleState.update { it - friendId }
        outboundQueue.clear(friendId)
        jobs.remove(friendId)
        if (::appContext.isInitialized) ConversationWorkScheduler.cancelAllForFriend(appContext, friendId)
    }

    fun refreshBusyReply(friend: Friend) {
        if (store.hasScheduledReply(friend.id)) {
            val sessionId = store.currentSessionId(friend.id)
            if (sessionId != null) ConversationWorkScheduler.scheduleBusyReply(appContext, friend, sessionId, store.language())
        }
        refreshScheduledFollowUp(friend)
    }

    fun pauseFriendProactive(friendId: String, untilMillis: Long) {
        store.pauseProactive(friendId, untilMillis)
        ConversationWorkScheduler.cancelProactiveForFriend(appContext, friendId)
    }

    fun resumeFriendProactive(friendId: String) {
        store.resumeProactive(friendId)
        store.loadFriends().firstOrNull { it.id == friendId }?.let {
            ConversationWorkScheduler.scheduleMorningGreeting(appContext, it, store.language())
            refreshScheduledFollowUp(it)
        }
    }

    fun refreshProactiveWork() {
        if (!::store.isInitialized || !::appContext.isInitialized) return
        store.loadFriends().forEach { friend ->
            if (store.proactiveEnabled() && !store.isProactivePaused(friend.id)) {
                ConversationWorkScheduler.scheduleMorningGreeting(appContext, friend, store.language())
                refreshScheduledFollowUp(friend)
            } else {
                ConversationWorkScheduler.cancelProactiveForFriend(appContext, friend.id)
            }
            store.loadPlannedEvents(friend.id)
                .filter { it.state == PlannedEventState.UPCOMING && it.followUpRequested }
                .forEach { ConversationWorkScheduler.scheduleUserReminder(appContext, friend, it, store.language()) }
        }
    }

    private fun refreshScheduledFollowUp(friend: Friend) {
        if (!store.proactiveEnabled() || store.isProactivePaused(friend.id)) return
        val followUp = store.loadProactiveMessages(friend.id)
            .filter {
                it.trigger == ProactiveTrigger.FOLLOW_UP &&
                    it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.CANCELLED)
            }
            .maxByOrNull { it.updatedAtMillis } ?: return
        val latestUserMessage = messagesFor(friend.id)
            .lastOrNull { it.sessionId == followUp.sessionId && it.role == "user" }
        if (!canRestoreScheduledFollowUp(followUp, store.currentSessionId(friend.id), latestUserMessage)) return
        ConversationWorkScheduler.scheduleCheckIn(
            appContext,
            friend,
            followUp.sessionId,
            store.language(),
            (followUp.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L),
            followUp.expectedUserMessageTimestamp
        )
    }

    private fun scheduleInactivityQuestion(friend: Friend, language: AppLanguage, sessionId: String, delayMillis: Long = store.automaticFollowUpMinutes() * 60_000L) {
        val latestUserMessage = messagesFor(friend.id).lastOrNull { it.sessionId == sessionId && it.role == "user" } ?: return
        ConversationWorkScheduler.scheduleCheckIn(appContext, friend, sessionId, language, delayMillis, latestUserMessage.timestampMillis)
    }

    private fun appendUserMessage(friendId: String, text: String, sessionId: String, modelId: String, imagePath: String): ChatMessage {
        return synchronized(dataImportGuard) {
            val userMessage = ChatMessage("user", text, sessionId = sessionId, modelId = modelId, imagePath = imagePath)
            val updated = messagesFor(friendId).map { message ->
                if (message.sessionId.isBlank()) message.copy(sessionId = sessionId) else message
            }.toMutableList().apply { add(userMessage) }
            publish(friendId, updated)
            store.saveMessages(friendId, updated)
            ConversationWorkScheduler.scheduleMessageLog(appContext, friendId, userMessage.messageId)
            userMessage
        }
    }

    private fun appendAssistant(friendId: String, response: String, messageId: String? = null, modelId: String = store.aiModel(), category: String = "conversation") {
        synchronized(dataImportGuard) {
            val sessionId = store.currentSessionId(friendId) ?: java.util.UUID.randomUUID().toString()
            store.saveCurrentSessionId(friendId, sessionId)
            val assistantMessage = ChatMessage("assistant", response, category = category, sessionId = sessionId, messageId = messageId ?: java.util.UUID.randomUUID().toString(), modelId = modelId)
            val saved = messagesFor(friendId).toMutableList().apply { add(assistantMessage) }
            publish(friendId, saved)
            store.saveMessages(friendId, saved)
            val isError = category == ERROR_MESSAGE_CATEGORY
            if (!isError) ConversationWorkScheduler.scheduleMessageLog(appContext, friendId, assistantMessage.messageId)
            scheduleSessionEnd(friendId, sessionId, SESSION_INACTIVITY_MILLIS)
            if (!isError && activeFriendId != friendId) {
                store.incrementUnread(friendId)
                unreadState.update { it + (friendId to store.unreadCount(friendId)) }
                store.loadFriends().firstOrNull { it.id == friendId }?.let { friend ->
                    ReplyNotifier.notifyReply(appContext, friend, response, store.language())
                }
            }
        }
    }

    private fun updateMood(friend: Friend, friendshipImpact: Int, loveImpact: Int) {
        val total = friendshipImpact + loveImpact
        val mood = when {
            total >= 2 -> "😊"
            total == 1 -> "🙂"
            total <= -1 -> "😔"
            else -> return
        }
        store.updateFriend(friend.id) { current ->
            if (current.mood == mood) current else current.copy(mood = mood)
        }
    }

    private fun unavailableReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "দুঃখিত, এই মুহূর্তে উত্তর দিতে পারছি না। একটু পরে আবার চেষ্টা করো।"
        AppLanguage.HINDI -> "माफ़ कीजिए, अभी जवाब नहीं दे पा रहा हूँ। कृपया थोड़ी देर बाद फिर कोशिश करें।"
        AppLanguage.ENGLISH -> "Sorry, I couldn't reply just now. Please try again in a moment."
    }

    private fun failureReply(error: Throwable, language: AppLanguage): String {
        var current: Throwable? = error
        while (current != null) {
            if (current.message == "App credential required") return credentialReply(language)
            current = current.cause
        }
        return connectionIssueReply(language)
    }

    private fun credentialReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "অ্যাপটি আপডেট করুন, অথবা ফোনের তারিখ ও সময় অটোমেটিক রাখুন। নিজের OpenRouter কী দিলে চ্যাট চলবে।"
        AppLanguage.HINDI -> "ऐप अपडेट करें, या फ़ोन की तारीख और समय अपने आप सेट रखें। अपनी OpenRouter कुंजी डालने पर चैट चलती रहेगी।"
        AppLanguage.ENGLISH -> "Update the app, or set the phone's date and time to automatic. Chat still works if you enter your own OpenRouter key."
    }

    private fun unreadableReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "এই উত্তরটা পড়া যায়নি। মেসেজ চেপে ধরে Reply differently বেছে আবার চেষ্টা করো।"
        AppLanguage.HINDI -> "यह जवाब पढ़ा नहीं जा सका। संदेश को दबाकर Reply differently चुनें और फिर कोशिश करें।"
        AppLanguage.ENGLISH -> "I couldn't read that reply. Touch and hold this message and choose Reply differently to try again."
    }

    private fun connectionIssueReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "এই মুহূর্তে সংযোগে সমস্যা হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করলে আমি উত্তর দেব।"
        AppLanguage.HINDI -> "अभी कनेक्शन में समस्या है। थोड़ी देर बाद फिर कोशिश करें, मैं जवाब दे दूँगा।"
        AppLanguage.ENGLISH -> "I'm having connection trouble right now. Try again in a little while and I'll reply."
    }

    private fun typingDelayMillis(): Long = 700L + java.util.concurrent.ThreadLocalRandom.current().nextLong(1_300L)

    fun regenerateReply(friend: Friend, language: AppLanguage) {
        val requestGeneration = dataImportGeneration
        lateinit var sessionId: String
        synchronized(dataImportGuard) {
            if (requestGeneration != dataImportGeneration) return
            sessionId = store.currentSessionId(friend.id) ?: return
            if (jobs.contains(friend.id) || friend.replyAvailableAt(ZonedDateTime.now()) != null) return
            val messages = messagesFor(friend.id).toMutableList()
            while (messages.isNotEmpty() && messages.last().role == "assistant" && messages.last().sessionId == sessionId) {
                messages.removeAt(messages.lastIndex)
            }
            if (messages.lastOrNull { it.sessionId == sessionId }?.role != "user") return
            publish(friend.id, messages)
            store.saveMessages(friend.id, messages)
            jobs.add(friend.id)
            sendingState.update { it + friend.id }
        }
        scope.launch {
            try {
                delay(typingDelayMillis())
                val answer = requestReply(friend, sessionId, language)
                if (!withCurrentImportGeneration(requestGeneration) {
                    if (answer.parseFailed) {
                        appendAssistant(friend.id, unreadableReply(language), modelId = store.aiModel(), category = ERROR_MESSAGE_CATEGORY)
                        return@withCurrentImportGeneration
                    }
                    store.addUserMemories(friend.id, answer.memories)
                    val response = listOf(
                        answer.text,
                        answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                    ).filter { it.isNotBlank() }.joinToString("\n\n")
                    appendAssistant(friend.id, response.ifBlank { unavailableReply(language) }, answer.serverMessageId, store.aiModel())
                }) {
                    return@launch
                }
            } catch (error: Throwable) {
                withCurrentImportGeneration(requestGeneration) {
                    appendAssistant(friend.id, failureReply(error, language), modelId = store.aiModel(), category = ERROR_MESSAGE_CATEGORY)
                }
            } finally {
                withCurrentImportGeneration(requestGeneration) {
                    finishJobAndResumeQueue(friend.id, requestGeneration)
                }
            }
        }
    }

    private suspend fun requestReply(friend: Friend, sessionId: String, language: AppLanguage, forceFollowUp: Boolean = false, seedMessage: ChatMessage? = null, ephemeralInstruction: String = ""): AiReply {
        val client = OpenRouterClient()
        val selectedModel = store.aiModel()
        val apiKey = store.openRouterApiKey()
        val history = synchronized(sendGuard(friend.id)) {
            val existing = messagesForPrompt(outboundQueue.visibleHistory(friend.id, messagesFor(friend.id).filter { it.sessionId == sessionId }))
            if (seedMessage != null && existing.isEmpty()) listOf(seedMessage) else existing
        }
        val memories = store.loadUserMemories(friend.id)
        val sessionSummaries = store.loadSessionSummaries(friend.id)
        val structuredUserProfile = store.loadStructuredUserProfile(friend.id, store.userName())
        try {
            return client.reply(
                apiKey, selectedModel, store.installationId(), store.userName(), friend, history, language,
                memories, friend.friendshipScore, friend.loveScore,
                sessionSummaries, userTimeZoneId = store.userTimeZoneId(), forceFollowUp = forceFollowUp,
                sessionId = sessionId, ephemeralInstruction = ephemeralInstruction, userAge = store.userAge(),
                structuredUserProfile = structuredUserProfile
            )
        } catch (firstFailure: Throwable) {
            if (selectedModel == OpenRouterClient.FALLBACK_FREE_MODEL) throw firstFailure
            delay(1_500)
            return client.reply(
                apiKey, OpenRouterClient.FALLBACK_FREE_MODEL, store.installationId(), store.userName(), friend, history, language,
                memories, friend.friendshipScore, friend.loveScore,
                sessionSummaries, userTimeZoneId = store.userTimeZoneId(), forceFollowUp = forceFollowUp,
                sessionId = sessionId, ephemeralInstruction = ephemeralInstruction, userAge = store.userAge(),
                structuredUserProfile = structuredUserProfile
            )
        }
    }

    private fun scheduleSessionEnd(friendId: String, sessionId: String, delayMillis: Long) {
        ConversationWorkScheduler.scheduleSessionEnd(appContext, friendId, sessionId, delayMillis)
    }

    fun replyScheduledMessageNow(friendId: String) {
        val friend = store.loadFriends().firstOrNull { it.id == friendId } ?: return
        val sessionId = store.currentSessionId(friendId) ?: return
        if (!store.hasScheduledReply(friendId)) return
        ConversationWorkScheduler.scheduleBusyReply(appContext, friend, sessionId, store.language(), sendImmediately = true)
    }

    suspend fun runScheduledReply(
        friendId: String,
        sessionId: String,
        language: AppLanguage,
        forceFollowUp: Boolean,
        expectedUserTimestamp: Long?,
        sendImmediately: Boolean = false,
        proactiveMessageId: String? = null
    ): Boolean {
        val requestGeneration = dataImportGeneration
        val proactiveMessage = proactiveMessageId?.takeIf { it.isNotBlank() }?.let(store::proactiveMessage)
        if (proactiveMessage != null && messagesFor(friendId).any { it.messageId == proactiveMessage.idempotencyKey }) return true
        val friend = store.loadFriends().firstOrNull { it.id == friendId } ?: run {
            store.setScheduledReply(friendId, false)
            return true
        }
        if (store.currentSessionId(friendId) != sessionId) {
            store.setScheduledReply(friendId, false)
            waitingForScheduleState.update { it - friendId }
            sendingState.update { it - friendId }
            return true
        }
        if (forceFollowUp) {
            if (!store.proactiveEnabled() || store.isProactivePaused(friendId)) return true
            if (!store.canSendProactive(friendId)) return false
        }
        if (shouldDelayBusyReply(friend, ZonedDateTime.now(), sendImmediately)) return false
        val history = messagesFor(friendId).filter { it.sessionId == sessionId }
        val latestUserMessage = history.lastOrNull { it.role == "user" } ?: return true
        if (expectedUserTimestamp != null && !isScheduledFollowUpCurrent(expectedUserTimestamp, latestUserMessage)) return true
        if (!jobs.add(friendId)) return false
        val selectedModel = store.aiModel()
        waitingForScheduleState.update { it - friendId }
        sendingState.update { it + friendId }
        try {
            val busyRoutine = friend.busyRoutineAt(ZonedDateTime.now())
            val busyInstruction = if (busyRoutine != null && (friend.busyReplyBehavior == BusyReplyBehavior.IMMEDIATE || sendImmediately)) {
                "You are currently at ${busyRoutine.reason.ifBlank { friend.busyReason }}. Reply now and, only if natural, briefly mention why you are busy."
            } else ""
            val answer = requestReply(friend, sessionId, language, forceFollowUp, ephemeralInstruction = busyInstruction)
            withCurrentImportGeneration(requestGeneration) {
                if (forceFollowUp && (!store.proactiveEnabled() || store.isProactivePaused(friendId))) {
                    store.setScheduledReply(friendId, false)
                    return@withCurrentImportGeneration
                }
                if (expectedUserTimestamp != null && !isScheduledFollowUpCurrent(
                        expectedUserTimestamp,
                        messagesFor(friendId).lastOrNull { it.sessionId == sessionId && it.role == "user" }
                    )
                ) {
                    store.setScheduledReply(friendId, false)
                    return@withCurrentImportGeneration
                }
                if (answer.parseFailed) {
                    if (!forceFollowUp) {
                        appendAssistant(friendId, unreadableReply(language), modelId = selectedModel, category = ERROR_MESSAGE_CATEGORY)
                    }
                    store.setScheduledReply(friendId, false)
                    return@withCurrentImportGeneration
                }
                store.addUserMemories(friendId, answer.memories)
                if (!forceFollowUp) {
                    updateFriendship(friendId, sessionId, answer.friendshipImpact, answer.loveImpact)
                }
                val response = listOf(
                    answer.text,
                    answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                ).filter { it.isNotBlank() }.joinToString("\n\n")
                appendAssistant(
                    friendId,
                    response.ifBlank { unavailableReply(language) },
                    proactiveMessage?.idempotencyKey ?: answer.serverMessageId,
                    selectedModel,
                    category = if (forceFollowUp) "follow_up" else "busy_reply"
                )
                store.setScheduledReply(friendId, false)
                if (!forceFollowUp) {
                    when {
                        answer.followUpDelayMinutes != null -> scheduleInactivityQuestion(friend, language, sessionId, answer.followUpDelayMinutes * 60_000L)
                        shouldScheduleDefaultFollowUp(answer.text, answer.followUpQuestion) -> scheduleInactivityQuestion(friend, language, sessionId)
                    }
                }
            }
            return true
        } finally {
            withCurrentImportGeneration(requestGeneration) {
                finishJobAndResumeQueue(friendId, requestGeneration)
                if (store.hasScheduledReply(friendId)) waitingForScheduleState.update { it + friendId }
            }
        }
    }

    suspend fun runSessionSummary(friendId: String, sessionId: String): Boolean {
        val requestGeneration = dataImportGeneration
        if (store.currentSessionId(friendId) != sessionId) {
            withCurrentImportGeneration(requestGeneration) {
                if (store.currentSessionId(friendId) != sessionId) store.saveCurrentSessionId(friendId, null)
            }
            return true
        }
        if (store.hasScheduledReply(friendId) || jobs.contains(friendId)) return false
        val sessionMessages = messagesFor(friendId).filter { it.sessionId == sessionId }
        if (sessionMessages.isEmpty()) {
            store.saveCurrentSessionId(friendId, null)
            return true
        }
        val latestMessage = sessionMessages.maxBy { it.timestampMillis }
        val remaining = SESSION_INACTIVITY_MILLIS - (System.currentTimeMillis() - latestMessage.timestampMillis)
        if (remaining > 0L) return false
        val friendName = store.loadFriends().firstOrNull { it.id == friendId }?.name ?: return true
        val summaryInput = messagesForPrompt(sessionMessages)
        if (summaryInput.isEmpty()) {
            withCurrentImportGeneration(requestGeneration) {
                if (store.currentSessionId(friendId) == sessionId) store.saveCurrentSessionId(friendId, null)
            }
            return true
        }
        val summary = OpenRouterClient().summarizeSession(store.openRouterApiKey(), store.aiModel(), friendName, summaryInput, store.language())
        var shouldRetry = false
        val imported = !withCurrentImportGeneration(requestGeneration) {
            val latestAfterSummary = messagesFor(friendId).lastOrNull { it.sessionId == sessionId }
            if (store.currentSessionId(friendId) != sessionId || latestAfterSummary?.timestampMillis != latestMessage.timestampMillis) {
                shouldRetry = true
                return@withCurrentImportGeneration
            }
            store.saveSessionSummary(friendId, SessionSummary(sessionId, sessionMessages.minOf { it.timestampMillis }, System.currentTimeMillis(), summary))
            if (store.currentSessionId(friendId) == sessionId) store.saveCurrentSessionId(friendId, null)
        }
        return imported || !shouldRetry
    }

    private fun gatedLoveImpact(friend: Friend, loveImpact: Int): Int =
        if (romanceAllowed(store.userAge(), friend.age)) loveImpact else 0

    private fun updateFriendship(friendId: String, sessionId: String, friendshipImpact: Int, loveImpact: Int) {
        val latestUserMessage = messagesFor(friendId).lastOrNull {
            it.sessionId == sessionId && it.role == "user"
        }?.content.orEmpty()
        val fingerprint = relationshipOutcomeFingerprint(latestUserMessage)
        val application = store.applyRelationshipOutcome(friendId, sessionId, fingerprint) { current ->
            current.withRelationshipDelta(friendshipImpact, loveImpact, store.userAge())
        } ?: return
        val updated = application.friend
        if (!application.applied) return
        relationshipState.update { it + (updated.id to RelationshipUpdate(updated.id, updated.friendshipScore, updated.loveScore)) }
    }

    private fun publish(friendId: String, messages: List<ChatMessage>) {
        messageState.update { it + (friendId to messages) }
    }

    internal const val DEFAULT_FOLLOW_UP_MILLIS = 30 * 60_000L // 30 minutes
    internal const val SESSION_INACTIVITY_MILLIS = 60 * 60 * 1000L // 60 minutes

    /** Default check-in only when the reply did not already ask something. */
    internal fun shouldScheduleDefaultFollowUp(replyText: String, followUpQuestion: String): Boolean =
        followUpQuestion.isBlank() && !replyText.contains("?")

    suspend fun runMorningGreeting(
        friendId: String,
        sessionId: String,
        language: AppLanguage,
        proactiveMessageId: String? = null
    ): Boolean {
        val requestGeneration = dataImportGeneration
        val proactiveMessage = proactiveMessageId?.takeIf { it.isNotBlank() }?.let(store::proactiveMessage)
        if (proactiveMessage != null && messagesFor(friendId).any { it.messageId == proactiveMessage.idempotencyKey }) return true
        val friend = store.loadFriends().firstOrNull { it.id == friendId } ?: return true
        if (!store.proactiveEnabled() || store.isProactivePaused(friendId)) return true
        if (!store.canSendProactive(friendId)) {
            ConversationWorkScheduler.scheduleMorningGreeting(appContext, friend, language)
            return true
        }
        // Reschedule tomorrow's greeting first so the chain never dies.
        ConversationWorkScheduler.scheduleMorningGreeting(appContext, friend, language)
        if (friend.replyAvailableAt(ZonedDateTime.now()) != null) return true
        if (!jobs.add(friendId)) return false
        sendingState.update { it + friendId }
        try {
            val plan = MorningGreeting.plan(sessionId, messagesFor(friendId))
            val answer = runCatching {
                requestReply(friend, plan.sessionId, language, forceFollowUp = false, ephemeralInstruction = plan.instruction)
            }.getOrNull() ?: return true
            val response = listOf(
                answer.text,
                answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
            ).filter { it.isNotBlank() }.joinToString("\n\n")
            if (response.isNotBlank()) {
                withCurrentImportGeneration(requestGeneration) {
                    if (!store.proactiveEnabled() || store.isProactivePaused(friendId)) return@withCurrentImportGeneration
                    store.saveCurrentSessionId(friendId, plan.sessionId)
                    appendAssistant(
                        friendId,
                        response,
                        proactiveMessage?.idempotencyKey ?: answer.serverMessageId,
                        store.aiModel(),
                        category = "morning_greeting"
                    )
                    store.recordProactiveSent(friendId)
                }
            }
            return true
        } finally {
            withCurrentImportGeneration(requestGeneration) {
                finishJobAndResumeQueue(friendId, requestGeneration)
            }
        }
    }

    suspend fun runUserReminder(friendId: String, proactiveMessageId: String, language: AppLanguage): Boolean {
        val requestGeneration = dataImportGeneration
        val proactiveMessage = store.proactiveMessage(proactiveMessageId) ?: return true
        val event = store.loadPlannedEvents(friendId).firstOrNull { it.id == proactiveMessage.eventId }
            ?: return true
        if (event.state != PlannedEventState.UPCOMING) return true
        if (System.currentTimeMillis() < event.dueAtMillis) return false
        if (!store.proactiveEnabled() || store.isProactivePaused(friendId)) return true
        if (messagesFor(friendId).any { it.messageId == proactiveMessage.idempotencyKey }) {
            store.completePlannedEvent(friendId, event.id)
            return true
        }
        val friend = store.loadFriends().firstOrNull { it.id == friendId } ?: return true
        if (!jobs.add(friendId)) return false
        sendingState.update { it + friendId }
        try {
            val certainty = if (event.confidence == "confirmed") {
                "The timing and details are confirmed."
            } else {
                "The timing or details are uncertain; do not present them as certain."
            }
            val instruction = "The user asked you to check in about this planned event: ${event.description}. " +
                "$certainty Be warm and brief, and do not imply that you personally attended or witnessed it."
            val sessionId = store.currentSessionId(friendId) ?: proactiveMessage.sessionId
            val answer = requestReply(friend, sessionId, language, forceFollowUp = true, ephemeralInstruction = instruction)
            if (answer.parseFailed) return false
            val response = listOf(answer.text, answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }
                ?.let(::limitToSingleQuestion).orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
            if (response.isBlank()) return false
            withCurrentImportGeneration(requestGeneration) {
                if (store.loadPlannedEvents(friendId).firstOrNull { it.id == event.id } != event) {
                    return@withCurrentImportGeneration
                }
                store.saveCurrentSessionId(friendId, sessionId)
                appendAssistant(friendId, response, proactiveMessage.idempotencyKey, store.aiModel(), category = "reminder")
                store.completePlannedEvent(friendId, event.id)
            }
            return true
        } finally {
            withCurrentImportGeneration(requestGeneration) {
                finishJobAndResumeQueue(friendId, requestGeneration)
            }
        }
    }

    private fun sendGuard(friendId: String): Any = sendGuards.computeIfAbsent(friendId) { Any() }

    private fun queueOutbound(friend: Friend, text: String, modelId: String, language: AppLanguage, imagePath: String) {
        ConversationWorkScheduler.cancelCheckIn(appContext, friend.id)
        val sessionId = store.currentSessionId(friend.id) ?: java.util.UUID.randomUUID().toString().also {
            store.saveCurrentSessionId(friend.id, it)
        }
        val saved = appendUserMessage(friend.id, text, sessionId, modelId, imagePath)
        scheduleSessionEnd(friend.id, sessionId, SESSION_INACTIVITY_MILLIS)
        outboundQueue.enqueue(friend.id, saved.messageId, sessionId, language)
    }

    private fun finishJobAndResumeQueue(friendId: String, requestGeneration: Long) {
        val ready = synchronized(dataImportGuard) {
            if (requestGeneration != dataImportGeneration) return
            synchronized(sendGuard(friendId)) {
                jobs.remove(friendId)
                val queued = outboundQueue.takeReady(friendId)
                if (queued.isEmpty()) {
                    sendingState.update { it - friendId }
                    emptyList()
                } else {
                    jobs.add(friendId)
                    sendingState.update { it + friendId }
                    queued
                }
            }
        }
        if (ready.isEmpty()) return
        val friend = synchronized(dataImportGuard) {
            if (requestGeneration != dataImportGeneration) return
            store.loadFriends().firstOrNull { it.id == friendId }
        } ?: run {
            synchronized(dataImportGuard) {
                outboundQueue.clear(friendId)
                jobs.remove(friendId)
                sendingState.update { it - friendId }
            }
            return
        }
        val pending = ready.last()
        scope.launch {
            try {
                ConversationWorkScheduler.cancelCheckIn(appContext, friend.id)
                delay(typingDelayMillis())
                val answer = requestReply(friend, pending.sessionId, pending.language)
                if (!withCurrentImportGeneration(requestGeneration) {
                    if (answer.parseFailed) {
                        appendAssistant(friend.id, unreadableReply(pending.language), modelId = store.aiModel(), category = ERROR_MESSAGE_CATEGORY)
                        return@withCurrentImportGeneration
                    }
                    store.addUserMemories(friend.id, answer.memories)
                    updateMood(friend, answer.friendshipImpact, gatedLoveImpact(friend, answer.loveImpact))
                    updateFriendship(friend.id, pending.sessionId, answer.friendshipImpact, answer.loveImpact)
                    val response = listOf(
                        answer.text,
                        answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                    ).filter { it.isNotBlank() }.joinToString("\n\n")
                    appendAssistant(friend.id, response.ifBlank { unavailableReply(pending.language) }, answer.serverMessageId, store.aiModel())
                    when {
                        answer.followUpDelayMinutes != null -> scheduleInactivityQuestion(friend, pending.language, pending.sessionId, answer.followUpDelayMinutes * 60_000L)
                        shouldScheduleDefaultFollowUp(answer.text, answer.followUpQuestion) -> scheduleInactivityQuestion(friend, pending.language, pending.sessionId)
                    }
                }) {
                    return@launch
                }
            } catch (error: Throwable) {
                withCurrentImportGeneration(requestGeneration) {
                    appendAssistant(friend.id, failureReply(error, pending.language), modelId = store.aiModel(), category = ERROR_MESSAGE_CATEGORY)
                }
            } finally {
                withCurrentImportGeneration(requestGeneration) {
                    finishJobAndResumeQueue(friendId, requestGeneration)
                }
            }
        }
    }
}
