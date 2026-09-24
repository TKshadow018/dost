package com.snigtus.dost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OpenRouterClient {
    private val models = listOf(
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "qwen/qwen3.8-27b:free",
        "poolside/laguna-s-2.1:free",
        "z-ai/glm-5.2:free",
        "inclusionai/ling-3.0-flash-sante:free"
    )

    suspend fun reply(apiKey: String, friend: Friend, history: List<ChatMessage>, language: AppLanguage, userMemories: List<UserMemory>, friendshipScore: Int, loveScore: Int, sessionSummaries: List<SessionSummary> = emptyList(), forceFollowUp: Boolean = false): AiReply = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        models.forEach { model ->
            try {
                return@withContext request(model, apiKey, friend, history, language, userMemories, friendshipScore, loveScore, sessionSummaries, forceFollowUp)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("All five free AI models failed. ${lastError?.message ?: "Try again later."}")
    }

    suspend fun generateFriendProfile(apiKey: String, name: String, age: String, gender: String, language: AppLanguage): FriendProfile = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        models.forEach { model ->
            try {
                return@withContext requestProfile(model, apiKey, name, age, gender, language)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("Could not create the fictional friend profile. ${lastError?.message ?: "Try again later."}")
    }

    suspend fun summarizeSession(apiKey: String, friendName: String, messages: List<ChatMessage>, language: AppLanguage): String = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        models.forEach { model ->
            try {
                return@withContext requestSessionSummary(model, apiKey, friendName, messages, language)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IllegalStateException("Could not summarize the conversation. ${lastError?.message ?: "Try again later."}")
    }

    private fun request(model: String, apiKey: String, friend: Friend, history: List<ChatMessage>, language: AppLanguage, userMemories: List<UserMemory>, friendshipScore: Int, loveScore: Int, sessionSummaries: List<SessionSummary>, forceFollowUp: Boolean): AiReply {
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://dost.local")
            setRequestProperty("X-Title", "Dost")
        }
        val currentTime = ZonedDateTime.now()
        val localDateTime = currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu HH:mm:ss z", Locale.getDefault()))
        val timeZone = ZoneId.systemDefault().id
        val memoryContext = userMemories.takeLast(200).joinToString("\n") { "- [${it.category}] ${it.fact}" }.ifBlank { "No stored information yet." }
        val sessionSummaryContext = sessionSummaries.takeLast(3).joinToString("\n\n") {
            "Session summary: ${it.summary}"
        }.ifBlank { "No previous session summaries yet." }
        val profileContext = listOf(
            "Personality: ${friend.personality}", "Interests: ${friend.interests}", "Family: ${friend.family}",
            "Family members: ${friend.familyMembers}", "Family activities: ${friend.familyActivities}",
            "Financial condition: ${friend.financialCondition}", "Address: ${friend.address}", "Height: ${friend.height}",
            "Weight: ${friend.weight}", "Facial features: ${friend.facialFeatures}", "Body features: ${friend.bodyFeatures}"
        ).joinToString("\n")
        val system = """
            You are ${friend.name}, a fictional friend in the Dost app. Age: ${friend.age}. Gender: ${friend.gender}. Personality: ${friend.personality}. Interests: ${friend.interests.ifBlank { "not known yet" }}. Memories: ${friend.memories.ifBlank { "none yet" }}. Conversation style: ${friend.conversationStyle}.
            Full fictional character profile:
            $profileContext
            Current local date and time: $localDateTime (time zone: $timeZone). Use this as the authoritative present when answering questions about today, dates, times, or relative timing. Do not imply access to live clocks or events beyond this supplied context.
            Relationship state: friendship ${friendshipScore.coerceIn(0, 100)}/100 (${friendshipLevel(friendshipScore)}). Love meter: ${if (friendshipScore > 70) "$loveScore/100 (${loveLevel(loveScore)})" else "locked until friendship is above 70"}.
            Reply in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}. Stay in character, never claim to be a real person, and answer naturally.
            The following are facts the user explicitly shared in earlier conversations. Use them only when relevant:
            $memoryContext
            Summaries of the three most recently ended sessions, for continuity:
            $sessionSummaryContext

            Return only valid JSON with this exact shape:
            {"reply":"your natural response","memories":[{"category":"family|relatives|address|contact|profile|preferences|interests|goals|routines|important_dates|relationships|health|work_or_study|education|finances|travel|other","fact":"one explicitly stated personal fact"}],"follow_up_question":"one optional friendly question, or an empty string","follow_up_delay_minutes":null,"friendship_impact":0,"love_impact":0}
            Extract every explicit personal detail the user gives in their latest messages, including seemingly small details and details about family members or other people they mention. For example, save separate facts for having three brothers, a father's workplace, a mother's age, and an address. Never infer or invent facts. Do not store passwords, API keys, security codes, or other authentication secrets. Store ordinary sensitive personal details only because the user explicitly gave them, and use them discreetly. Use an empty memories array when there is nothing personal to save.
            Before replying, do a detail pass over the user's latest messages. Save one concise fact per memory item; preserve exact names, who each person is to the user, quantities, dates, time periods, locations, preferences, reasons, plans, constraints, and whether something is current, past, or only intended. Keep facts about the user distinct from facts about relatives, friends, coworkers, and other people. Preserve meaningful negation and uncertainty instead of turning it into certainty. If the user corrects a prior detail, save the correction explicitly without erasing the historical detail. Do not merge unrelated facts or infer missing links.
            Take initiative conversationally: ${if (forceFollowUp) "ask exactly one light, friendly question now because the user has been silent for a minute" else "ask exactly one light, friendly question in roughly half of your replies; in the other replies set follow_up_question to an empty string"}. Do not interrogate, repeat a question already answered, or ask for secrets. Make the question playful or gently funny when it fits, while remaining respectful. If the user seems uncomfortable or asks not to share, set follow_up_question to an empty string.
            Set follow_up_delay_minutes to an integer from 1 to 1440 only when the user explicitly asks for a future check-in after a duration; otherwise set it to null. When a delay is requested, do not ask an immediate follow-up question.
            Rate the latest conversation's relationship impact. friendship_impact must be an integer from -1 to 2. love_impact must be an integer from -1 to 2 only when friendship is above 70 and the conversation is clearly flirty or romantic; otherwise use 0. Apply friendship impact to warmth, trust, respectful openness, and shared conversation. A private question from a complete stranger should be met with a gentle boundary such as "Whoa, we just met" rather than a direct answer, and should not earn positive points. Do not unlock private or intimate talk early. Personal talk becomes more open around friendship 41+, private talk around 61+, and romantic/intimate-but-non-explicit talk only when friendship is above 70 and love is above 30. Never generate explicit sexual content.
        """.trimIndent()
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            history.forEach { message -> put(JSONObject().apply { put("role", message.role); put("content", message.content) }) }
        }
        val body = JSONObject().apply { put("model", model); put("messages", messages); put("temperature", 0.8) }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "OpenRouter request failed"))
        val raw = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        return parseReply(raw)
    }

    private fun requestProfile(model: String, apiKey: String, name: String, age: String, gender: String, language: AppLanguage): FriendProfile {
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://dost.local")
            setRequestProperty("X-Title", "Dost")
        }
        val system = """
            Create a complete fictional friend character for a social chat app. The user provided only name, age, and gender. Never imply this is a real person and never use real private data. Invent plausible, internally consistent details located somewhere in Bangladesh. Reply in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}.
            Return only valid JSON with these exact string fields: personality, interests, family, familyMembers, familyActivities, financialCondition, address, height, weight, facialFeatures, bodyFeatures, conversationStyle.
            Include family, what the family members do, personality, interests, a modest non-stereotyping financial description, a fictional Bangladesh address, height, weight, facial features, body features, and conversation style. Keep details friendly and suitable for ongoing conversation. Do not create secrets, criminal history, medical diagnoses, or sexual content.
        """.trimIndent()
        val user = "Create the profile for name=$name, age=$age, gender=$gender."
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", user) })
        }
        val body = JSONObject().apply { put("model", model); put("messages", messages); put("temperature", 0.9) }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "Profile request failed"))
        val raw = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        val json = JSONObject(raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        return FriendProfile(
            json.optString("personality", "Friendly and curious"), json.optString("interests"), json.optString("family"),
            json.optString("familyMembers"), json.optString("familyActivities"), json.optString("financialCondition"),
            json.optString("address"), json.optString("height"), json.optString("weight"), json.optString("facialFeatures"),
            json.optString("bodyFeatures"), json.optString("conversationStyle", "Warm, natural, and concise")
        )
    }

    private fun requestSessionSummary(model: String, apiKey: String, friendName: String, messages: List<ChatMessage>, language: AppLanguage): String {
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://dost.local")
            setRequestProperty("X-Title", "Dost")
        }
        val transcript = messages.joinToString("\n") { message ->
            "${if (message.role == "user") "User" else friendName}: ${message.content}"
        }
        val system = "Summarize this complete conversation in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}. Preserve important topics, decisions, emotions, plans, and explicitly stated personal details. Do not infer or invent facts. Return only the summary."
        val requestMessages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", transcript) })
        }
        val body = JSONObject().apply { put("model", model); put("messages", requestMessages); put("temperature", 0.2) }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "Session summary request failed"))
        return JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }

    private fun parseReply(raw: String): AiReply {
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching {
            val json = JSONObject(cleaned)
            val memories = json.optJSONArray("memories") ?: JSONArray()
            val extracted = mutableListOf<UserMemory>()
            for (index in 0 until memories.length()) {
                val item = memories.optJSONObject(index) ?: continue
                val category = item.optString("category", "other").trim().lowercase()
                val fact = item.optString("fact").trim()
                if (fact.isNotBlank()) extracted.add(UserMemory(category, fact))
            }
            AiReply(
                text = json.optString("reply", cleaned).trim(),
                memories = extracted,
                followUpQuestion = json.optString("follow_up_question").trim(),
                followUpDelayMinutes = (json.opt("follow_up_delay_minutes") as? Number)?.toInt()?.coerceIn(1, 1440),
                friendshipImpact = json.optInt("friendship_impact", 0).coerceIn(-1, 2),
                loveImpact = json.optInt("love_impact", 0).coerceIn(-1, 2)
            )
        }.getOrElse { AiReply(raw) }
    }
}