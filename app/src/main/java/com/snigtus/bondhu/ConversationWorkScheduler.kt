package com.snigtus.dost

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal fun nextMorningGreetingAt(now: ZonedDateTime, friend: Friend, offsetMinutes: Long = 0L): ZonedDateTime {
    val friendNow = now.withZoneSameInstant(validZoneId(friend.timeZoneId))
    var target = friendNow.withHour(8).withMinute(0).withSecond(0).withNano(0).plusMinutes(offsetMinutes)
    if (!friendNow.isBefore(target)) target = target.plusDays(1)
    // Don't greet during busy hours; push to after the busy window if needed.
    return friend.replyAvailableAt(target) ?: target
}

internal object ConversationWorkScheduler {
    private const val BUSY_REPLY = "busy_reply"
    private const val CHECK_IN = "check_in"
    private const val SESSION_SUMMARY = "session_summary"
    private const val MORNING_GREETING = "morning_greeting"
    private const val USER_REMINDER = "user_reminder"

    fun scheduleBusyReply(context: Context, friend: Friend, sessionId: String, language: AppLanguage, sendImmediately: Boolean = false) {
        val now = ZonedDateTime.now()
        val availableAt = if (sendImmediately || friend.busyReplyBehavior == BusyReplyBehavior.IMMEDIATE) now else friend.replyAvailableAt(now) ?: now
        val delayMillis = Duration.between(now, availableAt).toMillis().coerceAtLeast(0L)
        enqueue(context, "reply_${friend.id}", ExistingWorkPolicy.REPLACE, BUSY_REPLY, friend.id, sessionId, language, delayMillis, sendImmediately = sendImmediately)
    }

