package com.snigtus.dost

import android.content.Context
import android.util.Log
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object PeopleMessageNotificationWork {
    fun enqueue(context: Context, profileNamespace: String, uid: String, chatId: String, messageId: String) {
        val request = OneTimeWorkRequestBuilder<PeopleMessageNotificationWorker>()
            .setInputData(workDataOf(
                PROFILE_NAMESPACE to profileNamespace,
                UID to uid,
                CONVERSATION_ID to chatId,
                MESSAGE_ID to messageId
            ))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(profileNamespace, chatId, messageId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun workName(profileNamespace: String, chatId: String, messageId: String): String {
        val key = "$chatId:$messageId".toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
            .joinToString("") { "%02x".format(it) }
        return "${profileNamespace}_people_notification_$digest"
    }

    private const val PROFILE_NAMESPACE = "profile_namespace"
    private const val UID = "uid"
    private const val CONVERSATION_ID = "conversation_id"
    private const val MESSAGE_ID = "message_id"
}

internal class PeopleMessageNotificationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val profileNamespace = inputData.getString("profile_namespace") ?: return Result.failure()
        if (profileNamespace != activeProfileNamespace(applicationContext)) return Result.success()
        val uid = inputData.getString("uid") ?: return Result.failure()
        val conversationId = inputData.getString("conversation_id") ?: return Result.failure()
        val messageId = inputData.getString("message_id") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()

        return try {
            PeopleRepository(applicationContext).notifyNewMessage(uid, conversationId, messageId)
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Could not deliver a People message notification request.", exception)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "PeopleMessageNotify"
        const val MAX_ATTEMPTS = 5
    }
}
