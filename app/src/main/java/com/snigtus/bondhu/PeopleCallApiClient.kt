package com.snigtus.dost

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class PeopleIceServer(
    val urls: List<String>,
    val username: String,
    val credential: String
)

internal class PeopleCallApiClient(
    context: Context,
    private val app: FirebaseApp?
) {
    private val applicationContext = context.applicationContext

    suspend fun notifyIncomingCall(uid: String, chatId: String, callId: String) {
        post(
            uid,
            "people_call_push.php",
            JSONObject().put("chat_id", chatId).put("call_id", callId)
        )
    }

    suspend fun notifyNewMessage(uid: String, chatId: String, messageId: String) {
        post(
            uid,
            "people_message_push.php",
            JSONObject().put("chat_id", chatId).put("message_id", messageId)
        )
    }

    suspend fun notifyCircleGroupMessage(uid: String, groupId: String, messageId: String) {
        post(
            uid,
            "people_message_push.php",
            JSONObject()
                .put("chat_id", groupId)
                .put("message_id", messageId)
                .put("circle_group", true)
        )
    }

    suspend fun notifyCircleDirectMessage(uid: String, chatId: String, messageId: String) {
        post(
            uid,
            "people_message_push.php",
            JSONObject()
                .put("chat_id", chatId)
                .put("message_id", messageId)
                .put("circle_direct", true)
        )
    }

    suspend fun lookupCircleHandle(uid: String, handle: String): String {
        val response = post(
            uid,
            "circle_handle_lookup.php",
            JSONObject().put("handle", handle)
        )
        val owner = response.optString("owner_uid")
        if (owner.isBlank()) throw IOException("No one is using that handle")
        return owner
    }

    suspend fun notifyCipherMessage(uid: String, chatId: String, messageId: String) {
        post(
            uid,
            "people_message_push.php",
            JSONObject().put("chat_id", chatId).put("message_id", messageId).put("cipher", true)
        )
    }

    suspend fun iceServers(uid: String, chatId: String, callId: String): List<PeopleIceServer> {
        val response = post(
            uid,
            "people_call_ice_servers.php",
            JSONObject().put("chat_id", chatId).put("call_id", callId)
        )
        val servers = response.optJSONArray("ice_servers")
            ?: throw IOException("The call service returned no ICE server configuration.")
        return (0 until servers.length()).map { index ->
            val server = servers.optJSONObject(index)
                ?: throw IOException("The call service returned an invalid ICE server.")
            val urls = server.optJSONArray("urls").asStrings()
            val username = server.optString("username")
            val credential = server.optString("credential")
            if (urls.isEmpty() || urls.any { !it.startsWith("turn:") && !it.startsWith("turns:") }
                || username.isBlank() || credential.isBlank()
            ) {
                throw IOException("The call service returned invalid TURN credentials.")
            }
            PeopleIceServer(urls, username, credential)
        }.ifEmpty {
            throw IOException("The call service returned no TURN servers.")
        }
    }

    private suspend fun post(uid: String, endpoint: String, request: JSONObject): JSONObject {
        val firebaseApp = app ?: throw IOException("Firebase is not configured for People calls.")
        val user = FirebaseAuth.getInstance(firebaseApp).currentUser
            ?: throw IOException("Sign in again to use People calls.")
        if (user.uid != uid) throw IOException("Sign in again to use People calls.")
        val idToken = user.getIdToken(false).await().token
            ?: throw IOException("Firebase did not return an ID token.")
        return withContext(Dispatchers.IO) {
            val connection = (URL("$API_ROOT/$endpoint").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            try {
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(MAX_RESPONSE_CHARS) }
                    .orEmpty()
                val json = try {
                    JSONObject(body)
                } catch (exception: Exception) {
                    throw IOException("The call service returned an invalid response.", exception)
                }
                if (status !in 200..299) {
                    throw IOException(json.optString("error").ifBlank {
                        "The call service is unavailable (HTTP $status)."
                    })
                }
                json
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun JSONArray?.asStrings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }
    }

    private companion object {
        const val API_ROOT = "https://bisque-bear-900175.hostingersite.com/api"
        const val MAX_RESPONSE_CHARS = 32_768
    }
}
