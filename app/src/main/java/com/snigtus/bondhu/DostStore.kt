package com.snigtus.dost

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DostStore(context: Context) {
    private val context = context
    private val preferences: SharedPreferences = context.getSharedPreferences("dost_local_data", Context.MODE_PRIVATE)
    private val messagesDirectory = File(context.filesDir, "messages").apply { mkdirs() }

    init {
        preferences.edit().remove("openrouter_key").apply()
    }

    fun hasUserProfile(): Boolean = preferences.getBoolean("user_profile_saved", false) || runCatching {
        JSONArray(preferences.getString("friends", "[]")).length() > 0
    }.getOrDefault(false)
    fun saveUserProfile(name: String, email: String, age: String, gender: String) {
        preferences.edit().putBoolean("user_profile_saved", true).putString("user_name", name).putString("user_email", email).putString("user_age", age).putString("user_gender", gender).apply()
    }
    fun userName(): String = preferences.getString("user_name", "") ?: ""
    fun userEmail(): String = preferences.getString("user_email", "") ?: ""
    fun userAge(): String = preferences.getString("user_age", "") ?: ""
    fun userGender(): String = preferences.getString("user_gender", "") ?: ""
    fun userTimeZoneId(): String = validZoneId(preferences.getString("user_time_zone", ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id).id
    fun saveUserTimeZoneId(timeZoneId: String) {
        preferences.edit().putString("user_time_zone", validZoneId(timeZoneId).id).apply()
    }
    fun installationId(): String = synchronized(preferences) {
        preferences.getString("installation_id", null)?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString().also {
            preferences.edit().putString("installation_id", it).commit()
        }
    }
    fun messageForLog(friendId: String, messageId: String): ChatMessage? = loadMessages(friendId).firstOrNull { it.messageId == messageId }
    fun isMessageLogged(messageId: String): Boolean = preferences.getBoolean("server_logged_$messageId", false)
    fun markMessageLogged(messageId: String) {
        preferences.edit().putBoolean("server_logged_$messageId", true).apply()
    }
    fun openRouterApiKey(): String = runCatching {
        val encoded = preferences.getString("openrouter_key_encrypted", null) ?: return ""
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        require(encrypted.size > GCM_IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_LENGTH, encrypted.copyOfRange(0, GCM_IV_LENGTH)))
        String(cipher.doFinal(encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)), StandardCharsets.UTF_8)
    }.getOrElse {
        preferences.edit().remove("openrouter_key_encrypted").apply()
        ""
    }
    fun saveOpenRouterApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            preferences.edit().remove("openrouter_key_encrypted").apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.iv + cipher.doFinal(apiKey.trim().toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString("openrouter_key_encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
    fun aiModel(): String = preferences.getString("ai_model", DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
    fun saveAiModel(model: String) {
        preferences.edit().putString("ai_model", model.trim()).apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(API_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            API_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    fun loadFriends(): MutableList<Friend> = runCatching {
        val json = JSONArray(preferences.getString("friends", "[]"))
        MutableList(json.length()) { index ->
            val item = json.getJSONObject(index)
            Friend(item.getString("id"), item.getString("name"), item.getString("age"), item.getString("gender"), item.optString("personality", "Friendly and curious"), item.optString("interests"), item.optString("memories"), item.optString("conversationStyle", "Warm, natural, and concise"), item.optString("family"), item.optString("familyMembers"), item.optString("familyActivities"), item.optString("financialCondition"), item.optString("address"), item.optString("height"), item.optString("weight"), item.optString("facialFeatures"), item.optString("bodyFeatures"), item.optInt("friendshipScore", 0).coerceIn(0, 100), item.optInt("loveScore", 0).coerceIn(0, 100), item.optString("photoUri"), item.optInt("busyStartHour", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 8 else 9), item.optInt("busyDurationHours", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 6 else 8), item.optString("busyReason", if ((item.optString("age").toIntOrNull() ?: 18) < 18) "School" else "Work"), validZoneId(item.optString("timeZoneId", ZoneId.systemDefault().id)).id)
        }
    }.getOrDefault(mutableListOf())

    fun saveFriends(friends: List<Friend>) {
        val json = JSONArray()
        friends.forEach { friend ->
            json.put(JSONObject().apply {
                put("id", friend.id); put("name", friend.name); put("age", friend.age); put("gender", friend.gender)
                put("personality", friend.personality); put("interests", friend.interests); put("memories", friend.memories); put("conversationStyle", friend.conversationStyle)
                put("family", friend.family); put("familyMembers", friend.familyMembers); put("familyActivities", friend.familyActivities)
                put("financialCondition", friend.financialCondition); put("address", friend.address); put("height", friend.height); put("weight", friend.weight)
                put("facialFeatures", friend.facialFeatures); put("bodyFeatures", friend.bodyFeatures)
                put("friendshipScore", friend.friendshipScore); put("loveScore", friend.loveScore)
                put("photoUri", friend.photoUri)
                put("busyStartHour", friend.busyStartHour); put("busyDurationHours", friend.busyDurationHours)
                put("busyReason", friend.busyReason)
                put("timeZoneId", friend.timeZoneId)
            })
        }
        preferences.edit().putString("friends", json.toString()).apply()
    }

    private fun messageFile(friendId: String): File {
        val safeId = friendId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown" }
        return File(messagesDirectory, "$safeId.json")
    }

    fun loadMessages(friendId: String): MutableList<ChatMessage> = runCatching {
        val file = messageFile(friendId)
        if (!file.isFile) {
            // One-time migration from SharedPreferences into per-friend files.
            val legacyJson = JSONArray(preferences.getString("messages_$friendId", "[]"))
            val migrated = parseMessages(friendId, legacyJson)
            writeMessagesFile(file, messagesToJson(migrated.messages))
            preferences.edit().remove("messages_$friendId").apply()
            return@runCatching migrated.messages
        }
        val parsed = parseMessages(friendId, JSONArray(file.readText()))
        if (parsed.needsMigration) saveMessages(friendId, parsed.messages)
        parsed.messages
    }.getOrDefault(mutableListOf())

    private class ParsedMessages(val messages: MutableList<ChatMessage>, val needsMigration: Boolean)

    private fun parseMessages(friendId: String, json: JSONArray): ParsedMessages {
        val needsMigration = (0 until json.length()).any { json.getJSONObject(it).optString("messageId").isBlank() }
        val messages = MutableList(json.length()) { index ->
            val item = json.getJSONObject(index)
            val role = item.getString("role")
            val content = item.getString("content")
            val sessionId = item.optString("sessionId")
            val timestamp = item.optLong("timestampMillis", System.currentTimeMillis())
            val messageId = item.optString("messageId").ifBlank {
                java.util.UUID.nameUUIDFromBytes("$friendId|$index|$role|$sessionId|$timestamp|$content".toByteArray(StandardCharsets.UTF_8)).toString()
            }
            ChatMessage(
                role, content, item.optString("category", "conversation"), sessionId, timestamp, messageId,
                item.optString("modelId"), item.optString("imagePath")
            )
        }
        return ParsedMessages(messages, needsMigration)
    }

    private fun messagesToJson(messages: List<ChatMessage>): JSONArray {
        val json = JSONArray()
        messages.forEach { message -> json.put(JSONObject().apply {
            put("role", message.role)
            put("content", message.content)
            put("category", message.category)
            put("sessionId", message.sessionId)
            put("timestampMillis", message.timestampMillis)
            put("messageId", message.messageId)
            put("modelId", message.modelId)
            put("imagePath", message.imagePath)
        }) }
        return json
    }

    private fun writeMessagesFile(file: File, json: JSONArray) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(json.toString())
            tmp.delete()
        }
    }

    fun saveMessages(friendId: String, messages: List<ChatMessage>) {
        runCatching { writeMessagesFile(messageFile(friendId), messagesToJson(messages)) }
    }

    fun deleteFriendData(friendId: String) {
        messageFile(friendId).delete()
        preferences.edit()
            .remove("messages_$friendId")
            .remove("user_memories_$friendId")
            .remove("session_summaries_$friendId")
            .remove("unread_$friendId")
            .remove("current_session_$friendId")
            .remove("scheduled_reply_$friendId")
            .apply()
    }

    fun currentSessionId(friendId: String): String? = preferences.getString("current_session_$friendId", null)?.takeIf { it.isNotBlank() }

    fun saveCurrentSessionId(friendId: String, sessionId: String?) {
        val editor = preferences.edit()
        if (sessionId.isNullOrBlank()) editor.remove("current_session_$friendId") else editor.putString("current_session_$friendId", sessionId)
        editor.apply()
    }

    fun hasScheduledReply(friendId: String): Boolean = preferences.getBoolean("scheduled_reply_$friendId", false)

    fun setScheduledReply(friendId: String, pending: Boolean) {
        preferences.edit().putBoolean("scheduled_reply_$friendId", pending).apply()
    }

    fun loadSessionSummaries(friendId: String): List<SessionSummary> = runCatching {
        val json = JSONArray(preferences.getString("session_summaries_$friendId", "[]"))
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            SessionSummary(item.getString("sessionId"), item.getLong("startedAtMillis"), item.getLong("endedAtMillis"), item.getString("summary"))
        }
    }.getOrDefault(emptyList())

    fun saveSessionSummary(friendId: String, summary: SessionSummary) {
        val summaries = (loadSessionSummaries(friendId).filterNot { it.sessionId == summary.sessionId } + summary).takeLast(10)
        val json = JSONArray()
        summaries.forEach { item ->
            json.put(JSONObject().apply {
                put("sessionId", item.sessionId)
                put("startedAtMillis", item.startedAtMillis)
                put("endedAtMillis", item.endedAtMillis)
                put("summary", item.summary)
            })
        }
        preferences.edit().putString("session_summaries_$friendId", json.toString()).apply()
    }

    fun unreadCount(friendId: String): Int = preferences.getInt("unread_$friendId", 0)

    fun unreadCounts(friendIds: List<String>): Map<String, Int> = friendIds.associateWith { unreadCount(it) }

    fun incrementUnread(friendId: String) {
        preferences.edit().putInt("unread_$friendId", unreadCount(friendId) + 1).apply()
    }

    fun clearUnread(friendId: String) {
        preferences.edit().putInt("unread_$friendId", 0).apply()
    }

    fun loadUserMemories(friendId: String): MutableList<UserMemory> = runCatching {
        val json = JSONArray(preferences.getString("user_memories_$friendId", "[]"))
        MutableList(json.length()) { index ->
            val item = json.getJSONObject(index)
            UserMemory(item.getString("category"), item.getString("fact"))
        }
    }.getOrDefault(mutableListOf())

    fun saveUserMemories(friendId: String, memories: List<UserMemory>) {
        val json = JSONArray()
        memories.forEach { memory ->
            json.put(JSONObject().apply { put("category", memory.category); put("fact", memory.fact) })
        }
        preferences.edit().putString("user_memories_$friendId", json.toString()).apply()
    }

    fun addUserMemories(friendId: String, newMemories: List<UserMemory>) {
        val memories = loadUserMemories(friendId)
        newMemories.forEach { memory ->
            val exists = memories.any { it.category.equals(memory.category, ignoreCase = true) && it.fact.equals(memory.fact, ignoreCase = true) }
            if (!exists && memory.fact.isNotBlank()) memories.add(memory)
        }
        saveUserMemories(friendId, memories.takeLast(1000))
    }

    fun language(): AppLanguage = when (preferences.getString("language", "bn")) {
        "hi" -> AppLanguage.HINDI
        "en" -> AppLanguage.ENGLISH
        else -> AppLanguage.BANGLA
    }
    fun saveLanguage(language: AppLanguage) {
        preferences.edit().putString("language", when (language) {
            AppLanguage.BANGLA -> "bn"
            AppLanguage.HINDI -> "hi"
            AppLanguage.ENGLISH -> "en"
        }).apply()
    }

    private companion object {
        const val API_KEY_ALIAS = "dost_openrouter_api_key"
        const val DEFAULT_AI_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}