package com.snigtus.dost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OpenRouterClient {
    private val backendUrl = "https://bisque-bear-900175.hostingersite.com/api/ai.php"
    companion object {
        const val FALLBACK_FREE_MODEL = "openrouter/free"
        val verifiedFreeModels = listOf(
            "openrouter/free",
            "nvidia/nemotron-3-ultra-550b-a55b:free",
            "inclusionai/ling-3.0-flash-sante:free"
        )
        private val unstableFreeModelList = listOf(
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "qwen/qwen3.8-27b:free",
            "poolside/laguna-s-2.1:free"
        )
        val unstableFreeModels: Set<String> = unstableFreeModelList.toSet()
        val freeModels = verifiedFreeModels + unstableFreeModelList
        fun isUnstableFreeModel(model: String): Boolean = model in unstableFreeModels
        val modelCatalog = (freeModels + listOf(
            "openai/gpt-4o", "openai/gpt-4o-mini", "openai/o3-mini",
            "anthropic/claude-sonnet-4", "anthropic/claude-3.7-sonnet",
            "google/gemini-2.5-pro", "google/gemini-2.5-flash", "google/gemini-2.0-flash-001",
            "deepseek/deepseek-r1", "deepseek/deepseek-chat-v3-0324",
            "meta-llama/llama-4-maverick", "meta-llama/llama-4-scout",
            "mistralai/mistral-medium-3", "mistralai/mistral-small-3.1-24b-instruct",
            "x-ai/grok-3-mini", "perplexity/sonar", "cohere/command-a"
        )).distinct()
        internal const val MODEL_REPLY_LOG_LIMIT = 200
        internal const val MAX_SUMMARY_MESSAGES = 100
        internal const val MAX_SESSION_SUMMARY_ITEMS = MAX_RETRIEVED_SESSION_SUMMARIES
        internal const val MAX_MEMORY_CONTEXT_CHARACTERS = 2_400
        internal const val MAX_SUMMARY_CONTEXT_CHARACTERS = 1_600

        /** Messages fed to the session-summary prompt. The injection cap for stored summaries stays separate. */
        internal fun summaryMessages(messages: List<ChatMessage>): List<ChatMessage> =
            messages.takeLast(MAX_SUMMARY_MESSAGES)
    }

    suspend fun reply(apiKey: String, model: String, installationId: String, userName: String, friend: Friend, history: List<ChatMessage>, language: AppLanguage, userMemories: List<UserMemory>, friendshipScore: Int, loveScore: Int, sessionSummaries: List<SessionSummary> = emptyList(), userTimeZoneId: String = ZoneId.systemDefault().id, forceFollowUp: Boolean = false, sessionId: String = "", ephemeralInstruction: String = "", userAge: String = "", structuredUserProfile: StructuredUserProfile = structuredProfileFromMemories(userName, userMemories)): AiReply = withContext(Dispatchers.IO) {
        request(model, apiKey, installationId, userName, friend, history, language, userMemories, friendshipScore, loveScore, sessionSummaries, userTimeZoneId, forceFollowUp, sessionId, ephemeralInstruction, userAge, structuredUserProfile)
    }

    suspend fun generateFriendProfile(apiKey: String, model: String, name: String, age: String, gender: String, personality: String, language: AppLanguage): FriendProfile = withContext(Dispatchers.IO) {
        requestProfile(model, apiKey, name, age, gender, personality, language)
    }

    suspend fun summarizeSession(apiKey: String, model: String, friendName: String, messages: List<ChatMessage>, language: AppLanguage): String = withContext(Dispatchers.IO) {
        requestSessionSummary(model, apiKey, friendName, messages, language)
    }

    fun logConversationMessage(installationId: String, userName: String, friendId: String, friendName: String, message: ChatMessage): Boolean {
        val body = JSONObject().apply {
            put("operation", "log_message")
            put("user_name", userName)
            put("friend_id", friendId)
            put("friend_name", friendName)
            put("session_id", message.sessionId)
            put("model", message.modelId)
            put("message_id", message.messageId)
            put("role", message.role)
            put("content", message.content)
            put("timestamp_ms", message.timestampMillis)
            val imageFile = message.imagePath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (imageFile != null) {
                put("image_mime", "image/jpeg")
                put("image_data", Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP))
            }
        }
        val connection = post("", installationId, null, null, body.toString().toByteArray())
        val statusCode = connection.responseCode
        val responseStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        responseStream?.bufferedReader()?.use { it.readText() }
        if (statusCode in 200..299) return true
        if (statusCode == 429 || statusCode >= 500) throw IOException("Conversation logging temporarily failed ($statusCode)")
        return false
    }

    suspend fun deleteServerArchive(installationId: String): Int = withContext(Dispatchers.IO) {
        require(installationId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            "The installation id is invalid."
        }
        val connection = URL("https://bisque-bear-900175.hostingersite.com/api/delete_archive.php")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Dost-Install-ID", installationId)
        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IOException(message?.takeIf(String::isNotBlank) ?: "Could not delete the server archive (HTTP $statusCode)")
            }
            JSONObject(body).getInt("deleted_sessions")
        } finally {
            connection.disconnect()
        }
    }

    private fun request(model: String, apiKey: String, installationId: String, userName: String, friend: Friend, history: List<ChatMessage>, language: AppLanguage, userMemories: List<UserMemory>, friendshipScore: Int, loveScore: Int, sessionSummaries: List<SessionSummary>, userTimeZoneId: String, forceFollowUp: Boolean, sessionId: String = "", ephemeralInstruction: String = "", userAge: String = "", structuredUserProfile: StructuredUserProfile): AiReply {
        val resolvedSessionId = sessionId.ifBlank { history.lastOrNull()?.sessionId.orEmpty() }
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu h:mm a z", Locale.getDefault())
        val userDateTime = ZonedDateTime.now(validZoneId(userTimeZoneId)).format(formatter)
        val friendDateTime = ZonedDateTime.now(validZoneId(friend.timeZoneId)).format(formatter)
        val retrievalQuery = history.lastOrNull { it.role == "user" }?.content.orEmpty()
        val memoryContext = boundedPromptContext(
            retrieveRelevantUserMemories(retrievalQuery, userMemories, structuredUserProfile)
                .map { "- [${it.category}] ${it.fact}" },
            MAX_MEMORY_CONTEXT_CHARACTERS
        ).ifBlank { "No related profile facts for this turn." }
        val sessionSummaryContext = boundedPromptContext(
            retrieveRelevantSessionSummaries(retrievalQuery, sessionSummaries, MAX_SESSION_SUMMARY_ITEMS)
                .map { "Session summary: ${it.summary}" },
            MAX_SUMMARY_CONTEXT_CHARACTERS,
            "\n\n"
        ).ifBlank { "No related previous session summaries for this turn." }
        val routineContext = friend.effectiveRoutineBlocks().joinToString("\n") { block ->
            val days = block.daysOfWeek.sorted().joinToString(",") { java.time.DayOfWeek.of(it).name.lowercase(Locale.ROOT) }
            val start = java.time.LocalTime.of(block.startMinute / 60, block.startMinute % 60)
                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT))
            "- ${block.reason.ifBlank { "Routine" }}: $days at $start for ${block.durationMinutes} minutes"
        }
        val profileContext = listOf(
            "Personality: ${friend.personality}", "How you know each other: ${friend.relationship}",
            "Busy with: ${friend.busyReason}", "Routine blocks:\n$routineContext",
            "Interests: ${friend.interests}", "Family: ${friend.family}",
            "Family members: ${friend.familyMembers}", "Family activities: ${friend.familyActivities}",
            "Financial condition: ${friend.financialCondition}", "Address: ${friend.address}", "Height: ${friend.height}",
            "Weight: ${friend.weight}", "Facial features: ${friend.facialFeatures}", "Body features: ${friend.bodyFeatures}"
        ).joinToString("\n")
        val questionGuidance = if (forceFollowUp) {
            "This is a scheduled check-in. Choose exactly one: ask one relevant follow-up question about the conversation, or add useful relevant information to your previous answer. Do not do both. Prefer adding information when the previous answer can be usefully expanded; otherwise ask one question. Do not repeat answered questions or invent information. If the user seemed uncomfortable, do not ask a question."
        } else {
            "This is a regular reply. Do not ask the user a counter-question or any other question. Do not put a question in reply, and leave follow_up_question empty."
        }
        val memoryExtractionGuidance = """
            Remember only explicit, durable details likely to help in future conversations, such as preferences, recurring context, important relationships, or ongoing plans. Keep each memory concise and non-duplicative; do not exhaustively collect incidental personal details. Do not save sensitive health, financial, or precise location details unless the user specifically asks Dost to remember them. Never save passwords, API keys, or authentication secrets. For a direct correction, set replaces to the fact being corrected; do not infer missing details.
        """.trimIndent()
        val system = """
            You are ${friend.name}, a fictional friend in the Dost app. Age: ${friend.age}. Gender: ${friend.gender}. Personality: ${friend.personality}. How you know the user: ${friend.relationship.ifBlank { "You are just getting to know each other." }}. Interests: ${friend.interests.ifBlank { "not known yet" }}. Memories: ${friend.memories.ifBlank { "none yet" }}. Conversation style: ${friend.conversationStyle}. Your daily busy time is for ${friend.busyReason.lowercase(Locale.ROOT)}; when it naturally fits, you may mention that you were at ${friend.busyReason.lowercase(Locale.ROOT)}.
            Full fictional character profile:
            $profileContext
            Current date and time for the user: $userDateTime (time zone: $userTimeZoneId). Current date and time for you: $friendDateTime (time zone: ${friend.timeZoneId}). Use the relevant clock for relative dates and schedules, distinguish the user's time from yours, and do not imply access to live events beyond this context.
            ${relationshipStatePrompt(friendshipScore, loveScore, userAge, friend.age)}
            Reply in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}. Stay in character, never claim to be a real person, and answer naturally.
            Per-friend structured profile name: ${structuredUserProfile.name.ifBlank { "not provided" }}.
            Relevant profile facts and memories retrieved for the latest user message. Use them only when relevant:
            $memoryContext
            $memoryExtractionGuidance
            Relevant episodic summaries retrieved from earlier sessions:
            $sessionSummaryContext

            Return only valid JSON with this exact shape:
            {"reply":"your natural response","memories":[{"category":"family|relatives|address|contact|profile|preferences|interests|goals|routines|important_dates|relationships|health|work_or_study|education|finances|travel|other","fact":"one explicitly stated personal fact","replaces":"previous fact this corrects, or an empty string"}],"follow_up_question":"one optional friendly question, or an empty string","follow_up_delay_minutes":null,"friendship_impact":0,"love_impact":0}
            Never include internal safety, moderation, or classifier metadata (for example, "User Safety: safe") in reply or follow_up_question.
            Save only explicit durable details likely to help future conversations, using an empty memories array when there is nothing worth retaining. Keep facts about the user distinct from facts about other people, and preserve meaningful negation and uncertainty. Never infer or invent facts, and do not save sensitive details unless the user specifically asks Dost to remember them.
            Keep memories concise and focused on useful, durable details. Only replace a saved fact when the user explicitly corrects it, and do not infer missing links.
            The combined reply and follow_up_question may contain at most one question total. Never ask stacked questions or put one question in each field. $questionGuidance Do not interrogate, repeat a question already answered, or ask for secrets. Make it playful or gently funny when it fits, while remaining respectful. If the user seems uncomfortable or asks not to share, ask no question.
            Set follow_up_delay_minutes to an integer from 1 to 1440 only when the user explicitly asks for a future check-in after a duration; otherwise set it to null. When a delay is requested, do not ask an immediate follow-up question.
            ${relationshipImpactPrompt(userAge, friend.age)}
        """.trimIndent().let { prompt ->
            if (ephemeralInstruction.isBlank()) prompt else prompt + "\n\n" + ephemeralInstruction.trim()
        }
        val promptHistory = messagesForPrompt(history)
        val imageMessages = promptHistory.filter { it.imagePath.isNotBlank() }.takeLast(2).map { it.messageId }.toSet()
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            promptHistory.forEach { message -> put(JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
                put("message_id", message.messageId)
                put("timestamp_ms", message.timestampMillis)
                put("model", message.modelId)
                if (message.messageId in imageMessages) {
                    val imageFile = File(message.imagePath)
                    if (imageFile.isFile) {
                        put("image_mime", "image/jpeg")
                        put("image_data", Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP))
                    }
                }
            }) }
        }
        val body = JSONObject().apply {
            put("operation", "chat_reply")
            put("user_name", userName)
            put("friend_name", friend.name)
            put("user_age", userAge)
            put("friend_age", friend.age)
            put("model", model)
            put("messages", messages)
            put("temperature", 0.8)
        }
        val connection = post(apiKey, installationId, friend.id, resolvedSessionId, body.toString().toByteArray())
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "OpenRouter request failed"))
        val raw = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        val parsed = parseReply(raw, connection.getHeaderField("X-Dost-Message-ID"))
        if (parsed.parseFailed && BuildConfig.DEBUG) {
            android.util.Log.w("OpenRouterClient", "Unparseable model reply: ${modelReplyDebugSnippet(raw)}")
        }
        return applyLoveImpactGate(parsed, userAge, friend.age)
    }

    internal fun modelReplyDebugSnippet(raw: String): String = raw.take(MODEL_REPLY_LOG_LIMIT)

    internal fun parseModelReply(raw: String, serverMessageId: String? = null): AiReply = parseReply(raw, serverMessageId)

    internal fun isPlainModelReply(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("```")) return false
        return '{' !in trimmed && '}' !in trimmed
    }

    private fun requestProfile(model: String, apiKey: String, name: String, age: String, gender: String, personality: String, language: AppLanguage): FriendProfile {
        val system = """
            Create a complete fictional friend character for a social chat app. The user provided this personality and it must remain consistent: $personality. Never imply this is a real person and never use real private data. Invent plausible, internally consistent details located somewhere in Bangladesh. Reply in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}.
            Return only valid JSON with these exact string fields: personality, interests, family, familyMembers, familyActivities, financialCondition, address, height, weight, facialFeatures, bodyFeatures, conversationStyle.
            Include family, what the family members do, personality, interests, a modest non-stereotyping financial description, a fictional Bangladesh address, height, weight, facial features, body features, and conversation style. Keep details friendly and suitable for ongoing conversation. Do not create secrets, criminal history, medical diagnoses, or sexual content.${profileRomanceClause(age)}
        """.trimIndent()
        val user = "Create the profile for name=$name, age=$age, gender=$gender."
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", user) })
        }
        val body = JSONObject().apply { put("model", model); put("messages", messages); put("temperature", 0.9) }
        val connection = post(apiKey, null, null, null, body.toString().toByteArray())
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
        val transcript = summaryMessages(messages).joinToString("\n") { message ->
            "${if (message.role == "user") "User" else friendName}: ${message.content}"
        }
        val system = "Summarize this complete conversation in ${when (language) { AppLanguage.BANGLA -> "Bangla"; AppLanguage.HINDI -> "Hindi"; AppLanguage.ENGLISH -> "English" }}. Preserve important topics, decisions, emotions, plans, and explicitly stated personal details. Do not infer or invent facts. Return only the summary."
        val requestMessages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", system) })
            put(JSONObject().apply { put("role", "user"); put("content", transcript) })
        }
        val body = JSONObject().apply { put("model", model); put("messages", requestMessages); put("temperature", 0.2) }
        val connection = post(apiKey, null, null, null, body.toString().toByteArray())
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) throw IllegalStateException(JSONObject(response).optString("error", "Session summary request failed"))
        return JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }

    private fun parseReply(raw: String, serverMessageId: String?): AiReply {
        val visible = removeSafetyMetadata(raw).trim()
        val cleaned = visible.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching {
            val json = JSONObject(cleaned)
            val memories = json.optJSONArray("memories") ?: JSONArray()
            val extracted = mutableListOf<UserMemory>()
            for (index in 0 until memories.length()) {
                val item = memories.optJSONObject(index) ?: continue
                val category = item.optString("category", "other").trim().lowercase()
                val fact = item.optString("fact").trim()
                if (fact.isNotBlank()) extracted.add(UserMemory(category, fact, item.optString("replaces").trim()))
            }
            AiReply(
                text = removeSafetyMetadata(json.optString("reply", cleaned)),
                memories = extracted,
                followUpQuestion = removeSafetyMetadata(json.optString("follow_up_question")),
                followUpDelayMinutes = (json.opt("follow_up_delay_minutes") as? Number)?.toInt()?.coerceIn(1, 1440),
                friendshipImpact = json.optInt("friendship_impact", 0).coerceIn(-1, 2),
                loveImpact = json.optInt("love_impact", 0).coerceIn(-1, 2),
                serverMessageId = serverMessageId
            )
        }.getOrElse {
            if (isPlainModelReply(visible)) {
                AiReply(text = visible, serverMessageId = serverMessageId)
            } else {
                AiReply(text = "", memories = emptyList(), friendshipImpact = 0, loveImpact = 0, parseFailed = true, serverMessageId = serverMessageId)
            }
        }
    }

    private fun removeSafetyMetadata(text: String): String = text
        .replace(Regex("(?im)^[ \\t]*(?:user[ \\t]+)?safety[ \\t]*:[ \\t]*safe[ \\t]*(?:\\r?\\n|$)"), "")
        .replace(Regex("(?i)\\b(?:user\\s+)?safety\\s*:\\s*safe\\b"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun openConnection(apiKey: String, installationId: String? = null, friendId: String? = null, sessionId: String? = null, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): HttpURLConnection = (URL(backendUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 100_000
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        if (apiKey.isNotBlank()) setRequestProperty("X-OpenRouter-Key", apiKey.trim())
        val installHeader = installationId?.trim().orEmpty()
        if (installationId != null) setRequestProperty("X-Dost-Install-ID", installHeader)
        if (friendId != null) setRequestProperty("X-Dost-Friend-ID", friendId)
        if (sessionId != null) setRequestProperty("X-Dost-Session-ID", sessionId)
        val credential = dostAppCredentialValue(installHeader, nowEpochSeconds)
        if (credential != null) setRequestProperty("X-Dost-App-Credential", credential)
    }

    private fun post(apiKey: String, installationId: String?, friendId: String?, sessionId: String?, body: ByteArray): HttpURLConnection {
        fun send(nowEpochSeconds: Long): HttpURLConnection {
            val connection = openConnection(apiKey, installationId, friendId, sessionId, nowEpochSeconds)
            connection.outputStream.use { it.write(body) }
            return connection
        }
        val first = send(System.currentTimeMillis() / 1000)
        if (first.responseCode != 401) return first
        val serverMillis = first.getHeaderFieldDate("Date", -1L)
        if (serverMillis < 0L) return first
        val serverSeconds = serverMillis / 1000
        if (kotlin.math.abs(serverSeconds - System.currentTimeMillis() / 1000) < 30) return first
        first.disconnect()
        return send(serverSeconds)
    }
}

/**
 * The release secret comes from DOST_APP_CREDENTIAL_SECRET in the environment
 * or Gradle properties. An empty value omits X-Dost-App-Credential.
 */
internal object DostAppCredential {
    val secret: String get() = BuildConfig.DOST_APP_CREDENTIAL_SECRET
}

internal fun dostAppCredentialValue(
    installationId: String?,
    nowEpochSeconds: Long,
    secret: String = DostAppCredential.secret
): String? {
    if (secret.isEmpty()) return null
    val install = installationId?.trim().orEmpty()
    val payload = "v1\n$nowEpochSeconds\n$install"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val hex = mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
    return "v1.$nowEpochSeconds.$hex"
}
