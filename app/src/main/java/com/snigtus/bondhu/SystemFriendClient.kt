package com.snigtus.dost

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

internal class SystemFriendClient {
    suspend fun getCatalog(): List<SystemFriendListing> = withContext(Dispatchers.IO) {
        val profiles = getJson("").getJSONArray("profiles")
        List(profiles.length()) { index ->
            val profile = profiles.getJSONObject(index)
            val id = profile.getString("id").takeIf(String::isNotBlank)
                ?: throw IOException("A system friend profile has no ID")
            SystemFriendListing(
                id = id,
                name = requiredText(profile, "name"),
                age = requiredText(profile, "age"),
                gender = requiredText(profile, "gender"),
                interests = profile.optString("interests"),
                photoUrl = photoUrl(profile.optString("photo_url"))
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getProfile(catalogId: String): Friend = withContext(Dispatchers.IO) {
        val query = "id=${URLEncoder.encode(catalogId, "UTF-8")}"
        val profile = getJson(query).getJSONObject("profile")
        val id = profile.getString("id").takeIf(String::isNotBlank)
            ?: throw IOException("A system friend profile has no ID")
        val age = requiredText(profile, "age")
        Friend(
            id = UUID.randomUUID().toString(),
            name = requiredText(profile, "name"),
            age = age,
            gender = requiredText(profile, "gender"),
            personality = profile.optString("personality", "Friendly and curious"),
            interests = profile.optString("interests"),
            memories = profile.optString("memories"),
            conversationStyle = profile.optString("conversationStyle", "Warm, natural, and concise"),
            family = profile.optString("family"),
            familyMembers = profile.optString("familyMembers"),
            familyActivities = profile.optString("familyActivities"),
            financialCondition = profile.optString("financialCondition"),
            address = profile.optString("address"),
            height = profile.optString("height"),
            weight = profile.optString("weight"),
            facialFeatures = profile.optString("facialFeatures"),
            bodyFeatures = profile.optString("bodyFeatures"),
            photoUri = photoUrl(profile.optString("photo_url")),
            busyStartHour = profile.optInt("busyStartHour", if ((age.toIntOrNull() ?: 18) < 18) 8 else 9).coerceIn(0, 23),
            busyDurationHours = profile.optInt("busyDurationHours", if ((age.toIntOrNull() ?: 18) < 18) 6 else 8).coerceIn(4, 8),
            busyReason = profile.optString("busyReason", if ((age.toIntOrNull() ?: 18) < 18) "School" else "Work"),
            timeZoneId = validZoneId(profile.optString("timeZoneId", java.time.ZoneId.systemDefault().id)).id,
            catalogId = id
        )
    }

    suspend fun savePhotoForOfflineUse(context: Context, friend: Friend): Friend = withContext(Dispatchers.IO) {
        if (friend.photoUri.isBlank()) return@withContext friend
        friend.copy(photoUri = Uri.fromFile(FriendPhotoLoader.saveRemotePhoto(context, friend.photoUri, friend.catalogId)).toString())
    }

    private fun getJson(query: String): JSONObject {
        val url = if (query.isBlank()) API_URL else "$API_URL?$query"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.useCaches = false
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IOException(message?.takeIf(String::isNotBlank) ?: "Could not load system friends (HTTP $status)")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requiredText(profile: JSONObject, key: String): String =
        profile.optString(key).trim().takeIf(String::isNotBlank)
            ?: throw IOException("A system friend profile is missing $key")

    private fun photoUrl(path: String): String =
        path.takeIf(String::isNotBlank)?.let { URL(URL(API_URL), it).toExternalForm() }.orEmpty()

    private companion object {
        const val API_URL = "https://bisque-bear-900175.hostingersite.com/api/friends.php"
    }
}
