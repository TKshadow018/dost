package com.snigtus.dost

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal enum class AnonymousTelemetryEvent(val wireName: String) {
    FRIEND_PROFILE_OPENED("friend_profile_opened"),
    MEMORY_SAVED("memory_saved"),
    MEMORY_FORGOTTEN("memory_forgotten"),
    FAILED_SEND_RETRIED("failed_send_retried"),
    FIRST_MESSAGE_TO_FRIEND("first_message_to_friend"),
    SHARED_RITUAL_SAVED("shared_ritual_saved"),
    PROACTIVE_MESSAGES_DISABLED("proactive_messages_disabled"),
    PROACTIVE_MESSAGES_PAUSED("proactive_messages_paused"),
    PROACTIVE_REPLY_COMPLETED("proactive_reply_completed")
}

internal object AnonymousTelemetry {
    private const val ENDPOINT = "https://bisque-bear-900175.hostingersite.com/api/telemetry.php"
    private const val TAG = "DostTelemetry"

    suspend fun record(enabled: Boolean, event: AnonymousTelemetryEvent) = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext

        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val payload = JSONObject().put("event", event.wireName).toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { it.write(payload) }
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                Log.w(TAG, "Anonymous event was not accepted (HTTP $statusCode)")
            }
        } catch (error: IOException) {
            Log.w(TAG, "Anonymous event could not be sent", error)
        } finally {
            connection.disconnect()
        }
    }
}
