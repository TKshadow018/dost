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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object PeopleMessageOutbox {
    private const val PROFILE_NAMESPACE = "profile_namespace"
    private const val UID = "uid"
    private const val CONVERSATION_ID = "conversation_id"
    private const val MESSAGE_ID = "message_id"
    private val mutableUpdates = MutableSharedFlow<PeopleMessageDeliveryUpdate>(extraBufferCapacity = 32)
    val updates = mutableUpdates.asSharedFlow()

    fun enqueue(context: Context, profileNamespace: String, message: PendingPeopleMessage) {
        val request = OneTimeWorkRequestBuilder<PeopleMessageOutboxWorker>()
            .setInputData(workDataOf(
                PROFILE_NAMESPACE to profileNamespace,
                UID to message.uid,
                CONVERSATION_ID to message.conversationId,
                MESSAGE_ID to message.messageId
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(profileNamespace, message.conversationId, message.messageId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun publish(update: PeopleMessageDeliveryUpdate) {
        mutableUpdates.tryEmit(update)
    }

    private fun workName(profileNamespace: String, conversationId: String, messageId: String): String {
        val key = "$conversationId:$messageId".toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
            .joinToString("") { "%02x".format(it) }
        return "${profileNamespace}_people_message_$digest"
    }
}

internal class PeopleMessageOutboxWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val profileNamespace = inputData.getString("profile_namespace") ?: return Result.failure()
        if (profileNamespace != activeProfileNamespace(applicationContext)) return Result.success()
        val uid = inputData.getString("uid") ?: return Result.failure()
        val conversationId = inputData.getString("conversation_id") ?: return Result.failure()
        val messageId = inputData.getString("message_id") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()

        val store = DostStore(applicationContext, profileNamespace)
        val message = store.loadPendingPeopleMessage(conversationId, messageId)
            ?: return Result.success()
        if (message.uid != uid || message.status != PendingPeopleMessageStatus.WAITING) {
            return Result.success()
        }

        return try {
            PeopleRepository(applicationContext)
                .sendMessage(uid, conversationId, message.text, messageId)
            store.removePendingPeopleMessage(conversationId, messageId)
            PeopleMessageOutbox.publish(
                PeopleMessageDeliveryUpdate(uid, conversationId, messageId, SocialMessageDeliveryState.SENT)
            )
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val retryable = isRetryablePeopleMessageFailure(exception)
            val updated = message.copy(
                status = if (retryable) PendingPeopleMessageStatus.WAITING else PendingPeopleMessageStatus.FAILED
            )
            try {
                store.savePendingPeopleMessage(updated)
            } catch (_: Exception) {
                return Result.retry()
            }
            PeopleMessageOutbox.publish(
                PeopleMessageDeliveryUpdate(
                    uid,
                    conversationId,
                    messageId,
                    if (retryable) SocialMessageDeliveryState.WAITING else SocialMessageDeliveryState.FAILED
                )
            )
            if (retryable) Result.retry() else Result.success()
        }
    }
}
