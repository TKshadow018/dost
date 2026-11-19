package com.snigtus.dost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object PeopleCallNotifications {
    private const val INCOMING_CHANNEL_ID = "people_calls_incoming"
    private const val ONGOING_CHANNEL_ID = "people_calls_ongoing"
    private const val INCOMING_ID_BASE = 73000
    const val ONGOING_NOTIFICATION_ID = 73001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val incomingChannel = NotificationChannel(
            INCOMING_CHANNEL_ID,
            "People calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming People call alerts"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val ongoingChannel = NotificationChannel(
            ONGOING_CHANNEL_ID,
            "Ongoing People calls",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for active audio calls"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(incomingChannel)
            createNotificationChannel(ongoingChannel)
        }
    }

    fun cancelIncoming(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(
            INCOMING_ID_BASE + callId.hashCode().and(0x7fff)
        )
    }

    fun showIncoming(
        context: Context,
        chatId: String,
        callId: String,
        callerUid: String,
        calleeUid: String,
        callerName: String,
        mediaType: String = "audio"
    ) {
        if (!ReplyNotifier.canNotify(context)) return
        ensureChannel(context)
        val notificationId = INCOMING_ID_BASE + callId.hashCode().and(0x7fff)
        val person = Person.Builder().setName(callerName).setImportant(true).build()
        val openIntent = activityIntent(
            context,
            ACTION_OPEN_INCOMING,
            chatId,
            callId,
            callerUid,
            calleeUid,
            callerName,
            mediaType
        )
        val answerIntent = activityIntent(
            context,
            ACTION_ANSWER,
            chatId,
            callId,
            callerUid,
            calleeUid,
            callerName,
            mediaType
        )
        val declineIntent = receiverIntent(context, ACTION_DECLINE, chatId, callId)
        val notification = NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(
                callLabel(
                    DostStore(context).language(),
                    if (mediaType == "video") "Incoming video call" else "Incoming audio call",
                    if (mediaType == "video") "ভিডিও কল আসছে" else "অডিও কল আসছে",
                    if (mediaType == "video") "वीडियो कॉल आ रही है" else "ऑडियो कॉल आ रही है"
                )
            )
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000L)
            .setContentIntent(pendingActivity(context, notificationId, openIntent))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(
                person,
                pendingBroadcast(context, notificationId xor 0x10000, declineIntent),
                pendingActivity(context, notificationId xor 0x20000, answerIntent)
            ))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (error: SecurityException) {
            Log.e(TAG, "Incoming-call notification permission is unavailable.", error)
        }
    }

    fun ongoing(
        context: Context,
        uid: String,
        chatId: String,
        callId: String,
        peerName: String,
        localIsCaller: Boolean,
        mediaType: String = "audio"
    ): Notification {
        ensureChannel(context)
        val person = Person.Builder().setName(peerName).setImportant(true).build()
        val activity = activityIntent(
            context,
            ACTION_RESUME,
            chatId,
            callId,
            if (localIsCaller) uid else "",
            if (localIsCaller) "" else uid,
            peerName,
            mediaType
        )
        val end = receiverIntent(context, ACTION_END, chatId, callId)
        return NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(
                callLabel(
                    DostStore(context).language(),
                    if (mediaType == "video") "Ongoing video call" else "Ongoing audio call",
                    if (mediaType == "video") "চলমান ভিডিও কল" else "চলমান অডিও কল",
                    if (mediaType == "video") "जारी वीडियो कॉल" else "जारी ऑडियो कॉल"
                )
            )
            .setContentText(peerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setContentIntent(pendingActivity(context, callId.hashCode(), activity))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(
                person,
                pendingBroadcast(context, callId.hashCode() xor 0x30000, end)
            ))
            .build()
    }

    private fun activityIntent(
        context: Context,
        action: String,
        chatId: String,
        callId: String,
        callerUid: String,
        calleeUid: String,
        peerName: String,
        mediaType: String = "audio"
    ) = Intent(context, PeopleCallActivity::class.java).apply {
        this.action = action
        putExtra(PeopleCallActivity.EXTRA_CHAT_ID, chatId)
        putExtra(PeopleCallActivity.EXTRA_CALL_ID, callId)
        putExtra(PeopleCallActivity.EXTRA_CALLER_UID, callerUid)
        putExtra(PeopleCallActivity.EXTRA_CALLEE_UID, calleeUid)
        putExtra(PeopleCallActivity.EXTRA_PEER_NAME, peerName)
        putExtra(PeopleCallActivity.EXTRA_MEDIA_TYPE, mediaType)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private fun receiverIntent(context: Context, action: String, chatId: String, callId: String) =
        Intent(context, PeopleCallNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(PeopleCallActivity.EXTRA_CHAT_ID, chatId)
            putExtra(PeopleCallActivity.EXTRA_CALL_ID, callId)
        }

    private fun pendingActivity(context: Context, requestCode: Int, intent: Intent) =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun pendingBroadcast(context: Context, requestCode: Int, intent: Intent) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun callLabel(language: AppLanguage, english: String, bangla: String, hindi: String) = when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.BANGLA -> bangla
        AppLanguage.HINDI -> hindi
    }

    private const val TAG = "PeopleCall"
    const val ACTION_OPEN_INCOMING = "com.snigtus.dost.action.PEOPLE_CALL_OPEN"
    const val ACTION_ANSWER = "com.snigtus.dost.action.PEOPLE_CALL_ANSWER"
    const val ACTION_DECLINE = "com.snigtus.dost.action.PEOPLE_CALL_DECLINE"
    const val ACTION_RESUME = "com.snigtus.dost.action.PEOPLE_CALL_RESUME"
    const val ACTION_END = "com.snigtus.dost.action.PEOPLE_CALL_END"
}

class PeopleCallMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "people_call") return
        val chatId = data["chat_id"].orEmpty()
        val callId = data["call_id"].orEmpty()
        val callerUid = data["caller_uid"].orEmpty()
        val calleeUid = data["callee_uid"].orEmpty()
        val callerName = data["caller_name"].orEmpty().ifBlank { "Dost user" }.take(60)
        val mediaType = data["call_type"].orEmpty().ifBlank { "audio" }
        if (!chatId.matches(ID_PATTERN) || !callId.matches(ID_PATTERN)
            || !callerUid.matches(UID_PATTERN) || !calleeUid.matches(UID_PATTERN)
            || mediaType !in PEOPLE_CALL_MEDIA_TYPES
        ) {
            Log.w(TAG, "Ignoring an invalid incoming-call notification payload.")
            return
        }
        val uid = FirebaseAuthRepository(applicationContext).currentAccount?.uid
        if (uid != calleeUid || callerUid == calleeUid) return
        PeopleCallNotifications.showIncoming(
            applicationContext,
            chatId,
            callId,
            callerUid,
            calleeUid,
            callerName,
            mediaType
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = FirebaseAuthRepository(applicationContext).currentAccount?.uid ?: return@launch
                PeopleRepository(applicationContext).registerCallToken(uid, token)
            } catch (error: Exception) {
                Log.e(TAG, "Could not register the refreshed call notification token.", error)
            }
        }
    }

    private companion object {
        const val TAG = "PeopleCall"
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        val UID_PATTERN = Regex("[A-Za-z0-9:_-]{1,128}")
    }
}

class PeopleCallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val chatId = intent.getStringExtra(PeopleCallActivity.EXTRA_CHAT_ID).orEmpty()
        val callId = intent.getStringExtra(PeopleCallActivity.EXTRA_CALL_ID).orEmpty()
        if (!chatId.matches(ID_PATTERN) || !callId.matches(ID_PATTERN)) return
        if (action != PeopleCallNotifications.ACTION_DECLINE && action != PeopleCallNotifications.ACTION_END) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = FirebaseAuthRepository(context.applicationContext).currentAccount?.uid
                    ?: throw IllegalStateException("Sign in again to manage this call.")
                if (action == PeopleCallNotifications.ACTION_DECLINE) {
                    PeopleRepository(context.applicationContext)
                        .updateCallStatus(uid, chatId, callId, "rejected")
                } else {
                    withContext(Dispatchers.Main.immediate) {
                        PeopleCallEngineProvider.get(context.applicationContext)
                            .endFromNotification(uid, chatId, callId)
                    }
                    context.stopService(PeopleAudioCallService.intent(context))
                }
                PeopleCallNotifications.cancelIncoming(context, callId)
            } catch (error: Exception) {
                Log.e(TAG, "Could not update the People call from its notification.", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PeopleCall"
        val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}

class PeopleAudioCallService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        val chatId = intent.getStringExtra(PeopleCallActivity.EXTRA_CHAT_ID).orEmpty()
        val callId = intent.getStringExtra(PeopleCallActivity.EXTRA_CALL_ID).orEmpty()
        val peerName = intent.getStringExtra(PeopleCallActivity.EXTRA_PEER_NAME).orEmpty()
        val mediaType = intent.getStringExtra(PeopleCallActivity.EXTRA_MEDIA_TYPE).orEmpty().ifBlank { "audio" }
        val localIsCaller = intent.getBooleanExtra(EXTRA_LOCAL_IS_CALLER, false)
        if (!uid.matches(UID_PATTERN) || !chatId.matches(ID_PATTERN)
            || !callId.matches(ID_PATTERN) || peerName.isBlank()
            || mediaType !in PEOPLE_CALL_MEDIA_TYPES
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val notification = PeopleCallNotifications.ongoing(
            this,
            uid,
            chatId,
            callId,
            peerName,
            localIsCaller,
            mediaType
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PeopleCallNotifications.ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (mediaType == "video") ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            )
        } else {
            startForeground(PeopleCallNotifications.ONGOING_NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "com.snigtus.dost.action.PEOPLE_CALL_START"
        private const val EXTRA_UID = "people_call_uid"
        private const val EXTRA_LOCAL_IS_CALLER = "people_call_local_is_caller"
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        private val UID_PATTERN = Regex("[A-Za-z0-9:_-]{1,128}")

        fun intent(context: Context) = Intent(context, PeopleAudioCallService::class.java)

        fun start(
            context: Context,
            uid: String,
            chatId: String,
            callId: String,
            peerName: String,
            localIsCaller: Boolean,
            mediaType: String = "audio"
        ) {
            val intent = intent(context)
                .setAction(ACTION_START)
                .putExtra(EXTRA_UID, uid)
                .putExtra(PeopleCallActivity.EXTRA_CHAT_ID, chatId)
                .putExtra(PeopleCallActivity.EXTRA_CALL_ID, callId)
                .putExtra(PeopleCallActivity.EXTRA_PEER_NAME, peerName)
                .putExtra(PeopleCallActivity.EXTRA_MEDIA_TYPE, mediaType)
                .putExtra(EXTRA_LOCAL_IS_CALLER, localIsCaller)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
