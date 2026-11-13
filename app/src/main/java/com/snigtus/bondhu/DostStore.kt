package com.snigtus.dost

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
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
    private val chatImagesDirectory = File(context.filesDir, "chat_images").apply { mkdirs() }

    init {
        preferences.edit().remove("openrouter_key").apply()
    }

    fun hasUserProfile(): Boolean = preferences.getBoolean("user_profile_saved", false) || runCatching {
        JSONArray(preferences.getString("friends", "[]")).length() > 0
    }.getOrDefault(false)

    /** Saved profile whose age meets [MINIMUM_ACCOUNT_AGE]. Friends alone do not count. */
    fun hasEligibleAccount(): Boolean =
        preferences.getBoolean("user_profile_saved", false) && accountAgeResult(userAge()) == AccountAgeResult.VALID

    fun authenticationRequired(): Boolean = preferences.getBoolean("authentication_required", false)

    fun authenticationAccountId(): String = preferences.getString("authentication_account_id", "") ?: ""

    fun markAuthenticationRequired() {
        preferences.edit().putBoolean("authentication_required", true).apply()
    }

    fun bindAuthenticationAccount(accountId: String) {
        if (accountId.isBlank()) return
        preferences.edit().putString("authentication_account_id", accountId).apply()
    }

    /** Writes love scores back as 0 when romance is locked for this user or friend. */
    fun clearLockedLoveScores() {
        val age = userAge()
        val friends = loadFriends()
        if (friends.any { !romanceAllowed(age, it.age) && it.loveScore != 0 }) {
            saveFriends(friends)
        }
    }
    fun saveUserProfile(name: String, email: String, age: String, gender: String, accountId: String? = null) {
        val editor = preferences.edit()
            .putBoolean("user_profile_saved", true)
            .putString("user_name", name)
            .putString("user_email", email)
            .putString("user_age", age)
            .putString("user_gender", gender)
        if (!accountId.isNullOrBlank()) editor.putString("authentication_account_id", accountId)
        editor.apply()
    }
    fun userName(): String = preferences.getString("user_name", "") ?: ""
    fun userEmail(): String = preferences.getString("user_email", "") ?: ""
    fun userAge(): String = preferences.getString("user_age", "") ?: ""
    fun userGender(): String = preferences.getString("user_gender", "") ?: ""
    fun firebaseAccountId(): String = preferences.getString("firebase_account_id", "") ?: ""
    fun linkFirebaseAccount(uid: String) {
        if (uid.isNotBlank()) preferences.edit().putString("firebase_account_id", uid).apply()
    }
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
    fun anonymousTelemetryEnabled(): Boolean = preferences.getBoolean("anonymous_telemetry_enabled", false)
    fun setAnonymousTelemetryEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("anonymous_telemetry_enabled", enabled).apply()
    }

    fun exportLocalData(): JSONObject {
        val backupFriends = JSONArray()
        loadFriends().forEach { friend ->
            val backupMessages = JSONArray()
            loadMessages(friend.id).forEach { message ->
                val item = JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                    put("category", message.category)
                    put("sessionId", message.sessionId)
                    put("timestampMillis", message.timestampMillis)
                    put("messageId", message.messageId)
                    put("modelId", message.modelId)
                }
                val image = message.imagePath.takeIf(String::isNotBlank)?.let(::localChatImage)
                    ?.takeIf { it.isFile && it.length() in 1..MAX_BACKUP_IMAGE_BYTES }
                    ?.let { runCatching { it.readBytes() }.getOrNull() }
                    ?.takeIf(::isJpeg)
                if (image != null) {
                    item.put("imageData", Base64.encodeToString(image, Base64.NO_WRAP))
                }
                backupMessages.put(item)
            }
            val memories = JSONArray().apply {
                loadUserMemories(friend.id).forEach { memory ->
                    put(JSONObject().put("category", memory.category).put("fact", memory.fact))
                }
            }
            val summaries = JSONArray().apply {
                loadSessionSummaries(friend.id).forEach { summary ->
                    put(JSONObject().apply {
                        put("sessionId", summary.sessionId)
                        put("startedAtMillis", summary.startedAtMillis)
                        put("endedAtMillis", summary.endedAtMillis)
                        put("summary", summary.summary)
                    })
                }
            }
            val events = JSONArray().apply {
                loadPlannedEvents(friend.id).forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id)
                        put("description", event.description)
                        put("dueAtMillis", event.dueAtMillis)
                        put("confidence", event.confidence)
                        put("followUpRequested", event.followUpRequested)
                        put("state", event.state.name)
                    })
                }
            }
            val sharedMoments = sharedMomentsToJson(loadSharedMoments(friend.id))
            backupFriends.put(JSONObject().apply {
                put("profile", friendToJson(friend, userAge()))
                put("memories", memories)
                put("summaries", summaries)
                put("events", events)
                put("relationshipOutcomes", JSONArray(loadRelationshipOutcomeIds(friend.id)))
                put("relationshipFingerprints", JSONArray(loadRelationshipOutcomeFingerprints(friend.id)))
                put("sharedMoments", sharedMoments)
                put("messages", backupMessages)
                put("currentSessionId", currentSessionId(friend.id) ?: "")
            })
        }
        return JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("version", BACKUP_VERSION)
            put("profile", JSONObject().apply {
                put("name", userName())
                put("email", userEmail())
                put("age", userAge())
                put("gender", userGender())
            })
            put("settings", JSONObject().apply {
                put("language", when (language()) {
                    AppLanguage.BANGLA -> "bn"
                    AppLanguage.HINDI -> "hi"
                    AppLanguage.ENGLISH -> "en"
                })
                put("timeZone", userTimeZoneId())
                put("model", aiModel())
                put("proactiveEnabled", proactiveEnabled())
                put("proactiveCadenceMinutes", proactiveCadenceMinutes())
                put("automaticFollowUpMinutes", automaticFollowUpMinutes())
                put("quietHoursStartMinute", quietHoursStartMinute())
                put("quietHoursEndMinute", quietHoursEndMinute())
            })
            put("friends", backupFriends)
        }
    }

    fun importLocalData(backup: JSONObject) {
        require(backup.optString("format") == BACKUP_FORMAT && backup.optInt("version", -1) == BACKUP_VERSION) {
            "This is not a supported Dost backup."
        }
        val profile = backup.getJSONObject("profile")
        val name = backupString(profile, "name", 200).trim()
        val email = backupString(profile, "email", 320).trim()
        val age = backupString(profile, "age", 3).trim()
        val gender = backupString(profile, "gender", 100).trim()
        require(name.isNotEmpty() && email.isNotEmpty() && gender.isNotEmpty()
            && accountAgeResult(age) == AccountAgeResult.VALID
        ) { "The backup profile is invalid." }

        val settings = backup.getJSONObject("settings")
        val language = backupString(settings, "language", 2)
        require(language in setOf("bn", "hi", "en")) { "The backup language is invalid." }
        val timeZone = backupString(settings, "timeZone", 100)
        val parsedTimeZone = runCatching { ZoneId.of(timeZone).id }.getOrNull()
        require(parsedTimeZone != null) { "The backup timezone is invalid." }
        val model = backupString(settings, "model", 160)
        require(model.matches(Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)?"))) {
            "The backup model is invalid."
        }
        val proactiveEnabled = settings.optBoolean("proactiveEnabled", true)
        val proactiveCadenceMinutes = settings.optInt("proactiveCadenceMinutes", 180)
        val automaticFollowUpMinutes = settings.optInt("automaticFollowUpMinutes", 30)
        val quietHoursStartMinute = settings.optInt("quietHoursStartMinute", 22 * 60)
        val quietHoursEndMinute = settings.optInt("quietHoursEndMinute", 8 * 60)
        require(proactiveCadenceMinutes in 30..1440 && automaticFollowUpMinutes in 5..120
            && quietHoursStartMinute in 0..1439 && quietHoursEndMinute in 0..1439
            && quietHoursStartMinute != quietHoursEndMinute
        ) { "The backup proactive-message settings are invalid." }

        val friendItems = backup.getJSONArray("friends")
        require(friendItems.length() <= MAX_BACKUP_FRIENDS) { "The backup has too many friends." }
        val importedFriends = ArrayList<ImportedBackupFriend>(friendItems.length())
        val friendIds = HashSet<String>()
        for (index in 0 until friendItems.length()) {
            val item = friendItems.getJSONObject(index)
            val friendProfile = item.getJSONObject("profile")
            val friend = friendFromJson(friendProfile)
            require(isBackupUuid(friend.id) && friendIds.add(friend.id)
                && friend.name.isNotBlank() && friendAgeResult(friend.age) == AccountAgeResult.VALID
                && friend.gender.isNotBlank()
            ) { "The backup contains an invalid friend." }
            require(
                listOf(
                    friend.name, friend.gender, friend.personality, friend.interests, friend.memories,
                    friend.conversationStyle, friend.family, friend.familyMembers, friend.familyActivities,
                    friend.financialCondition, friend.address, friend.height, friend.weight,
                    friend.facialFeatures, friend.bodyFeatures, friend.mood, friend.photoUri,
                    friend.busyReason, friend.catalogId
                ).all { it.length <= 5_000 }
                    && friend.busyStartHour in 0..23
                    && friend.busyDurationHours in 4..8
                    && runCatching { ZoneId.of(friend.timeZoneId) }.isSuccess
                    && friend.busyReplyDelayMinutes in 0..120
                    && friend.routineBlocks.size <= 12
                    && friend.routineBlocks.all {
                        it.id.isNotBlank() && it.id.length <= 64
                            && it.reason.length <= 80
                            && it.startMinute in 0..1439
                            && it.durationMinutes in 1..1440
                            && it.daysOfWeek.isNotEmpty()
                            && it.daysOfWeek.all { day -> day in 1..7 }
                    }
            ) { "The backup contains an invalid friend profile." }
            val memoriesJson = item.getJSONArray("memories")
            require(memoriesJson.length() <= MAX_STORED_USER_MEMORIES) { "The backup has too many memories." }
            val memories = List(memoriesJson.length()) { memoryIndex ->
                val memory = memoriesJson.getJSONObject(memoryIndex)
                UserMemory(
                    backupString(memory, "category", 200).trim(),
                    backupString(memory, "fact", MAX_BACKUP_TEXT_LENGTH).trim()
                ).also { require(it.category.isNotEmpty() && it.fact.isNotEmpty()) { "The backup contains an invalid memory." } }
            }
            val summariesJson = item.getJSONArray("summaries")
            require(summariesJson.length() <= 10) { "The backup has too many session summaries." }
            val summaries = List(summariesJson.length()) { summaryIndex ->
                val summary = summariesJson.getJSONObject(summaryIndex)
                val sessionId = backupString(summary, "sessionId", 36)
                require(isBackupUuid(sessionId)) { "The backup contains an invalid session summary." }
                SessionSummary(
                    sessionId,
                    summary.getLong("startedAtMillis"),
                    summary.getLong("endedAtMillis"),
                    backupString(summary, "summary", MAX_BACKUP_TEXT_LENGTH)
                ).also { require(it.startedAtMillis > 0 && it.endedAtMillis >= it.startedAtMillis) { "The backup contains invalid session dates." } }
            }
            val eventsJson = item.optJSONArray("events") ?: JSONArray()
            require(eventsJson.length() <= 100) { "The backup has too many planned events." }
            val events = List(eventsJson.length()) { eventIndex ->
                val event = eventsJson.getJSONObject(eventIndex)
                val id = backupString(event, "id", 64)
                val description = backupString(event, "description", 500).trim()
                val confidence = backupString(event, "confidence", 20)
                val state = runCatching { PlannedEventState.valueOf(backupString(event, "state", 20)) }
                    .getOrElse { throw IllegalArgumentException("The backup contains an invalid planned event.") }
                require(isBackupUuid(id) && description.isNotBlank()
                    && confidence in setOf("confirmed", "uncertain")
                    && event.getLong("dueAtMillis") > 0L
                ) { "The backup contains an invalid planned event." }
                PlannedEvent(
                    id = id,
                    description = description,
                    dueAtMillis = event.getLong("dueAtMillis"),
                    confidence = confidence,
                    followUpRequested = event.optBoolean("followUpRequested", true),
                    state = state
                )
            }
            require(events.map { it.id }.distinct().size == events.size) {
                "The backup contains duplicate planned events."
            }
            val outcomesJson = item.optJSONArray("relationshipOutcomes") ?: JSONArray()
            require(outcomesJson.length() <= MAX_RELATIONSHIP_OUTCOMES) {
                "The backup has too many relationship outcomes."
            }
            val relationshipOutcomes = List(outcomesJson.length()) { outcomeIndex ->
                outcomesJson.getString(outcomeIndex)
            }
            require(relationshipOutcomes.all(::isBackupUuid)
                && relationshipOutcomes.distinct().size == relationshipOutcomes.size
            ) { "The backup contains invalid relationship outcomes." }
            val fingerprintsJson = item.optJSONArray("relationshipFingerprints") ?: JSONArray()
            require(fingerprintsJson.length() <= MAX_RELATIONSHIP_FINGERPRINTS) {
                "The backup has too many relationship fingerprints."
            }
            val relationshipFingerprints = List(fingerprintsJson.length()) { fingerprintIndex ->
                fingerprintsJson.getString(fingerprintIndex)
            }
            require(relationshipFingerprints.all(::isBackupUuid)
                && relationshipFingerprints.distinct().size == relationshipFingerprints.size
            ) { "The backup contains invalid relationship fingerprints." }
            val momentsJson = item.optJSONArray("sharedMoments") ?: JSONArray()
            require(momentsJson.length() <= MAX_SHARED_MOMENTS) { "The backup has too many shared moments." }
            val sharedMoments = List(momentsJson.length()) { momentIndex ->
                val moment = momentsJson.getJSONObject(momentIndex)
                val id = backupString(moment, "id", 36)
                val title = backupString(moment, "title", 200)
                val detail = backupString(moment, "detail", 2_000)
                val kind = backupString(moment, "kind", 40)
                val stageIndex = if (moment.has("stageIndex") && !moment.isNull("stageIndex")) moment.getInt("stageIndex") else null
                require(isBackupUuid(id) && title.isNotBlank() && kind.isNotBlank()
                    && moment.getLong("createdAtMillis") > 0L
                    && (stageIndex == null || stageIndex in 0..9)
                ) { "The backup contains an invalid shared moment." }
                SharedMoment(
                    id = id,
                    title = title,
                    detail = detail,
                    createdAtMillis = moment.getLong("createdAtMillis"),
                    kind = kind,
                    pinned = moment.optBoolean("pinned", false),
                    stageIndex = stageIndex
                )
            }
            require(sharedMoments.map { it.id }.distinct().size == sharedMoments.size) {
                "The backup contains duplicate shared moments."
            }
            val currentSessionId = backupString(item, "currentSessionId", 36)
            require(currentSessionId.isEmpty() || isBackupUuid(currentSessionId)) {
                "The backup contains an invalid current session."
            }
            val messagesJson = item.getJSONArray("messages")
            val messages = List(messagesJson.length()) { messageIndex ->
                val message = messagesJson.getJSONObject(messageIndex)
                val role = backupString(message, "role", 20)
                val content = backupString(message, "content", MAX_BACKUP_TEXT_LENGTH)
                val messageId = backupString(message, "messageId", 36)
                val sessionId = backupString(message, "sessionId", 36)
                val category = backupString(message, "category", 100)
                val modelId = backupString(message, "modelId", 160)
                require(role in setOf("user", "assistant") && content.length <= 25_000
                    && isBackupUuid(messageId) && (sessionId.isEmpty() || isBackupUuid(sessionId))
                ) { "The backup contains an invalid message." }
                val imageBytes = if (message.has("imageData")) {
                    val encoded = backupString(message, "imageData", MAX_BACKUP_IMAGE_BASE64_LENGTH)
                    val decoded = Base64.decode(encoded, Base64.DEFAULT)
                    require(decoded.size in 1..MAX_BACKUP_IMAGE_BYTES && isJpeg(decoded)) {
                        "The backup contains an invalid chat image."
                    }
                    decoded
                } else {
                    null
                }
                ImportedBackupMessage(
                    ChatMessage(
                        role = role,
                        content = content,
                        category = category,
                        sessionId = sessionId,
                        timestampMillis = message.getLong("timestampMillis"),
                        messageId = messageId,
                        modelId = modelId
                    ),
                    imageBytes
                )
            }
            importedFriends += ImportedBackupFriend(
                friend,
                memories,
                summaries,
                events,
                relationshipOutcomes,
                relationshipFingerprints,
                sharedMoments,
                messages,
                currentSessionId
            )
        }

        val stagedImages = File(chatImagesDirectory, ".restore-${java.util.UUID.randomUUID()}").apply {
            require(mkdirs()) { "Could not prepare chat images for import." }
        }
        try {
            importedFriends.forEach { imported ->
                imported.messages.forEach { entry ->
                    entry.imageBytes?.let { image ->
                        val imageFile = File(stagedImages, "${java.util.UUID.randomUUID()}.jpg")
                        imageFile.writeBytes(image)
                        entry.message = entry.message.copy(imagePath = File(chatImagesDirectory, imageFile.name).absolutePath)
                    }
                }
            }
            messagesDirectory.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach {
                require(it.delete()) { "Could not replace the existing transcripts." }
            }
            chatImagesDirectory.listFiles()?.filter { it != stagedImages }?.forEach {
                require(it.deleteRecursively()) { "Could not replace the existing chat images." }
            }
            stagedImages.listFiles()?.forEach { image ->
                require(image.renameTo(File(chatImagesDirectory, image.name))) { "Could not restore a chat image." }
            }
            importedFriends.forEach { imported ->
                require(writeMessagesFile(messageFile(imported.friend.id), messagesToJson(imported.messages.map { it.message }))) {
                    "Could not restore a transcript."
                }
            }
            val savedFriends = importedFriends.map { it.friend }
            val editor = preferences.edit()
            preferences.all.keys.filterNot { it == "installation_id" || it == "openrouter_key_encrypted" }
                .forEach(editor::remove)
            editor.putBoolean("user_profile_saved", true)
                .putString("user_name", name)
                .putString("user_email", email)
                .putString("user_age", age)
                .putString("user_gender", gender)
                .putString("user_time_zone", parsedTimeZone)
                .putString("language", language)
                .putString("ai_model", model)
                .putBoolean("proactive_enabled", proactiveEnabled)
                .putInt("proactive_cadence_minutes", proactiveCadenceMinutes)
                .putInt("automatic_follow_up_minutes", automaticFollowUpMinutes)
                .putInt("quiet_hours_start_minute", quietHoursStartMinute)
                .putInt("quiet_hours_end_minute", quietHoursEndMinute)
                .putString("friends", JSONArray().apply {
                    savedFriends.forEach { put(friendToJson(it, age)) }
                }.toString())
            importedFriends.forEach { imported ->
                val friendId = imported.friend.id
                editor.putString("user_memories_$friendId", JSONArray().apply {
                    imported.memories.forEach { put(JSONObject().put("category", it.category).put("fact", it.fact)) }
                }.toString())
                editor.putString("session_summaries_$friendId", JSONArray().apply {
                    imported.summaries.forEach { summary ->
                        put(JSONObject().apply {
                            put("sessionId", summary.sessionId)
                            put("startedAtMillis", summary.startedAtMillis)
                            put("endedAtMillis", summary.endedAtMillis)
                            put("summary", summary.summary)
                        })
                    }
                }.toString())
                editor.putString("planned_events_$friendId", JSONArray().apply {
                    imported.events.forEach { event ->
                        put(JSONObject().apply {
                            put("id", event.id)
                            put("description", event.description)
                            put("dueAtMillis", event.dueAtMillis)
                            put("confidence", event.confidence)
                            put("followUpRequested", event.followUpRequested)
                            put("state", event.state.name)
                        })
                    }
                }.toString())
                editor.putString("relationship_outcomes_$friendId", JSONArray(imported.relationshipOutcomes).toString())
                editor.putString("relationship_fingerprints_$friendId", JSONArray(imported.relationshipFingerprints).toString())
                editor.putString("shared_moments_$friendId", sharedMomentsToJson(imported.sharedMoments).toString())
                imported.currentSessionId.takeIf(String::isNotEmpty)?.let {
                    editor.putString("current_session_$friendId", it)
                }
                imported.messages.forEach { entry -> editor.putBoolean("server_logged_${entry.message.messageId}", true) }
            }
            require(editor.commit()) { "Could not save the imported profile." }
        } finally {
            stagedImages.deleteRecursively()
        }
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
            friendFromJson(json.getJSONObject(index))
        }
    }.getOrDefault(mutableListOf())

    fun updateFriend(friendId: String, transform: (Friend) -> Friend): Friend? = synchronized(preferences) {
        val friends = loadFriends()
        val index = friends.indexOfFirst { it.id == friendId }
        if (index < 0) return null
        val updated = transform(friends[index])
        friends[index] = updated
        saveFriends(friends)
        updated
    }

    internal fun applyRelationshipOutcome(
        friendId: String,
        sessionId: String,
        fingerprint: String?,
        transform: (Friend) -> Friend
    ): RelationshipApplication? = synchronized(preferences) {
        require(isBackupUuid(sessionId)) { "The relationship outcome session is invalid." }
        val outcomes = loadRelationshipOutcomeIds(friendId)
        val fingerprints = loadRelationshipOutcomeFingerprints(friendId)
        val friends = loadFriends()
        val index = friends.indexOfFirst { it.id == friendId }
        if (index < 0) return null
        if (!shouldApplyRelationshipOutcome(outcomes, sessionId)) return RelationshipApplication(friends[index], false)

        val previous = friends[index]
        val nextOutcomes = (outcomes + sessionId).takeLast(MAX_RELATIONSHIP_OUTCOMES)
        if (!shouldApplyRelationshipOutcome(outcomes, fingerprints, sessionId, fingerprint)) {
            check(
                preferences.edit()
                    .putString("relationship_outcomes_$friendId", JSONArray(nextOutcomes).toString())
                    .commit()
            ) { "Could not save the relationship outcome." }
            return RelationshipApplication(previous, false)
        }
        val updated = transform(previous)
        friends[index] = updated
        val editor = preferences.edit()
            .putString("friends", friendsToJson(friends).toString())
            .putString("relationship_outcomes_$friendId", JSONArray(nextOutcomes).toString())
        if (fingerprint != null) {
            val nextFingerprints = (fingerprints + fingerprint).takeLast(MAX_RELATIONSHIP_FINGERPRINTS)
            editor.putString("relationship_fingerprints_$friendId", JSONArray(nextFingerprints).toString())
        }
        val previousStage = friendshipStageIndex(previous.friendshipScore)
        val nextStage = friendshipStageIndex(updated.friendshipScore)
        if (nextStage > previousStage) {
            val moment = SharedMoment(
                id = stableIdempotencyKey("relationship-stage:$friendId:$sessionId:$nextStage"),
                title = "Relationship stage",
                detail = "A conversation helped your friendship reach a new stage.",
                createdAtMillis = System.currentTimeMillis(),
                kind = "relationship_stage",
                stageIndex = nextStage
            )
            val moments = boundedSharedMoments(loadSharedMoments(friendId).filterNot { it.id == moment.id } + moment)
            editor.putString("shared_moments_$friendId", sharedMomentsToJson(moments).toString())
        }
        check(editor.commit()) { "Could not save the relationship update." }
        RelationshipApplication(updated, true)
    }

    fun loadRelationshipOutcomeIds(friendId: String): List<String> = runCatching {
        val json = JSONArray(preferences.getString("relationship_outcomes_$friendId", "[]"))
        List(json.length()) { index -> json.getString(index) }
    }.getOrDefault(emptyList())

    fun loadRelationshipOutcomeFingerprints(friendId: String): List<String> = runCatching {
        val json = JSONArray(preferences.getString("relationship_fingerprints_$friendId", "[]"))
        List(json.length()) { index -> json.getString(index) }
    }.getOrDefault(emptyList())

    fun saveFriends(friends: List<Friend>) = synchronized(preferences) {
        preferences.edit().putString("friends", friendsToJson(friends).toString()).apply()
    }

    private fun friendsToJson(friends: List<Friend>): JSONArray = JSONArray().apply {
        friends.forEach { friend -> put(friendToJson(friend, userAge())) }
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

    private fun friendFromJson(item: JSONObject): Friend = Friend(
        item.getString("id"),
        item.getString("name"),
        item.getString("age"),
        item.getString("gender"),
        item.optString("personality", "Friendly and curious"),
        item.optString("interests"),
        item.optString("memories"),
        item.optString("conversationStyle", "Warm, natural, and concise"),
        item.optString("family"),
        item.optString("familyMembers"),
        item.optString("familyActivities"),
        item.optString("financialCondition"),
        item.optString("address"),
        item.optString("height"),
        item.optString("weight"),
        item.optString("facialFeatures"),
        item.optString("bodyFeatures"),
        item.optInt("friendshipScore", 0).coerceIn(0, 100),
        item.optInt("loveScore", 0).coerceIn(0, 100),
        item.optString("mood"),
        item.optString("photoUri"),
        item.optInt("busyStartHour", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 8 else 9),
        item.optInt("busyDurationHours", if ((item.optString("age").toIntOrNull() ?: 18) < 18) 6 else 8),
        item.optString("busyReason", if ((item.optString("age").toIntOrNull() ?: 18) < 18) "School" else "Work"),
        validZoneId(item.optString("timeZoneId", ZoneId.systemDefault().id)).id,
        item.optString("catalogId"),
        item.optBoolean("profilePending", false),
        item.optString("greeting"),
        item.optString("relationship")
    ).copy(
        routineBlocks = item.optJSONArray("routineBlocks")?.let { blocks ->
            List(blocks.length().coerceAtMost(12)) { index ->
                routineBlockFromJson(blocks.getJSONObject(index))
            }
        }.orEmpty(),
        busyReplyBehavior = runCatching { BusyReplyBehavior.valueOf(item.optString("busyReplyBehavior", "WAIT")) }
            .getOrDefault(BusyReplyBehavior.WAIT),
        busyReplyDelayMinutes = item.optInt("busyReplyDelayMinutes", 30).coerceIn(0, 120),
        favorite = item.optBoolean("favorite", false)
    )

    private fun friendToJson(friend: Friend, userAge: String): JSONObject = JSONObject().apply {
        put("id", friend.id); put("name", friend.name); put("age", friend.age); put("gender", friend.gender)
        put("personality", friend.personality); put("interests", friend.interests); put("memories", friend.memories); put("conversationStyle", friend.conversationStyle)
        put("family", friend.family); put("familyMembers", friend.familyMembers); put("familyActivities", friend.familyActivities)
        put("financialCondition", friend.financialCondition); put("address", friend.address); put("height", friend.height); put("weight", friend.weight)
        put("facialFeatures", friend.facialFeatures); put("bodyFeatures", friend.bodyFeatures)
        put("friendshipScore", friend.friendshipScore); put("loveScore", friend.withLoveMeterGated(userAge).loveScore)
        put("mood", friend.mood)
        put("photoUri", friend.photoUri)
        put("busyStartHour", friend.busyStartHour); put("busyDurationHours", friend.busyDurationHours)
        put("busyReason", friend.busyReason)
        put("timeZoneId", friend.timeZoneId)
        put("catalogId", friend.catalogId)
        put("profilePending", friend.profilePending)
        put("greeting", friend.greeting)
        put("relationship", friend.relationship)
        put("favorite", friend.favorite)
        put("routineBlocks", JSONArray().apply {
            friend.routineBlocks.forEach { block ->
                put(JSONObject().apply {
                    put("id", block.id)
                    put("reason", block.reason)
                    put("startMinute", block.startMinute)
                    put("durationMinutes", block.durationMinutes)
                    put("daysOfWeek", JSONArray().apply { block.daysOfWeek.sorted().forEach(::put) })
                })
            }
        })
        put("busyReplyBehavior", friend.busyReplyBehavior.name)
        put("busyReplyDelayMinutes", friend.busyReplyDelayMinutes.coerceIn(0, 120))
    }

    private fun routineBlockFromJson(item: JSONObject): RoutineBlock {
        val days = item.optJSONArray("daysOfWeek")?.let { values ->
            buildSet {
                for (index in 0 until values.length()) {
                    values.optInt(index).takeIf { it in 1..7 }?.let(::add)
                }
            }
        }.orEmpty()
        return RoutineBlock(
            id = item.optString("id").takeIf(String::isNotBlank) ?: java.util.UUID.randomUUID().toString(),
            reason = item.optString("reason"),
            startMinute = item.optInt("startMinute", 9 * 60).coerceIn(0, 1439),
            durationMinutes = item.optInt("durationMinutes", 8 * 60).coerceIn(1, 24 * 60),
            daysOfWeek = days.ifEmpty { (1..7).toSet() }
        )
    }

    private fun backupString(json: JSONObject, key: String, maximumLength: Int): String {
        val value = json.opt(key)
        require(value is String && value.length <= maximumLength) { "The backup field $key is invalid." }
        return value
    }

    private fun localChatImage(path: String): File? = runCatching {
        val candidate = File(path).canonicalFile
        candidate.takeIf { it.parentFile == chatImagesDirectory.canonicalFile }
    }.getOrNull()

    private fun isBackupUuid(value: String): Boolean =
        value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()

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

    private fun writeMessagesFile(file: File, json: JSONArray): Boolean = runCatching {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (tmp.renameTo(file)) {
            true
        } else {
            file.writeText(json.toString())
            tmp.delete()
            file.isFile
        }
    }.getOrDefault(false)

    fun saveMessages(friendId: String, messages: List<ChatMessage>) {
        runCatching { writeMessagesFile(messageFile(friendId), messagesToJson(messages)) }
    }

    fun deleteFriendData(friend: Friend) {
        loadMessages(friend.id).forEach { message ->
            message.imagePath.takeIf { it.isNotBlank() }?.let(::deleteChatImage)
        }
        messageFile(friend.id).delete()
        deleteFriendPhotos(friend)
        preferences.edit()
            .remove("messages_${friend.id}")
            .remove("user_memories_${friend.id}")
            .remove("structured_user_profile_${friend.id}")
            .remove("session_summaries_${friend.id}")
            .remove("relationship_outcomes_${friend.id}")
            .remove("relationship_fingerprints_${friend.id}")
            .remove("shared_moments_${friend.id}")
            .remove("unread_${friend.id}")
            .remove("current_session_${friend.id}")
            .remove("scheduled_reply_${friend.id}")
            .remove("planned_events_${friend.id}")
            .remove("proactive_paused_until_${friend.id}")
            .remove("last_proactive_sent_${friend.id}")
            .apply()
        removeProactiveMessages(friend.id)
    }

    private fun deleteChatImage(path: String) {
        runCatching {
            val directory = File(context.filesDir, "chat_images").canonicalFile
            val image = File(path).canonicalFile
            if (image.parentFile == directory && image.isFile) image.delete()
        }
    }

    private fun deleteFriendPhotos(friend: Friend) {
        val directory = File(context.filesDir, "system-friend-photos")
        val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: return
        val photoUri = Uri.parse(friend.photoUri)
        if (photoUri.scheme == "file") {
            photoUri.path?.let { path ->
                runCatching {
                    val photo = File(path).canonicalFile
                    if (photo.parentFile == canonicalDirectory) photo.delete()
                }
            }
        }
        if (friend.catalogId.isNotBlank()) {
            val safeId = friend.catalogId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            File(directory, "$safeId.image").delete()
        }
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

    fun proactiveEnabled(): Boolean = preferences.getBoolean("proactive_enabled", true)
    fun setProactiveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("proactive_enabled", enabled).apply()
    }

    fun automaticFollowUpMinutes(): Int = preferences.getInt("automatic_follow_up_minutes", 30).coerceIn(5, 120)
    fun setAutomaticFollowUpMinutes(minutes: Int) {
        require(minutes in 5..120)
        preferences.edit().putInt("automatic_follow_up_minutes", minutes).apply()
    }

    fun quietHoursStartMinute(): Int = preferences.getInt("quiet_hours_start_minute", 22 * 60).coerceIn(0, 1439)
    fun quietHoursEndMinute(): Int = preferences.getInt("quiet_hours_end_minute", 8 * 60).coerceIn(0, 1439)
    fun setQuietHours(startMinute: Int, endMinute: Int) {
        require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute)
        preferences.edit()
            .putInt("quiet_hours_start_minute", startMinute)
            .putInt("quiet_hours_end_minute", endMinute)
            .apply()
    }

    fun proactivePausedUntil(friendId: String): Long = preferences.getLong("proactive_paused_until_$friendId", 0L)
    fun isProactivePaused(friendId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        proactivePausedUntil(friendId) > nowMillis

    fun pauseProactive(friendId: String, untilMillis: Long) {
        require(untilMillis > System.currentTimeMillis())
        preferences.edit().putLong("proactive_paused_until_$friendId", untilMillis).apply()
    }

    fun resumeProactive(friendId: String) {
        preferences.edit().remove("proactive_paused_until_$friendId").apply()
    }

    fun canSendProactive(friendId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nextProactiveEligibleAt(friendId, nowMillis)?.let { it <= nowMillis } ?: false
    }

    fun recordProactiveSent(friendId: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong("last_proactive_sent_$friendId", nowMillis)
            .putLong("last_proactive_global_sent", nowMillis)
            .apply()
    }

    fun nextProactiveEligibleAt(friendId: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!proactiveEnabled()) return null
        val pausedUntil = proactivePausedUntil(friendId)
        if (pausedUntil == Long.MAX_VALUE) return null
        val lastSent = preferences.getLong("last_proactive_sent_$friendId", 0L)
        val cadenceMillis = preferences.getInt("proactive_cadence_minutes", 180).coerceIn(30, 1440) * 60_000L
        val globalEligible = preferences.getLong("last_proactive_global_sent", 0L) + GLOBAL_PROACTIVE_SPACING_MILLIS
        val timeZone = validZoneId(userTimeZoneId())
        var eligible = nextTimeAfterQuietHours(
            java.time.Instant.ofEpochMilli(maxOf(nowMillis, pausedUntil, lastSent + cadenceMillis, globalEligible)).atZone(timeZone),
            quietHoursStartMinute(),
            quietHoursEndMinute()
        ).toInstant().toEpochMilli()
        val reservedDueTimes = loadProactiveMessages()
            .filter { it.trigger != ProactiveTrigger.USER_REMINDER && it.state == ProactiveDeliveryState.SCHEDULED }
            .map { it.dueAtMillis }
        while (true) {
            val staggered = staggerProactiveTime(eligible, reservedDueTimes)
            if (staggered == eligible) break
            eligible = nextTimeAfterQuietHours(
                java.time.Instant.ofEpochMilli(staggered).atZone(timeZone),
                quietHoursStartMinute(),
                quietHoursEndMinute()
            ).toInstant().toEpochMilli()
        }
        return eligible
    }

    fun setProactiveCadenceMinutes(minutes: Int) {
        require(minutes in 30..1440)
        preferences.edit().putInt("proactive_cadence_minutes", minutes).apply()
    }

    fun proactiveCadenceMinutes(): Int = preferences.getInt("proactive_cadence_minutes", 180).coerceIn(30, 1440)

    fun loadProactiveMessages(friendId: String? = null): List<ProactiveMessage> = runCatching {
        val json = JSONArray(preferences.getString("proactive_messages", "[]"))
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            ProactiveMessage(
                id = item.getString("id"),
                friendId = item.getString("friendId"),
                trigger = ProactiveTrigger.valueOf(item.getString("trigger")),
                sessionId = item.optString("sessionId"),
                dueAtMillis = item.getLong("dueAtMillis"),
                reason = item.optString("reason"),
                idempotencyKey = item.getString("idempotencyKey"),
                expectedUserMessageTimestamp = item.optLong("expectedUserMessageTimestamp", 0L),
                eventId = item.optString("eventId"),
                state = ProactiveDeliveryState.valueOf(item.optString("state", "SCHEDULED")),
                updatedAtMillis = item.optLong("updatedAtMillis", 0L)
            )
        }.filter { friendId == null || it.friendId == friendId }
    }.getOrDefault(emptyList())

    fun saveProactiveMessage(message: ProactiveMessage) = synchronized(preferences) {
        val all = loadProactiveMessages().filterNot { it.id == message.id } + message
        persistProactiveMessages(all)
    }

    private fun persistProactiveMessages(messages: List<ProactiveMessage>) {
        val json = JSONArray()
        messages.takeLast(200).forEach { item ->
            json.put(JSONObject().apply {
                put("id", item.id)
                put("friendId", item.friendId)
                put("trigger", item.trigger.name)
                put("sessionId", item.sessionId)
                put("dueAtMillis", item.dueAtMillis)
                put("reason", item.reason)
                put("idempotencyKey", item.idempotencyKey)
                put("expectedUserMessageTimestamp", item.expectedUserMessageTimestamp)
                put("eventId", item.eventId)
                put("state", item.state.name)
                put("updatedAtMillis", item.updatedAtMillis)
            })
        }
        preferences.edit().putString("proactive_messages", json.toString()).apply()
    }

    private fun removeProactiveMessages(friendId: String) = synchronized(preferences) {
        persistProactiveMessages(loadProactiveMessages().filterNot { it.friendId == friendId })
    }

    fun proactiveMessage(id: String): ProactiveMessage? = loadProactiveMessages().firstOrNull { it.id == id }

    fun claimProactiveMessage(id: String, nowMillis: Long = System.currentTimeMillis()): ProactiveMessage? = synchronized(preferences) {
        val existing = proactiveMessage(id) ?: return null
        val staleClaim = existing.state == ProactiveDeliveryState.SENDING && nowMillis - existing.updatedAtMillis >= 15 * 60_000L
        if (existing.state != ProactiveDeliveryState.SCHEDULED && !staleClaim) return null
        existing.copy(state = ProactiveDeliveryState.SENDING, updatedAtMillis = nowMillis).also(::saveProactiveMessage)
    }

    fun finishProactiveMessage(id: String, state: ProactiveDeliveryState, nowMillis: Long = System.currentTimeMillis()) {
        require(state == ProactiveDeliveryState.COMPLETED || state == ProactiveDeliveryState.CANCELLED || state == ProactiveDeliveryState.SCHEDULED)
        val existing = proactiveMessage(id) ?: return
        val nextState = proactiveStateAfterFinish(existing.state, state)
        if (nextState == existing.state) return
        saveProactiveMessage(existing.copy(state = nextState, updatedAtMillis = nowMillis))
        if (nextState == ProactiveDeliveryState.COMPLETED && existing.trigger != ProactiveTrigger.USER_REMINDER) {
            preferences.edit()
                .putLong("last_proactive_sent_${existing.friendId}", nowMillis)
                .putLong("last_proactive_global_sent", nowMillis)
                .apply()
        }
    }

    fun loadPlannedEvents(friendId: String): List<PlannedEvent> = runCatching {
        val json = JSONArray(preferences.getString("planned_events_$friendId", "[]"))
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            PlannedEvent(
                id = item.getString("id"),
                description = item.getString("description"),
                dueAtMillis = item.getLong("dueAtMillis"),
                confidence = item.optString("confidence", "confirmed"),
                followUpRequested = item.optBoolean("followUpRequested", true),
                state = PlannedEventState.valueOf(item.optString("state", "UPCOMING"))
            )
        }
    }.getOrDefault(emptyList())

    fun savePlannedEvent(friendId: String, event: PlannedEvent) {
        require(isBackupUuid(event.id) && event.description.isNotBlank()
            && event.description.length <= 500 && event.dueAtMillis > 0L
            && event.confidence in setOf("confirmed", "uncertain")
        ) { "The planned event is invalid." }
        val events = loadPlannedEvents(friendId).filterNot { it.id == event.id } + event
        require(events.size <= 100) { "A friend can have up to 100 planned events." }
        preferences.edit().putString("planned_events_$friendId", plannedEventsToJson(events).toString()).apply()
    }

    fun completePlannedEvent(friendId: String, eventId: String, completedAtMillis: Long = System.currentTimeMillis()): PlannedEvent? =
        synchronized(preferences) {
            val event = loadPlannedEvents(friendId).firstOrNull { it.id == eventId } ?: return null
            if (event.state != PlannedEventState.UPCOMING) return event
            val completed = event.copy(state = PlannedEventState.COMPLETED)
            val events = loadPlannedEvents(friendId).map { if (it.id == eventId) completed else it }
            val moment = completedEventMoment(completed, completedAtMillis)
            val moments = boundedSharedMoments(loadSharedMoments(friendId).filterNot { it.id == moment.id } + moment)
            check(
                preferences.edit()
                    .putString("planned_events_$friendId", plannedEventsToJson(events).toString())
                    .putString("shared_moments_$friendId", sharedMomentsToJson(moments).toString())
                    .commit()
            ) { "Could not complete the planned event." }
            completed
        }

    private fun plannedEventsToJson(events: List<PlannedEvent>): JSONArray = JSONArray().apply {
        events.forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("description", item.description)
                put("dueAtMillis", item.dueAtMillis)
                put("confidence", item.confidence)
                put("followUpRequested", item.followUpRequested)
                put("state", item.state.name)
            })
        }
    }

    fun updatePlannedEvent(friendId: String, eventId: String, transform: (PlannedEvent) -> PlannedEvent): PlannedEvent? {
        val event = loadPlannedEvents(friendId).firstOrNull { it.id == eventId } ?: return null
        return transform(event).also { savePlannedEvent(friendId, it) }
    }

    fun loadSharedMoments(friendId: String): List<SharedMoment> = runCatching {
        val json = JSONArray(preferences.getString("shared_moments_$friendId", "[]"))
        List(json.length()) { index ->
            val item = json.getJSONObject(index)
            SharedMoment(
                id = item.getString("id"),
                title = item.optString("title"),
                detail = item.optString("detail"),
                createdAtMillis = item.getLong("createdAtMillis"),
                kind = item.getString("kind"),
                pinned = item.optBoolean("pinned", false),
                stageIndex = if (item.has("stageIndex") && !item.isNull("stageIndex")) item.getInt("stageIndex") else null
            )
        }
    }.getOrDefault(emptyList())

    fun saveSharedMoment(friendId: String, moment: SharedMoment) {
        require(isBackupUuid(moment.id) && moment.title.length <= 200 && moment.detail.length <= 2_000
            && moment.createdAtMillis > 0L && moment.kind.length in 1..40
            && (moment.stageIndex == null || moment.stageIndex in 0..9)
        ) { "The shared moment is invalid." }
        val moments = boundedSharedMoments(loadSharedMoments(friendId).filterNot { it.id == moment.id } + moment)
        preferences.edit().putString("shared_moments_$friendId", sharedMomentsToJson(moments).toString()).apply()
    }

    fun updateSharedMoment(friendId: String, momentId: String, transform: (SharedMoment) -> SharedMoment): SharedMoment? {
        val moment = loadSharedMoments(friendId).firstOrNull { it.id == momentId } ?: return null
        return transform(moment).also { saveSharedMoment(friendId, it) }
    }

    fun deleteSharedMoment(friendId: String, momentId: String) {
        val moments = loadSharedMoments(friendId).filterNot { it.id == momentId }
        preferences.edit().putString("shared_moments_$friendId", sharedMomentsToJson(moments).toString()).apply()
    }

    private fun sharedMomentsToJson(moments: List<SharedMoment>): JSONArray = JSONArray().apply {
        moments.forEach { moment ->
            put(JSONObject().apply {
                put("id", moment.id)
                put("title", moment.title)
                put("detail", moment.detail)
                put("createdAtMillis", moment.createdAtMillis)
                put("kind", moment.kind)
                put("pinned", moment.pinned)
                moment.stageIndex?.let { put("stageIndex", it) }
            })
        }
    }

    private fun boundedSharedMoments(moments: List<SharedMoment>): List<SharedMoment> {
        val pinned = moments.filter { it.pinned }.takeLast(MAX_SHARED_MOMENTS)
        val unpinned = moments.filterNot { it.pinned }.takeLast((MAX_SHARED_MOMENTS - pinned.size).coerceAtLeast(0))
        return (pinned + unpinned).sortedBy { it.createdAtMillis }
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

    fun loadStructuredUserProfile(friendId: String, name: String = userName()): StructuredUserProfile {
        val key = "structured_user_profile_$friendId"
        val storedJson = preferences.getString(key, null)
        val stored = storedJson?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                fun strings(slot: String) = json.optJSONArray(slot)?.let { values ->
                    List(values.length()) { index -> values.optString(index) }.filter { it.isNotBlank() }
                }.orEmpty()
                StructuredUserProfile(json.optString("name"), strings("people"), strings("preferences"))
            }.getOrNull()
        }
        val profile = stored ?: structuredProfileFromMemories(name, loadUserMemories(friendId))
        val updated = profile.copy(name = name.trim().ifBlank { profile.name })
        if (stored == null || updated != stored) saveStructuredUserProfile(friendId, updated)
        return updated
    }

    private fun saveStructuredUserProfile(friendId: String, profile: StructuredUserProfile) {
        val json = JSONObject().apply {
            put("name", profile.name)
            put("people", JSONArray(profile.people.takeLast(MAX_PROFILE_FACTS)))
            put("preferences", JSONArray(profile.preferences.takeLast(MAX_PROFILE_FACTS)))
        }
        preferences.edit().putString("structured_user_profile_$friendId", json.toString()).apply()
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
        saveStructuredUserProfile(friendId, structuredProfileFromMemories(userName(), memories))
    }

    fun updateUserMemory(friendId: String, original: UserMemory, updated: UserMemory) {
        saveUserMemories(friendId, replaceUserMemory(loadUserMemories(friendId), original, updated))
    }

    fun deleteUserMemory(friendId: String, memory: UserMemory) {
        saveUserMemories(friendId, withoutUserMemory(loadUserMemories(friendId), memory))
    }

    fun addUserMemories(friendId: String, newMemories: List<UserMemory>) {
        saveUserMemories(friendId, mergeUserMemories(loadUserMemories(friendId), newMemories))
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

    private data class ImportedBackupFriend(
        val friend: Friend,
        val memories: List<UserMemory>,
        val summaries: List<SessionSummary>,
        val events: List<PlannedEvent>,
        val relationshipOutcomes: List<String>,
        val relationshipFingerprints: List<String>,
        val sharedMoments: List<SharedMoment>,
        val messages: List<ImportedBackupMessage>,
        val currentSessionId: String
    )

    private data class ImportedBackupMessage(var message: ChatMessage, val imageBytes: ByteArray?)

    private companion object {
        const val MAX_RELATIONSHIP_OUTCOMES = 1_000
        const val MAX_RELATIONSHIP_FINGERPRINTS = 100
        const val MAX_SHARED_MOMENTS = 100
        const val API_KEY_ALIAS = "dost_openrouter_api_key"
        const val DEFAULT_AI_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
        const val BACKUP_FORMAT = "dost-local-backup"
        const val BACKUP_VERSION = 1

        const val MAX_BACKUP_FRIENDS = 10
        const val MAX_BACKUP_TEXT_LENGTH = 250_000
        const val MAX_BACKUP_IMAGE_BYTES = 375_000
        const val MAX_BACKUP_IMAGE_BASE64_LENGTH = 500_000
    }
}