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
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyReply(context: Context, friend: Friend, preview: String, language: AppLanguage) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val postNotificationsGranted =
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ChatLaunch.EXTRA_FRIEND_ID, friend.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, friend.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(friend.name)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, snoozeLabel, actionIntent(context, friend.id, ACTION_SNOOZE))
            .addAction(0, pauseLabel, actionIntent(context, friend.id, ACTION_PAUSE))
            .build()
        if (postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notifications.notify(friend.id.hashCode(), notification)
        }
    }

    private fun actionIntent(context: Context, friendId: String, action: String): PendingIntent {
        val intent = Intent(context, ProactiveNotificationReceiver::class.java)
            .setAction(action)
            .putExtra("friend_id", friendId)
        val requestCode = friendId.hashCode() * 31 + action.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    class ProactiveNotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
}
