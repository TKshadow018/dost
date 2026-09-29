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
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

object ConversationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageState = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val unreadState = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val sendingState = MutableStateFlow<Set<String>>(emptySet())
    private val waitingForScheduleState = MutableStateFlow<Set<String>>(emptySet())
    private val jobs = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activeFriendId: String? = null
    private lateinit var appContext: Context
    private lateinit var store: DostStore

    val messages: StateFlow<Map<String, List<ChatMessage>>> = messageState.asStateFlow()
    val unread: StateFlow<Map<String, Int>> = unreadState.asStateFlow()
    val sending: StateFlow<Set<String>> = sendingState.asStateFlow()
    val waitingForSchedule: StateFlow<Set<String>> = waitingForScheduleState.asStateFlow()

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
            }
        }
    }

    fun messagesFor(friendId: String): List<ChatMessage> = messageState.value[friendId] ?: store.loadMessages(friendId)

    fun send(friend: Friend, text: String, language: AppLanguage, imagePath: String = "") {
        if (text.isBlank() && imagePath.isBlank()) return
        val selectedModel = store.aiModel()
        val messageContent = text.ifBlank { "Shared an image" }
        if (store.hasScheduledReply(friend.id)) {
            val sessionId = store.currentSessionId(friend.id)
            if (sessionId == null) {
                store.setScheduledReply(friend.id, false)
                return
            }
            appendUserMessage(friend.id, messageContent, sessionId, selectedModel, imagePath)
            scheduleSessionEnd(friend.id, sessionId, SESSION_INACTIVITY_MILLIS)
            if (friend.id !in waitingForScheduleState.value) {
                // A reply was marked scheduled but no worker is waiting (state lost or a
                // worker already running). Re-schedule so this message is not dropped.
                sendingState.update { it + friend.id }
                waitingForScheduleState.update { it + friend.id }
                ConversationWorkScheduler.scheduleBusyReply(appContext, friend, sessionId, language)
            }
            return
        }
        if (jobs.contains(friend.id)) return
        ConversationWorkScheduler.cancelCheckIn(appContext, friend.id)
        val sessionId = store.currentSessionId(friend.id) ?: java.util.UUID.randomUUID().toString()
        store.saveCurrentSessionId(friend.id, sessionId)
        appendUserMessage(friend.id, messageContent, sessionId, selectedModel, imagePath)
        scheduleSessionEnd(friend.id, sessionId, SESSION_INACTIVITY_MILLIS)
        if (friend.replyAvailableAt(ZonedDateTime.now()) != null) {
            store.setScheduledReply(friend.id, true)
            sendingState.update { it + friend.id }
            waitingForScheduleState.update { it + friend.id }
            ConversationWorkScheduler.scheduleBusyReply(appContext, friend, sessionId, language)
            return
        }
        jobs.add(friend.id)
        sendingState.update { it + friend.id }
        scope.launch {
            try {
                delay(typingDelayMillis())
                val answer = requestReply(friend, sessionId, language)
                store.addUserMemories(friend.id, answer.memories)
                updateMood(friend, answer.friendshipImpact, answer.loveImpact)
                val friendship = (friend.friendshipScore + answer.friendshipImpact).coerceIn(0, 100)
                val love = if (friendship > 70) (friend.loveScore + answer.loveImpact).coerceIn(0, 100) else 0
                val currentFriend = store.loadFriends().firstOrNull { it.id == friend.id }
                if (currentFriend != null) {
                    val friends = store.loadFriends()
                    val index = friends.indexOfFirst { it.id == friend.id }
                    if (index >= 0) {
                        friends[index] = currentFriend.copy(friendshipScore = friendship, loveScore = love)
                        store.saveFriends(friends)
                    }
                }
                val hasQuestion = answer.followUpQuestion.isNotBlank() || answer.text.contains("?")
                val response = listOf(
                    answer.text,
                    answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                ).filter { it.isNotBlank() }.joinToString("\n\n")
                appendAssistant(friend.id, response.ifBlank { unavailableReply(language) }, answer.serverMessageId, selectedModel)
                when {
                    answer.followUpDelayMinutes != null -> scheduleInactivityQuestion(friend, language, sessionId, answer.followUpDelayMinutes * 60_000L)
                    !hasQuestion -> scheduleInactivityQuestion(friend, language, sessionId)
                }
            } catch (_: Throwable) {
                appendAssistant(friend.id, connectionIssueReply(language), modelId = selectedModel)
            } finally {
                jobs.remove(friend.id)
                sendingState.update { it - friend.id }
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
        jobs.remove(friendId)
        if (::appContext.isInitialized) ConversationWorkScheduler.cancelAllForFriend(appContext, friendId)
    }

    fun refreshBusyReply(friend: Friend) {
        if (!store.hasScheduledReply(friend.id)) return
        val sessionId = store.currentSessionId(friend.id) ?: return
        ConversationWorkScheduler.scheduleBusyReply(appContext, friend, sessionId, store.language())
    }

    private fun scheduleInactivityQuestion(friend: Friend, language: AppLanguage, sessionId: String, delayMillis: Long = DEFAULT_FOLLOW_UP_MILLIS) {
        val latestUserMessage = messagesFor(friend.id).lastOrNull { it.sessionId == sessionId && it.role == "user" } ?: return
        ConversationWorkScheduler.scheduleCheckIn(appContext, friend, sessionId, language, delayMillis, latestUserMessage.timestampMillis)
    }

    private fun appendUserMessage(friendId: String, text: String, sessionId: String, modelId: String, imagePath: String) {
        val userMessage = ChatMessage("user", text, sessionId = sessionId, modelId = modelId, imagePath = imagePath)
        val updated = messagesFor(friendId).map { message ->
            if (message.sessionId.isBlank()) message.copy(sessionId = sessionId) else message
        }.toMutableList().apply { add(userMessage) }
        publish(friendId, updated)
        store.saveMessages(friendId, updated)
        ConversationWorkScheduler.scheduleMessageLog(appContext, friendId, userMessage.messageId)
    }

    private fun appendAssistant(friendId: String, response: String, messageId: String? = null, modelId: String = store.aiModel()) {
        val sessionId = store.currentSessionId(friendId) ?: java.util.UUID.randomUUID().toString()
        store.saveCurrentSessionId(friendId, sessionId)
        val assistantMessage = ChatMessage("assistant", response, sessionId = sessionId, messageId = messageId ?: java.util.UUID.randomUUID().toString(), modelId = modelId)
        val saved = messagesFor(friendId).toMutableList().apply { add(assistantMessage) }
        publish(friendId, saved)
        store.saveMessages(friendId, saved)
        ConversationWorkScheduler.scheduleMessageLog(appContext, friendId, assistantMessage.messageId)
        scheduleSessionEnd(friendId, sessionId, SESSION_INACTIVITY_MILLIS)
        if (activeFriendId != friendId) {
            store.incrementUnread(friendId)
            unreadState.update { it + (friendId to store.unreadCount(friendId)) }
            store.loadFriends().firstOrNull { it.id == friendId }?.let { friend ->
                ReplyNotifier.notifyReply(appContext, friend, response)
            }
        }
    }

    private fun updateMood(friend: Friend, friendshipImpact: Int, loveImpact: Int) {
        val total = friendshipImpact + loveImpact
        val mood = when {
            total >= 2 -> "😊"
            total == 1 -> "🙂"
            total <= -1 -> "😔"
            else -> friend.mood
        }
        if (mood != friend.mood) {
            val friends = store.loadFriends()
            val index = friends.indexOfFirst { it.id == friend.id }
            if (index >= 0) {
                friends[index] = friends[index].copy(mood = mood)
                store.saveFriends(friends)
            }
        }
    }

    private fun unavailableReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "দুঃখিত, এই মুহূর্তে উত্তর দিতে পারছি না। একটু পরে আবার চেষ্টা করো।"
        AppLanguage.HINDI -> "माफ़ कीजिए, अभी जवाब नहीं दे पा रहा हूँ। कृपया थोड़ी देर बाद फिर कोशिश करें।"
        AppLanguage.ENGLISH -> "Sorry, I couldn't reply just now. Please try again in a moment."
    }

    private fun connectionIssueReply(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "এই মুহূর্তে সংযোগে সমস্যা হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করলে আমি উত্তর দেব।"
        AppLanguage.HINDI -> "अभी कनेक्शन में समस्या है। थोड़ी देर बाद फिर कोशिश करें, मैं जवाब दे दूँगा।"
        AppLanguage.ENGLISH -> "I'm having connection trouble right now. Try again in a little while and I'll reply."
    }

    private fun typingDelayMillis(): Long = 700L + java.util.concurrent.ThreadLocalRandom.current().nextLong(1_300L)

    fun regenerateReply(friend: Friend, language: AppLanguage) {
        val sessionId = store.currentSessionId(friend.id) ?: return
        if (jobs.contains(friend.id) || friend.replyAvailableAt(ZonedDateTime.now()) != null) return
        val messages = messagesFor(friend.id).toMutableList()
        // Remove trailing assistant messages until we reach a user message to regenerate against.
        while (messages.isNotEmpty() && messages.last().role == "assistant" && messages.last().sessionId == sessionId) {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.lastOrNull { it.sessionId == sessionId }?.role != "user") return
        publish(friend.id, messages)
        store.saveMessages(friend.id, messages)
        jobs.add(friend.id)
        sendingState.update { it + friend.id }
        scope.launch {
            try {
                delay(typingDelayMillis())
                val answer = requestReply(friend, sessionId, language)
                store.addUserMemories(friend.id, answer.memories)
                updateMood(friend, answer.friendshipImpact, answer.loveImpact)
                val response = listOf(
                    answer.text,
                    answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
                ).filter { it.isNotBlank() }.joinToString("\n\n")
                appendAssistant(friend.id, response.ifBlank { unavailableReply(language) }, answer.serverMessageId, store.aiModel())
            } catch (_: Throwable) {
                appendAssistant(friend.id, connectionIssueReply(language), modelId = store.aiModel())
            } finally {
                jobs.remove(friend.id)
                sendingState.update { it - friend.id }
            }
        }
    }

    private suspend fun requestReply(friend: Friend, sessionId: String, language: AppLanguage, forceFollowUp: Boolean = false, seedMessage: ChatMessage? = null): AiReply {
        val client = OpenRouterClient()
        val selectedModel = store.aiModel()
        val apiKey = store.openRouterApiKey()
        val history = messagesFor(friend.id).filter { it.sessionId == sessionId }.let { existing ->
            if (seedMessage != null && existing.isEmpty()) listOf(seedMessage) else existing
        }
        try {
            return client.reply(
                apiKey, selectedModel, store.installationId(), store.userName(), friend, history, language,
                store.loadUserMemories(friend.id), friend.friendshipScore, friend.loveScore,
                store.loadSessionSummaries(friend.id).takeLast(3), userTimeZoneId = store.userTimeZoneId(), forceFollowUp = forceFollowUp
            )
        } catch (firstFailure: Throwable) {
            if (selectedModel == OpenRouterClient.FALLBACK_FREE_MODEL) throw firstFailure
            delay(1_500)
            return client.reply(
                apiKey, OpenRouterClient.FALLBACK_FREE_MODEL, store.installationId(), store.userName(), friend, history, language,
                store.loadUserMemories(friend.id), friend.friendshipScore, friend.loveScore,
                store.loadSessionSummaries(friend.id).takeLast(3), userTimeZoneId = store.userTimeZoneId(), forceFollowUp = forceFollowUp
            )
        }
    }

    private fun scheduleSessionEnd(friendId: String, sessionId: String, delayMillis: Long) {
        ConversationWorkScheduler.scheduleSessionEnd(appContext, friendId, sessionId, delayMillis)
    }

    suspend fun runScheduledReply(friendId: String, sessionId: String, language: AppLanguage, forceFollowUp: Boolean, expectedUserTimestamp: Long?): Boolean {
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
        if (friend.replyAvailableAt(ZonedDateTime.now()) != null) return false
        val history = messagesFor(friendId).filter { it.sessionId == sessionId }
        val latestUserMessage = history.lastOrNull { it.role == "user" } ?: return true
        if (expectedUserTimestamp != null && latestUserMessage.timestampMillis != expectedUserTimestamp) return true
        if (!jobs.add(friendId)) return false
        val selectedModel = store.aiModel()
        waitingForScheduleState.update { it - friendId }
        sendingState.update { it + friendId }
        try {
            val answer = requestReply(friend, sessionId, language, forceFollowUp)
            store.addUserMemories(friendId, answer.memories)
            updateFriendship(friend, answer.friendshipImpact, answer.loveImpact)
            val response = listOf(
                answer.text,
                answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
            ).filter { it.isNotBlank() }.joinToString("\n\n")
            appendAssistant(friendId, response.ifBlank { unavailableReply(language) }, answer.serverMessageId, selectedModel)
            store.setScheduledReply(friendId, false)
            if (!forceFollowUp) {
                val hasQuestion = answer.followUpQuestion.isNotBlank() || answer.text.contains("?")
                when {
                    answer.followUpDelayMinutes != null -> scheduleInactivityQuestion(friend, language, sessionId, answer.followUpDelayMinutes * 60_000L)
                    !hasQuestion -> scheduleInactivityQuestion(friend, language, sessionId)
                }
            }
            return true
        } finally {
            jobs.remove(friendId)
            sendingState.update { it - friendId }
            if (store.hasScheduledReply(friendId)) waitingForScheduleState.update { it + friendId }
        }
    }

    suspend fun runSessionSummary(friendId: String, sessionId: String): Boolean {
        if (store.currentSessionId(friendId) != sessionId) {
            store.saveCurrentSessionId(friendId, null)
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
        val summary = OpenRouterClient().summarizeSession(store.openRouterApiKey(), store.aiModel(), friendName, sessionMessages, store.language())
        val latestAfterSummary = messagesFor(friendId).lastOrNull { it.sessionId == sessionId }
        if (store.currentSessionId(friendId) != sessionId || latestAfterSummary?.timestampMillis != latestMessage.timestampMillis) return false
        store.saveSessionSummary(friendId, SessionSummary(sessionId, sessionMessages.minOf { it.timestampMillis }, System.currentTimeMillis(), summary))
        if (store.currentSessionId(friendId) == sessionId) store.saveCurrentSessionId(friendId, null)
        return true
    }

    private fun updateFriendship(friend: Friend, friendshipImpact: Int, loveImpact: Int) {
        val friends = store.loadFriends()
        val index = friends.indexOfFirst { it.id == friend.id }
        if (index < 0) return
        val currentFriend = friends[index]
        val friendship = (currentFriend.friendshipScore + friendshipImpact).coerceIn(0, 100)
        val love = if (friendship > 70) (currentFriend.loveScore + loveImpact).coerceIn(0, 100) else 0
        friends[index] = currentFriend.copy(friendshipScore = friendship, loveScore = love)
        store.saveFriends(friends)
    }

    private fun publish(friendId: String, messages: List<ChatMessage>) {
        messageState.update { it + (friendId to messages) }
    }

    private const val DEFAULT_FOLLOW_UP_MILLIS = 30_000L //will be 1 minutes later
    private const val SESSION_INACTIVITY_MILLIS = 5 * 60 * 1000L // will be 60 minutes later

    suspend fun runMorningGreeting(friendId: String, sessionId: String, language: AppLanguage): Boolean {
        val friend = store.loadFriends().firstOrNull { it.id == friendId } ?: return true
        // Reschedule tomorrow's greeting first so the chain never dies.
        ConversationWorkScheduler.scheduleMorningGreeting(appContext, friend, language)
        if (friend.replyAvailableAt(ZonedDateTime.now()) != null) return true
        if (!jobs.add(friendId)) return false
        sendingState.update { it + friendId }
        try {
            val greetingSeed = ChatMessage("user", "Say a warm good-morning greeting to me as if you just woke up and thought of me.", sessionId = sessionId, modelId = store.aiModel())
            val answer = runCatching {
                requestReply(friend, sessionId, language, forceFollowUp = false, seedMessage = greetingSeed)
            }.getOrNull() ?: return true
            val response = listOf(
                answer.text,
                answer.followUpQuestion.takeIf { answer.followUpDelayMinutes == null }?.let(::limitToSingleQuestion).orEmpty()
            ).filter { it.isNotBlank() }.joinToString("\n\n")
            if (response.isNotBlank()) appendAssistant(friendId, response, answer.serverMessageId, store.aiModel())
            return true
        } finally {
            jobs.remove(friendId)
            sendingState.update { it - friendId }
        }
    }
}
