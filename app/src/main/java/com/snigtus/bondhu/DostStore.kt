package com.snigtus.dost

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class DostStore(context: Context) {
    private val context = context
    private val preferences: SharedPreferences = context.getSharedPreferences("dost_local_data", Context.MODE_PRIVATE)

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

    fun loadFriends(): MutableList<Friend> = runCatching {
        val json = JSONArray(preferences.getString("friends", "[]"))
        MutableList(json.length()) { index ->
            val item = json.getJSONObject(index)
            Friend(item.getString("id"), item.getString("name"), item.getString("age"), item.getString("gender"), item.optString("personality", "Friendly and curious"), item.optString("interests"), item.optString("memories"), item.optString("conversationStyle", "Warm, natural, and concise"), item.optString("family"), item.optString("familyMembers"), item.optString("familyActivities"), item.optString("financialCondition"), item.optString("address"), item.optString("height"), item.optString("weight"), item.optString("facialFeatures"), item.optString("bodyFeatures"), item.optInt("friendshipScore", 0).coerceIn(0, 100), item.optInt("loveScore", 0).coerceIn(0, 100), item.optString("photoUri"), item.optInt("busyStartHour", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 8 else 9), item.optInt("busyDurationHours", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 6 else 8))
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
            })
        }
        preferences.edit().putString("friends", json.toString()).apply()
    }

    fun loadMessages(friendId: String): MutableList<ChatMessage> = runCatching {
        val json = JSONArray(preferences.getString("messages_$friendId", "[]"))
        MutableList(json.length()) { index ->
            val item = json.getJSONObject(index)
            ChatMessage(item.getString("role"), item.getString("content"), item.optString("category", "conversation"), item.optString("sessionId"), item.optLong("timestampMillis", System.currentTimeMillis()))
        }
    }.getOrDefault(mutableListOf())

    fun saveMessages(friendId: String, messages: List<ChatMessage>) {
        val json = JSONArray()
        messages.forEach { message -> json.put(JSONObject().apply { put("role", message.role); put("content", message.content); put("category", message.category); put("sessionId", message.sessionId); put("timestampMillis", message.timestampMillis) }) }
        preferences.edit().putString("messages_$friendId", json.toString()).apply()
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

    fun apiKey(): String = preferences.getString("openrouter_key", "") ?: ""

    fun saveApiKey(apiKey: String) {
        preferences.edit().putString("openrouter_key", apiKey.trim()).apply()
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
}