    fun scheduleCheckIn(context: Context, friend: Friend, sessionId: String, language: AppLanguage, delayMillis: Long, expectedUserTimestamp: Long) {
        val store = DostStore(context)
        if (!store.proactiveEnabled() || store.isProactivePaused(friend.id)) {
            cancelCheckIn(context, friend.id)
            return
        }
        cancelPendingTrigger(store, friend.id, ProactiveTrigger.FOLLOW_UP)
        val now = ZonedDateTime.now()
        val requestedAt = now.plusNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis.coerceAtLeast(0L)))
        val busyRelease = friend.replyAvailableAt(requestedAt) ?: requestedAt
        val eligibleMillis = store.nextProactiveEligibleAt(friend.id, busyRelease.toInstant().toEpochMilli()) ?: return
        val dueAt = java.time.Instant.ofEpochMilli(eligibleMillis).atZone(now.zone)
        val effectiveDelay = Duration.between(now, dueAt).toMillis().coerceAtLeast(0L)
        val proactive = ProactiveMessage(
            id = java.util.UUID.randomUUID().toString(),
            friendId = friend.id,
            trigger = ProactiveTrigger.FOLLOW_UP,
            sessionId = sessionId,
            dueAtMillis = dueAt.toInstant().toEpochMilli(),
            reason = "conversation_follow_up",
            idempotencyKey = stableIdempotencyKey("FOLLOW_UP:${friend.id}:$sessionId:$expectedUserTimestamp"),
            expectedUserMessageTimestamp = expectedUserTimestamp
        )
        store.saveProactiveMessage(proactive)
        enqueue(
            context,
            "check_in_${friend.id}",
            ExistingWorkPolicy.REPLACE,
            CHECK_IN,
            friend.id,
            sessionId,
            language,
            effectiveDelay,
            expectedUserTimestamp,
            proactiveMessageId = proactive.id
        )
    }

    fun cancelCheckIn(context: Context, friendId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("check_in_$friendId")
        val store = DostStore(context)
        store.loadProactiveMessages(friendId)
            .filter {
                it.trigger == ProactiveTrigger.FOLLOW_UP &&
                    it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING)
            }
            .forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
    }

    fun cancelProactiveForFriend(context: Context, friendId: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("morning_$friendId")
        workManager.cancelUniqueWork("check_in_$friendId")
        val store = DostStore(context)
        store.loadProactiveMessages(friendId)
            .filter {
                it.trigger != ProactiveTrigger.USER_REMINDER &&
                    it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING)
            }
            .forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
    }

    fun cancelAllForFriend(context: Context, friendId: String) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("reply_$friendId")
        workManager.cancelUniqueWork("check_in_$friendId")
        workManager.cancelUniqueWork("session_end_$friendId")
        workManager.cancelUniqueWork("morning_$friendId")
        val store = DostStore(context)
        store.loadProactiveMessages(friendId)
            .filter { it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING) }
            .forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
        store.loadPlannedEvents(friendId)
            .filter { it.state == PlannedEventState.UPCOMING }
            .forEach { event ->
                workManager.cancelUniqueWork("reminder_${event.id}")
                store.updatePlannedEvent(friendId, event.id) { it.copy(state = PlannedEventState.DISMISSED) }
            }
    }

    fun scheduleMorningGreeting(context: Context, friend: Friend, language: AppLanguage) {
        val store = DostStore(context)
        if (!store.proactiveEnabled() || store.isProactivePaused(friend.id)) {
            WorkManager.getInstance(context).cancelUniqueWork("morning_${friend.id}")
            return
        }
        cancelPendingTrigger(store, friend.id, ProactiveTrigger.MORNING_GREETING)
        val friendOrder = store.loadFriends().indexOfFirst { it.id == friend.id }.coerceAtLeast(0)
        val offsetMinutes = friendOrder * (GLOBAL_PROACTIVE_SPACING_MILLIS / 60_000L)
        val availableAt = nextMorningGreetingAt(ZonedDateTime.now(), friend, offsetMinutes)
        val eligibleMillis = store.nextProactiveEligibleAt(friend.id, availableAt.toInstant().toEpochMilli())
            ?: return
        val scheduledAt = java.time.Instant.ofEpochMilli(eligibleMillis).atZone(availableAt.zone)
        val sessionId = java.util.UUID.randomUUID().toString()
        val actualDelay = Duration.between(ZonedDateTime.now(), scheduledAt).toMillis().coerceAtLeast(0L)
        val proactive = ProactiveMessage(
            id = java.util.UUID.randomUUID().toString(),
            friendId = friend.id,
            trigger = ProactiveTrigger.MORNING_GREETING,
            sessionId = sessionId,
            dueAtMillis = scheduledAt.toInstant().toEpochMilli(),
            reason = "daily_greeting",
            idempotencyKey = stableIdempotencyKey("MORNING_GREETING:${friend.id}:${scheduledAt.toLocalDate()}")
        )
        store.saveProactiveMessage(proactive)
        enqueue(
            context,
            "morning_${friend.id}",
            ExistingWorkPolicy.REPLACE,
            MORNING_GREETING,
            friend.id,
            sessionId,
            language,
            actualDelay,
            proactiveMessageId = proactive.id
        )
    }

    fun scheduleUserReminder(context: Context, friend: Friend, event: PlannedEvent, language: AppLanguage) {
        val store = DostStore(context)
        if (event.state != PlannedEventState.UPCOMING || !event.followUpRequested) {
            cancelUserReminder(context, event.id)
            return
        }
        store.loadProactiveMessages(friend.id)
            .filter {
                it.eventId == event.id &&
                    it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING)
            }
            .forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
        val sessionId = store.currentSessionId(friend.id) ?: java.util.UUID.randomUUID().toString()
        val proactive = ProactiveMessage(
            id = java.util.UUID.randomUUID().toString(),
            friendId = friend.id,
            trigger = ProactiveTrigger.USER_REMINDER,
            sessionId = sessionId,
            dueAtMillis = event.dueAtMillis,
            reason = "user_requested_reminder",
            idempotencyKey = event.id,
            eventId = event.id
        )
        store.saveProactiveMessage(proactive)
        val delayMillis = (event.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        enqueue(
            context,
            "reminder_${event.id}",
            ExistingWorkPolicy.REPLACE,
            USER_REMINDER,
            friend.id,
            sessionId,
            language,
            delayMillis,
            proactiveMessageId = proactive.id
        )
    }

    fun cancelUserReminder(context: Context, eventId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$eventId")
        val store = DostStore(context)
        store.loadProactiveMessages().filter {
            it.eventId == eventId &&
                it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING)
        }.forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
    }

    fun scheduleSessionEnd(context: Context, friendId: String, sessionId: String, delayMillis: Long) {
        enqueue(context, "session_end_$friendId", ExistingWorkPolicy.REPLACE, SESSION_SUMMARY, friendId, sessionId, null, delayMillis)
    }

    fun scheduleMessageLog(context: Context, friendId: String, messageId: String) {
        val request = OneTimeWorkRequestBuilder<ConversationLogWorker>()
            .setInputData(workDataOf("friend_id" to friendId, "message_id" to messageId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("conversation_log_$messageId", ExistingWorkPolicy.KEEP, request)
    }

    private fun enqueue(
        context: Context,
        uniqueName: String,
        policy: ExistingWorkPolicy,
        task: String,
        friendId: String,
        sessionId: String,
        language: AppLanguage?,
        delayMillis: Long,
        expectedUserTimestamp: Long = 0L,
        sendImmediately: Boolean = false,
        proactiveMessageId: String? = null
    ) {
        val data = workDataOf(
            "task" to task,
            "friend_id" to friendId,
            "session_id" to sessionId,
            "expected_user_timestamp" to expectedUserTimestamp,
            "send_immediately" to sendImmediately,
            "proactive_message_id" to proactiveMessageId
        ).let { base ->
            if (language == null) base else androidx.work.Data.Builder().putAll(base).putString("language", language.name).build()
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ConversationTaskWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, policy, request)
    }

    private fun cancelPendingTrigger(store: DostStore, friendId: String, trigger: ProactiveTrigger) {
        store.loadProactiveMessages(friendId)
            .filter {
                it.trigger == trigger &&
                    it.state in setOf(ProactiveDeliveryState.SCHEDULED, ProactiveDeliveryState.SENDING)
            }
            .forEach { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
    }

}

internal class ConversationTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val friendId = inputData.getString("friend_id") ?: return Result.failure()
        val sessionId = inputData.getString("session_id") ?: return Result.failure()
        val task = inputData.getString("task") ?: return Result.failure()
        ConversationManager.initialize(applicationContext)
        val store = DostStore(applicationContext)
        val proactiveMessageId = inputData.getString("proactive_message_id").orEmpty()
        var proactiveMessage: ProactiveMessage? = null
        if (proactiveMessageId.isNotBlank()) {
            val existing = store.proactiveMessage(proactiveMessageId) ?: return Result.success()
            if (existing.state == ProactiveDeliveryState.COMPLETED ||
                existing.state == ProactiveDeliveryState.CANCELLED
            ) return Result.success()
            val claimedMessage = store.claimProactiveMessage(proactiveMessageId) ?: return Result.retry()
            proactiveMessage = claimedMessage
            if (claimedMessage.trigger != ProactiveTrigger.USER_REMINDER &&
                (!store.proactiveEnabled() || store.isProactivePaused(friendId))
            ) {
                store.finishProactiveMessage(claimedMessage.id, ProactiveDeliveryState.CANCELLED)
                return Result.success()
            }
            if (ConversationManager.messagesFor(friendId).any { it.messageId == claimedMessage.idempotencyKey }) {
                store.finishProactiveMessage(claimedMessage.id, ProactiveDeliveryState.COMPLETED)
                return Result.success()
            }
        }
        return try {
            val completed = when (task) {
                "busy_reply" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: run {
                            proactiveMessage?.let { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
                            return Result.failure()
                        }
                    ConversationManager.runScheduledReply(
                        friendId,
                        sessionId,
                        language,
                        forceFollowUp = false,
                        expectedUserTimestamp = null,
                        sendImmediately = inputData.getBoolean("send_immediately", false),
                        proactiveMessageId = proactiveMessageId
                    )
                }
                "check_in" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: run {
                            proactiveMessage?.let { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
                            return Result.failure()
                        }
                    ConversationManager.runScheduledReply(
                        friendId,
                        sessionId,
                        language,
                        forceFollowUp = true,
                        expectedUserTimestamp = inputData.getLong("expected_user_timestamp", 0L),
                        sendImmediately = inputData.getBoolean("send_immediately", false),
                        proactiveMessageId = proactiveMessageId
                    )
                }
                "session_summary" -> ConversationManager.runSessionSummary(friendId, sessionId)
                "morning_greeting" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: run {
                            proactiveMessage?.let { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
                            return Result.failure()
                        }
                    ConversationManager.runMorningGreeting(friendId, sessionId, language, proactiveMessageId)
                }
                "user_reminder" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: run {
                            proactiveMessage?.let { store.finishProactiveMessage(it.id, ProactiveDeliveryState.CANCELLED) }
                            return Result.failure()
                        }
                    ConversationManager.runUserReminder(friendId, proactiveMessageId, language)
                }
                else -> return Result.failure()
            }
            proactiveMessage?.let { record ->
                val delivered = ConversationManager.messagesFor(friendId).any { it.messageId == record.idempotencyKey }
                store.finishProactiveMessage(
                    record.id,
                    when {
                        !completed -> ProactiveDeliveryState.SCHEDULED
                        delivered -> ProactiveDeliveryState.COMPLETED
                        else -> ProactiveDeliveryState.CANCELLED
                    }
                )
                if (completed && delivered && record.trigger != ProactiveTrigger.USER_REMINDER) {
                    AnonymousTelemetry.record(
                        store.anonymousTelemetryEnabled(),
                        AnonymousTelemetryEvent.PROACTIVE_REPLY_COMPLETED
                    )
                }
            }
            if (completed) Result.success() else Result.retry()
        } catch (_: Exception) {
            proactiveMessage?.let { store.finishProactiveMessage(it.id, ProactiveDeliveryState.SCHEDULED) }
            Result.retry()
        }
    }
}

internal class ConversationLogWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val friendId = inputData.getString("friend_id") ?: return Result.failure()
        val messageId = inputData.getString("message_id") ?: return Result.failure()
        val store = DostStore(applicationContext)
        if (store.isMessageLogged(messageId)) return Result.success()
        val message = store.messageForLog(friendId, messageId) ?: return Result.failure()
        val friendName = store.loadFriends().firstOrNull { it.id == friendId }?.name ?: friendId
        return try {
            if (OpenRouterClient().logConversationMessage(store.installationId(), store.userName(), friendId, friendName, message)) {
                store.markMessageLogged(messageId)
                Result.success()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
