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

internal object ConversationWorkScheduler {
    private const val BUSY_REPLY = "busy_reply"
    private const val CHECK_IN = "check_in"
    private const val SESSION_SUMMARY = "session_summary"

    fun scheduleBusyReply(context: Context, friend: Friend, sessionId: String, language: AppLanguage) {
        val now = ZonedDateTime.now()
        val availableAt = friend.busyUntil(now) ?: now
        val delayMillis = Duration.between(now, availableAt).toMillis().coerceAtLeast(0L)
        enqueue(context, "reply_${friend.id}", ExistingWorkPolicy.REPLACE, BUSY_REPLY, friend.id, sessionId, language, delayMillis)
    }

    fun scheduleCheckIn(context: Context, friend: Friend, sessionId: String, language: AppLanguage, delayMillis: Long, expectedUserTimestamp: Long) {
        val now = ZonedDateTime.now()
        val requestedAt = now.plusNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis.coerceAtLeast(0L)))
        val dueAt = friend.busyUntil(requestedAt) ?: requestedAt
        val effectiveDelay = Duration.between(now, dueAt).toMillis().coerceAtLeast(0L)
        enqueue(context, "check_in_${friend.id}", ExistingWorkPolicy.REPLACE, CHECK_IN, friend.id, sessionId, language, effectiveDelay, expectedUserTimestamp)
    }

    fun cancelCheckIn(context: Context, friendId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("check_in_$friendId")
    }

    fun scheduleSessionEnd(context: Context, friendId: String, sessionId: String, delayMillis: Long) {
        enqueue(context, "session_end_$friendId", ExistingWorkPolicy.REPLACE, SESSION_SUMMARY, friendId, sessionId, null, delayMillis)
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
        expectedUserTimestamp: Long = 0L
    ) {
        val data = workDataOf(
            "task" to task,
            "friend_id" to friendId,
            "session_id" to sessionId,
            "expected_user_timestamp" to expectedUserTimestamp
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
}

internal class ConversationTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val friendId = inputData.getString("friend_id") ?: return Result.failure()
        val sessionId = inputData.getString("session_id") ?: return Result.failure()
        ConversationManager.initialize(applicationContext)
        return try {
            val completed = when (inputData.getString("task")) {
                "busy_reply" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: return Result.failure()
                    ConversationManager.runScheduledReply(friendId, sessionId, language, forceFollowUp = false, expectedUserTimestamp = null)
                }
                "check_in" -> {
                    val language = inputData.getString("language")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                        ?: return Result.failure()
                    ConversationManager.runScheduledReply(
                        friendId,
                        sessionId,
                        language,
                        forceFollowUp = true,
                        expectedUserTimestamp = inputData.getLong("expected_user_timestamp", 0L)
                    )
                }
                "session_summary" -> ConversationManager.runSessionSummary(friendId, sessionId)
                else -> return Result.failure()
            }
            if (completed) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}