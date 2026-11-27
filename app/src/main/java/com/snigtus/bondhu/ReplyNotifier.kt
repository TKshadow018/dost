package com.snigtus.dost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object ReplyNotifier {
    private const val CHANNEL_ID = "dost_replies"
    private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    private const val ACTION_SNOOZE = "com.snigtus.dost.action.SNOOZE_PROACTIVE"
    private const val ACTION_PAUSE = "com.snigtus.dost.action.PAUSE_PROACTIVE"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Friend replies", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyReply(context: Context, friend: Friend, preview: String, language: AppLanguage) {
        if (!superSecureSessionAllowsAi()) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        if (DostStore(context).isConversationMuted("ai:${friend.id}")) return
        val postNotificationsGranted =
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) return
        ensureChannel(context)
        val profileNamespace = activeProfileNamespace(context)
        val notificationId = "$profileNamespace:${friend.id}".hashCode()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ChatLaunch.EXTRA_FRIEND_ID, friend.id)
            .putExtra(ChatLaunch.EXTRA_PROFILE_NAMESPACE, profileNamespace)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snoozeLabel = when (language) {
            AppLanguage.BANGLA -> "২৪ ঘণ্টা বিরতি"
            AppLanguage.HINDI -> "24 घंटे स्थगित"
            AppLanguage.ENGLISH -> "Snooze 24 hours"
        }
        val pauseLabel = when (language) {
            AppLanguage.BANGLA -> "খোঁজ নেওয়া বিরতি"
            AppLanguage.HINDI -> "हालचाल रोकें"
            AppLanguage.ENGLISH -> "Pause check-ins"
        }
        val showPreview = DostStore(context).aiNotificationPreviewsEnabled()
        val title = if (showPreview) friend.name else "Dost"
        val text = if (showPreview) preview else when (language) {
            AppLanguage.BANGLA -> "আপনার AI বন্ধুর নতুন উত্তর এসেছে।"
            AppLanguage.HINDI -> "आपके AI मित्र का नया जवाब आया है।"
            AppLanguage.ENGLISH -> "You have a new AI friend reply."
        }
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Dost")
            .setContentText(when (language) {
                AppLanguage.BANGLA -> "নতুন AI উত্তর"
                AppLanguage.HINDI -> "नया AI जवाब"
                AppLanguage.ENGLISH -> "New AI reply"
            })
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .addAction(0, snoozeLabel, actionIntent(context, friend.id, profileNamespace, ACTION_SNOOZE))
            .addAction(0, pauseLabel, actionIntent(context, friend.id, profileNamespace, ACTION_PAUSE))
            .build()
        if (postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notifications.notify(notificationId, notification)
        }
    }

    fun cancelReply(context: Context, friendId: String) {
        val profileNamespace = activeProfileNamespace(context)
        NotificationManagerCompat.from(context)
            .cancel("$profileNamespace:$friendId".hashCode())
    }

    private fun actionIntent(context: Context, friendId: String, profileNamespace: String, action: String): PendingIntent {
        val intent = Intent(context, ProactiveNotificationReceiver::class.java)
            .setAction(action)
            .putExtra("friend_id", friendId)
            .putExtra(ChatLaunch.EXTRA_PROFILE_NAMESPACE, profileNamespace)
        val requestCode = "$profileNamespace:$friendId:$action".hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

}

class ProactiveNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestedProfile = intent.getStringExtra(ChatLaunch.EXTRA_PROFILE_NAMESPACE)
            ?: LEGACY_PROFILE_NAMESPACE
        if (requestedProfile != activeProfileNamespace(context)) return
        val friendId = intent.getStringExtra("friend_id")?.takeIf(String::isNotBlank) ?: return
        val until = when (intent.action) {
            "com.snigtus.dost.action.SNOOZE_PROACTIVE" -> System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            "com.snigtus.dost.action.PAUSE_PROACTIVE" -> Long.MAX_VALUE
            else -> return
        }
        DostStore(context).pauseProactive(friendId, until)
        ConversationWorkScheduler.cancelProactiveForFriend(context, friendId)
    }
}
