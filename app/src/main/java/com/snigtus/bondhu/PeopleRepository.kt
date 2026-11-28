package com.snigtus.dost

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

internal data class PeopleProfileDraft(
    val country: String = "",
    val bio: String = "",
    val interests: String = ""
)

internal enum class PeopleContactPolicy(val firestoreValue: String) {
    EVERYONE("everyone"),
    REQUESTS("requests"),
    MATCHES("matches"),
    NOBODY("nobody");

    companion object {
        fun fromFirestore(value: String?): PeopleContactPolicy =
            values().firstOrNull { it.firestoreValue == value }
                ?: if (value == null) EVERYONE else error("A profile has an invalid contact preference.")
    }
}

internal data class PublicPersonProfile(
    val uid: String,
    val displayName: String,
    val ageBand: String,
    val ageCohort: String,
    val gender: String,
    val country: String,
    val bio: String,
    val interests: String,
    val isPublic: Boolean,
    val contactPolicy: PeopleContactPolicy = PeopleContactPolicy.EVERYONE
)

internal fun filterPeopleByInterest(
    profiles: List<PublicPersonProfile>,
    interestQuery: String
): List<PublicPersonProfile> {
    val normalizedQuery = interestQuery.trim()
    if (normalizedQuery.isEmpty()) return profiles
    return profiles.filter { it.interests.contains(normalizedQuery, ignoreCase = true) }
}

internal data class SocialConversation(
    val id: String,
    val peer: PublicPersonProfile,
    val lastMessage: String,
    val lastMessageAtMillis: Long,
    val unreadCount: Int = 0
)

internal data class CircleGroup(
    val id: String,
    val name: String,
    val members: List<String>,
    val admins: List<String>,
    val lastMessage: String,
    val lastMessageAtMillis: Long
)

internal data class SocialContactRequest(
    val id: String,
    val sender: PublicPersonProfile
)

internal enum class SocialContactRequestStatus(val firestoreValue: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    IGNORED("ignored");

    companion object {
        fun fromFirestore(value: String?): SocialContactRequestStatus =
            values().firstOrNull { it.firestoreValue == value }
                ?: error("A contact request has an invalid status.")
    }
}

internal data class SocialOutgoingContactRequest(
    val recipientUid: String,
    val status: SocialContactRequestStatus
)

internal sealed class PeopleContactStartResult {
    data class Conversation(val value: SocialConversation) : PeopleContactStartResult()
    object RequestSent : PeopleContactStartResult()
    data class ExistingRequest(val status: SocialContactRequestStatus) : PeopleContactStartResult()
    object IncomingRequest : PeopleContactStartResult()
    object Unavailable : PeopleContactStartResult()
}

internal data class SocialChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAtMillis: Long,
    val deliveryState: SocialMessageDeliveryState = SocialMessageDeliveryState.SENT,
    val readBy: Set<String> = emptySet()
)

internal fun searchSocialMessages(
    messages: List<SocialChatMessage>,
    query: String
): List<SocialChatMessage> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return messages
    return messages.filter { it.text.contains(normalizedQuery, ignoreCase = true) }
}

internal data class OlderSocialMessagePage(
    val messages: List<SocialChatMessage>,
    val hasMore: Boolean
)

internal enum class SocialMessageDeliveryState { SENDING, WAITING, SENT, FAILED }

internal enum class PendingPeopleMessageStatus { WAITING, FAILED }

internal data class PendingPeopleMessage(
    val uid: String,
    val conversationId: String,
    val messageId: String,
    val text: String,
    val createdAtMillis: Long,
    val status: PendingPeopleMessageStatus
)

internal data class PeopleMessageDeliveryUpdate(
    val uid: String,
    val conversationId: String,
    val messageId: String,
    val deliveryState: SocialMessageDeliveryState
)

private val SOCIAL_MESSAGE_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
internal const val MAX_SOCIAL_CHAT_MESSAGES = 500

internal fun isValidSocialMessageId(messageId: String): Boolean =
    SOCIAL_MESSAGE_ID_PATTERN.matches(messageId)

internal fun PendingPeopleMessage.toSocialChatMessage(): SocialChatMessage =
    SocialChatMessage(
        id = messageId,
        senderUid = uid,
        text = text,
        createdAtMillis = createdAtMillis,
        deliveryState = when (status) {
            PendingPeopleMessageStatus.WAITING -> SocialMessageDeliveryState.WAITING
            PendingPeopleMessageStatus.FAILED -> SocialMessageDeliveryState.FAILED
        }
    )

internal fun isRetryablePeopleMessageFailure(exception: Throwable): Boolean {
    var cause: Throwable? = exception
    while (cause != null) {
        when (cause) {
            is FirebaseFirestoreException -> return cause.code in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED
            )
            is FirebaseNetworkException, is IOException -> return true
        }
        cause = cause.cause
    }
    return false
}

internal fun peopleFailureMessage(
    error: Throwable,
    language: AppLanguage,
    fallback: String
): String {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is kotlinx.coroutines.CancellationException) throw cause
        if (cause is FirebaseFirestoreException) {
            peopleFailureMessageForFirestoreCode(cause.code.name, language)?.let { return it }
        }
        if (cause is FirebaseNetworkException || cause is IOException) {
            return peopleConnectionFailureMessage(language)
        }
        cause = cause.cause
    }
    return fallback
}

internal fun peopleFailureMessageForFirestoreCode(code: String, language: AppLanguage): String? =
    when (code) {
        "RESOURCE_EXHAUSTED" -> when (language) {
            AppLanguage.BANGLA -> "অনেকবার চেষ্টা হয়েছে। একটু অপেক্ষা করে আবার চেষ্টা করুন।"
            AppLanguage.HINDI -> "बहुत अधिक प्रयास हुए। थोड़ी देर बाद फिर कोशिश करें।"
            AppLanguage.ENGLISH -> "Too many attempts. Wait a little and try again."
        }
        "PERMISSION_DENIED" -> when (language) {
            AppLanguage.BANGLA -> "এই কাজটি এই প্রোফাইল বা অ্যাকাউন্টের জন্য উপলভ্য নয়। পরে আবার চেষ্টা করুন।"
            AppLanguage.HINDI -> "यह कार्रवाई इस प्रोफ़ाइल या खाते के लिए उपलब्ध नहीं है। बाद में फिर कोशिश करें।"
            AppLanguage.ENGLISH -> "This action is unavailable for this profile or account. Try again later."
        }
        "UNAVAILABLE", "DEADLINE_EXCEEDED", "ABORTED" -> peopleConnectionFailureMessage(language)
        else -> null
    }

private fun peopleConnectionFailureMessage(language: AppLanguage): String = when (language) {
    AppLanguage.BANGLA -> "সংযোগে সমস্যা হয়েছে। ইন্টারনেট পরীক্ষা করে আবার চেষ্টা করুন।"
    AppLanguage.HINDI -> "कनेक्शन में समस्या है। इंटरनेट जाँचकर फिर कोशिश करें।"
    AppLanguage.ENGLISH -> "Connection problem. Check your internet and try again."
}

internal fun mergeSocialMessages(
    confirmedMessages: List<SocialChatMessage>,
    pendingMessages: List<SocialChatMessage>
): List<SocialChatMessage> {
    val uniqueConfirmedMessages = confirmedMessages.distinctBy { it.id }
    val confirmedIds = uniqueConfirmedMessages.mapTo(mutableSetOf()) { it.id }
    return (uniqueConfirmedMessages + pendingMessages.filterNot { it.id in confirmedIds })
        .sortedBy { it.createdAtMillis }
}

private fun DocumentSnapshot.toSocialChatMessage(): SocialChatMessage =
    SocialChatMessage(
        id = id,
        senderUid = getString("senderUid")
            ?: error("A chat message is missing its sender."),
        text = getString("text")
            ?: error("A chat message is missing its text."),
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        readBy = (get("readAtByUid") as? Map<*, *>)
            ?.keys
            ?.filterIsInstance<String>()
            ?.toSet()
            .orEmpty()
    )

internal data class PeopleCallDescription(
    val type: String,
    val sdp: String
)

internal data class PeopleCallRecord(
    val id: String,
    val chatId: String,
    val callerUid: String,
    val calleeUid: String,
    val mediaType: String,
    val status: String,
    val offer: PeopleCallDescription,
    val answer: PeopleCallDescription?,
    val ringExpiresAtMillis: Long,
    val expiresAtMillis: Long
)

internal val PEOPLE_CALL_MEDIA_TYPES = setOf("audio", "video")

internal data class PeopleCallCandidate(
    val id: String,
    val sdp: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int
)

internal fun socialAgeCohort(age: String): String? {
    val years = age.toIntOrNull() ?: return null
    if (years !in ROMANCE_MINIMUM_AGE..MAX_PROFILE_AGE) return null
    return "adult"
}

internal fun socialAgeBand(age: String): String? {
    val years = age.toIntOrNull() ?: return null
    if (years !in ROMANCE_MINIMUM_AGE..MAX_PROFILE_AGE) return null
    return when {
        years <= 24 -> "18–24"
        years <= 34 -> "25–34"
        else -> "35+"
    }
}

internal fun socialConversationId(firstUid: String, secondUid: String): String {
    require(firstUid.isNotBlank() && secondUid.isNotBlank() && firstUid != secondUid)
    val canonical = listOf(firstUid, secondUid).sorted().joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal class PeopleRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val app = FirebaseApp.getApps(applicationContext).firstOrNull()
    private val firestore = app?.let(FirebaseFirestore::getInstance)
    private val callApiClient = PeopleCallApiClient(applicationContext, app)

    private fun database(): FirebaseFirestore =
        firestore ?: throw IllegalStateException("Firebase is not configured for this app.")

    private fun requireSignedIn(uid: String) {
        val currentUid = app?.let { FirebaseAuth.getInstance(it).currentUser?.uid }
        check(!uid.isBlank() && currentUid == uid) { "Sign in again to use People." }
    }

    suspend fun getOwnProfile(uid: String): PublicPersonProfile? {
        requireSignedIn(uid)
        val snapshot = database().collection(PROFILES).document(uid).get().await()
        return if (snapshot.exists()) snapshot.toPublicPersonProfile() else null
    }

    suspend fun saveProfile(profile: PublicPersonProfile) {
        requireSignedIn(profile.uid)
        validateProfile(profile)
        database().collection(PROFILES).document(profile.uid).set(
            profile.toFirestoreMap() + ("updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun deleteOwnProfile(uid: String) {
        requireSignedIn(uid)
        database().collection(PROFILES).document(uid).delete().await()
    }

    suspend fun discover(uid: String, ageCohort: String): List<PublicPersonProfile> {
        requireSignedIn(uid)
        require(ageCohort == "adult") { "An adult account is required." }
        val blockedIds = blockedIds(uid)
        return database().collection(PROFILES)
            .whereEqualTo("isPublic", true)
            .whereEqualTo("ageCohort", ageCohort)
            .limit(MAX_DISCOVERED_PEOPLE.toLong())
            .get()
            .await()
            .documents
            .asSequence()
            .filterNot { it.id == uid || it.id in blockedIds }
            .map { it.toPublicPersonProfile() }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    suspend fun blockedIds(uid: String): Set<String> {
        requireSignedIn(uid)
        return database().collection(BLOCKS).document(uid)
            .collection(BLOCKED_USERS).get().await().documents.map { it.id }.toSet()
    }

    suspend fun startConversationOrRequest(
        uid: String,
        ownProfile: PublicPersonProfile,
        peer: PublicPersonProfile
    ): PeopleContactStartResult {
        requireSignedIn(uid)
        require(ownProfile.uid == uid && ownProfile.isPublic) { "Publish your profile before starting a chat." }
        require(peer.uid != uid && peer.isPublic && peer.ageCohort == ownProfile.ageCohort) {
            "This profile is not available for a chat."
        }
        val database = database()
        val conversationRef = database.collection(CHATS).document(socialConversationId(uid, peer.uid))
        val ownProfileRef = database.collection(PROFILES).document(uid)
        val peerProfileRef = database.collection(PROFILES).document(peer.uid)
        val requestRef = database.collection(CONTACT_REQUESTS).document(peer.uid)
            .collection(INCOMING_REQUESTS).document(uid)
        val incomingRequestRef = database.collection(CONTACT_REQUESTS).document(uid)
            .collection(INCOMING_REQUESTS).document(peer.uid)
        return database.runTransaction { transaction ->
            val existing = transaction.get(conversationRef)
            if (existing.exists()) {
                val members = existing.get("members") as? List<*>
                check(members?.contains(uid) == true && members.contains(peer.uid)) {
                    "This conversation is not available."
                }
                PeopleContactStartResult.Conversation(existing.toSocialConversation(uid))
            } else {
                val ownSnapshot = transaction.get(ownProfileRef)
                val peerSnapshot = transaction.get(peerProfileRef)
                val currentProfile = ownSnapshot.toPublicPersonProfile()
                val currentPeer = peerSnapshot.toPublicPersonProfile()
                if (!currentProfile.isPublic || !currentPeer.isPublic
                    || currentProfile.ageCohort != currentPeer.ageCohort
                ) {
                    PeopleContactStartResult.Unavailable
                } else {
                    val incomingRequest = transaction.get(incomingRequestRef)
                    if (incomingRequest.exists()
                        && incomingRequest.getString("status") == SocialContactRequestStatus.PENDING.firestoreValue
                    ) {
                        PeopleContactStartResult.IncomingRequest
                    } else {
                        when (currentPeer.contactPolicy) {
                            PeopleContactPolicy.EVERYONE -> {
                                transaction.set(conversationRef, newConversationData(uid, currentProfile, currentPeer))
                                PeopleContactStartResult.Conversation(
                                    SocialConversation(conversationRef.id, currentPeer, "", 0L)
                                )
                            }
                            PeopleContactPolicy.REQUESTS, PeopleContactPolicy.MATCHES -> {
                                val existingRequest = transaction.get(requestRef)
                                if (existingRequest.exists()) {
                                    PeopleContactStartResult.ExistingRequest(
                                        SocialContactRequestStatus.fromFirestore(existingRequest.getString("status"))
                                    )
                                } else {
                                    transaction.set(requestRef, mapOf(
                                        "senderUid" to uid,
                                        "recipientUid" to peer.uid,
                                        "conversationId" to conversationRef.id,
                                        "status" to SocialContactRequestStatus.PENDING.firestoreValue,
                                        "senderProfile" to currentProfile.toFirestoreMap().filterKeys { it != "isPublic" },
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    ))
                                    PeopleContactStartResult.RequestSent
                                }
                            }
                            PeopleContactPolicy.NOBODY -> PeopleContactStartResult.Unavailable
                        }
                    }
                }
            }
        }.await()
    }

    fun listenIncomingRequests(
        uid: String,
        onChange: (List<SocialContactRequest>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CONTACT_REQUESTS).document(uid)
            .collection(INCOMING_REQUESTS)
            .whereEqualTo("recipientUid", uid)
            .whereEqualTo("status", "pending")
            .limit(MAX_CONTACT_REQUESTS.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onError(IllegalStateException("Could not load contact requests."))
                    return@addSnapshotListener
                }
                try {
                    onChange(snapshot.documents.map { it.toSocialContactRequest() })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    fun listenOutgoingRequests(
            uid: String,
            onChange: (List<SocialOutgoingContactRequest>) -> Unit,
            onError: (Exception) -> Unit
    ): ListenerRegistration {
            requireSignedIn(uid)
            return database().collectionGroup(INCOMING_REQUESTS)
                .whereEqualTo("senderUid", uid)
                .whereIn(
                    "status",
                    SocialContactRequestStatus.values().map(SocialContactRequestStatus::firestoreValue)
                )
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(MAX_CONTACT_REQUESTS.toLong())
                .addSnapshotListener { snapshot, listenerError ->
                    if (listenerError != null) {
                        onError(listenerError)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) {
                        onError(IllegalStateException("Could not load sent contact requests."))
                        return@addSnapshotListener
                    }
                    try {
                        onChange(snapshot.documents.map { document ->
                            check(document.getString("senderUid") == uid) {
                                "A sent contact request has an invalid sender."
                            }
                            SocialOutgoingContactRequest(
                                recipientUid = document.getString("recipientUid")
                                    ?: error("A sent contact request is missing its recipient."),
                                status = SocialContactRequestStatus.fromFirestore(document.getString("status"))
                            )
                        })
                    } catch (exception: Exception) {
                        onError(exception)
                    }
                }
    }

    suspend fun acceptContactRequest(uid: String, request: SocialContactRequest): SocialConversation {
        requireSignedIn(uid)
        require(request.id == request.sender.uid)
        val database = database()
        val requestRef = database.collection(CONTACT_REQUESTS).document(uid)
            .collection(INCOMING_REQUESTS).document(request.id)
        val conversationRef = database.collection(CHATS).document(socialConversationId(uid, request.id))
        val approvalRef = database.collection(CONTACT_APPROVALS).document(conversationRef.id)
        val ownProfileRef = database.collection(PROFILES).document(uid)
        val senderProfileRef = database.collection(PROFILES).document(request.id)
        return database.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            check(requestSnapshot.exists() && requestSnapshot.getString("status") == "pending") {
                "This contact request is no longer available."
            }
            check(requestSnapshot.getString("conversationId") == conversationRef.id) {
                "This contact request is invalid."
            }
            val existing = transaction.get(conversationRef)
            val ownProfile = transaction.get(ownProfileRef).toPublicPersonProfile()
            val senderProfile = transaction.get(senderProfileRef).toPublicPersonProfile()
            check(ownProfile.isPublic && senderProfile.isPublic
                && ownProfile.ageCohort == senderProfile.ageCohort
            ) { "Both public profiles must remain available to start a chat." }
            val conversation = if (existing.exists()) {
                val members = existing.get("members") as? List<*>
                check(members?.contains(uid) == true && members.contains(request.id)) {
                    "This conversation is not available."
                }
                existing.toSocialConversation(uid)
            } else {
                transaction.set(conversationRef, newConversationData(uid, ownProfile, senderProfile))
                transaction.set(approvalRef, mapOf(
                    "conversationId" to conversationRef.id,
                    "recipientUid" to uid,
                    "senderUid" to request.id,
                    "members" to listOf(uid, request.id).sorted(),
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                SocialConversation(conversationRef.id, senderProfile, "", 0L)
            }
            transaction.update(requestRef, mapOf(
                "status" to "accepted",
                "respondedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            conversation
        }.await()
    }

    suspend fun ignoreContactRequest(uid: String, request: SocialContactRequest) {
        updateContactRequest(uid, request, "ignored")
    }

    suspend fun blockContactRequest(uid: String, request: SocialContactRequest) {
        requireSignedIn(uid)
        require(request.id == request.sender.uid)
        val database = database()
        val requestRef = database.collection(CONTACT_REQUESTS).document(uid)
            .collection(INCOMING_REQUESTS).document(request.id)
        val blockRef = database.collection(BLOCKS).document(uid)
            .collection(BLOCKED_USERS).document(request.id)
        database.runTransaction { transaction ->
            val requestSnapshot = transaction.get(requestRef)
            check(requestSnapshot.exists() && requestSnapshot.getString("status") == "pending") {
                "This contact request is no longer available."
            }
            transaction.set(blockRef, mapOf("createdAt" to FieldValue.serverTimestamp()))
            transaction.update(requestRef, mapOf(
                "status" to "ignored",
                "respondedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    private suspend fun updateContactRequest(uid: String, request: SocialContactRequest, status: String) {
        requireSignedIn(uid)
        require(request.id == request.sender.uid)
        database().collection(CONTACT_REQUESTS).document(uid)
            .collection(INCOMING_REQUESTS).document(request.id)
            .update(mapOf(
                "status" to status,
                "respondedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )).await()
    }

    fun listenConversations(
        uid: String,
        onChange: (List<SocialConversation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CHATS)
            .whereArrayContains("members", uid)
            .limit(MAX_CONVERSATIONS.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onError(IllegalStateException("Could not load conversations."))
                    return@addSnapshotListener
                }
                try {
                    onChange(snapshot.documents.map { it.toSocialConversation(uid) }
                        .sortedByDescending { it.lastMessageAtMillis })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    suspend fun markConversationRead(uid: String, conversationId: String) {
            requireSignedIn(uid)
            val conversation = database().collection(CHATS).document(conversationId)
            database().runTransaction { transaction ->
                val snapshot = transaction.get(conversation)
                check(snapshot.exists()) { "This conversation is not available." }
                val members = snapshot.get("members") as? List<*>
                    ?: error("A conversation is missing its participants.")
                check(uid in members) { "This conversation is not available." }
                val peerUid = members.filterIsInstance<String>().firstOrNull { it != uid }
                    ?: error("A conversation is missing the other participant.")
                val unreadCounts = snapshot.get("unreadCounts") as? Map<*, *>
                val ownUnreadCount = (unreadCounts?.get(uid) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                if (ownUnreadCount > 0L) {
                    val peerUnreadCount = (unreadCounts?.get(peerUid) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                    transaction.update(conversation, "unreadCounts", mapOf(uid to 0L, peerUid to peerUnreadCount))
                }
            }.await()
    }

    fun listenMessages(
        uid: String,
        conversationId: String,
        onChange: (List<SocialChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CHATS).document(conversationId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_SOCIAL_CHAT_MESSAGES.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onError(IllegalStateException("Could not load messages."))
                    return@addSnapshotListener
                }
                try {
                    onChange(snapshot.documents.map { it.toSocialChatMessage() }
                        .sortedBy { it.createdAtMillis })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    suspend fun loadOlderMessages(
        uid: String,
        conversationId: String,
        beforeMessageId: String
    ): OlderSocialMessagePage {
        requireSignedIn(uid)
        require(isValidSocialMessageId(beforeMessageId)) { "The message history cursor is invalid." }
        val collection = database().collection(CHATS).document(conversationId).collection(MESSAGES)
        val cursor = collection.document(beforeMessageId).get().await()
        check(cursor.exists()) { "The last visible message is no longer available." }
        val page = collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(cursor)
            .limit((MAX_SOCIAL_CHAT_MESSAGES + 1).toLong())
            .get()
            .await()
            .documents
        return OlderSocialMessagePage(
            messages = page.take(MAX_SOCIAL_CHAT_MESSAGES)
                .map { it.toSocialChatMessage() }
                .sortedBy { it.createdAtMillis },
            hasMore = page.size > MAX_SOCIAL_CHAT_MESSAGES
        )
    }

    suspend fun getConversation(uid: String, conversationId: String): SocialConversation? {
        requireSignedIn(uid)
        val snapshot = database().collection(CHATS).document(conversationId).get().await()
        return if (snapshot.exists()) snapshot.toSocialConversation(uid) else null
    }

    fun listenCircleGroups(
        uid: String,
        onChange: (List<CircleGroup>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CIRCLE_GROUPS)
            .whereArrayContains("members", uid)
            .limit(MAX_CONVERSATIONS.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                } else if (snapshot == null) {
                    onError(IllegalStateException("Could not load Circle groups."))
                } else {
                    try {
                        onChange(snapshot.documents.map { it.toCircleGroup() }
                            .sortedByDescending { it.lastMessageAtMillis })
                    } catch (exception: Exception) {
                        onError(exception)
                    }
                }
            }
    }

    suspend fun createCircleGroup(uid: String, name: String): CircleGroup {
        requireSignedIn(uid)
        val trimmed = name.trim()
        require(trimmed.length in 1..80) { "Group names must contain 1–80 characters." }
        val group = database().collection(CIRCLE_GROUPS).document()
        group.set(mapOf(
            "members" to listOf(uid),
            "admins" to listOf(uid),
            "createdBy" to uid,
            "name" to trimmed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "",
            "lastMessageSenderUid" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessageId" to ""
        )).await()
        return CircleGroup(group.id, trimmed, listOf(uid), listOf(uid), "", System.currentTimeMillis())
    }

    suspend fun inviteCircleGroupMember(uid: String, groupId: String, inviteeUid: String) {
        requireSignedIn(uid)
        require(groupId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(inviteeUid.matches(Regex("[A-Za-z0-9:_-]{1,128}")) && inviteeUid != uid)
        val group = database().collection(CIRCLE_GROUPS).document(groupId)
        val groupSnapshot = group.get().await()
        check(groupSnapshot.exists()) { "This group is no longer available." }
        group.collection(CIRCLE_GROUP_INVITES).document(inviteeUid)
            .set(mapOf(
                "groupId" to groupId,
                "groupName" to (groupSnapshot.getString("name") ?: "Circle group"),
                "inviteeUid" to inviteeUid,
                "invitedBy" to uid,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp()
            )).await()
    }

    fun listenCircleGroupInvites(
        uid: String,
        onChange: (List<Pair<String, String>>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collectionGroup(CIRCLE_GROUP_INVITES)
            .whereEqualTo("inviteeUid", uid)
            .whereEqualTo("status", "pending")
            .limit(MAX_CONTACT_REQUESTS.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                } else if (snapshot == null) {
                    onError(IllegalStateException("Could not load group invitations."))
                } else {
                    onChange(snapshot.documents.mapNotNull { invite ->
                        val groupId = invite.getString("groupId") ?: return@mapNotNull null
                        groupId to (invite.getString("groupName") ?: "Circle group")
                    })
                }
            }
    }

    suspend fun acceptCircleGroupInvite(uid: String, groupId: String) {
        requireSignedIn(uid)
        val database = database()
        val group = database.collection(CIRCLE_GROUPS).document(groupId)
        val invite = group.collection(CIRCLE_GROUP_INVITES).document(uid)
        database.runTransaction { transaction ->
            val groupSnapshot = transaction.get(group)
            val inviteSnapshot = transaction.get(invite)
            check(groupSnapshot.exists() && inviteSnapshot.getString("status") == "pending") {
                "This group invitation is no longer available."
            }
            val members = (groupSnapshot.get("members") as? List<*>)
                ?.filterIsInstance<String>() ?: error("The group membership is invalid.")
            check(uid !in members && members.size < MAX_CIRCLE_GROUP_MEMBERS) {
                "This group is full or you are already a member."
            }
            transaction.update(group, mapOf(
                "members" to (members + uid),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            transaction.update(invite, mapOf(
                "status" to "accepted",
                "acceptedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun renameCircleGroup(uid: String, groupId: String, name: String) {
        requireSignedIn(uid)
        val trimmed = name.trim()
        require(trimmed.length in 1..80) { "Group names must contain 1–80 characters." }
        database().collection(CIRCLE_GROUPS).document(groupId).update(mapOf(
            "name" to trimmed,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun leaveCircleGroup(uid: String, groupId: String) {
        requireSignedIn(uid)
        val database = database()
        val group = database.collection(CIRCLE_GROUPS).document(groupId)
        database.runTransaction { transaction ->
            val snapshot = transaction.get(group)
            check(snapshot.exists()) { "This group is no longer available." }
            val members = (snapshot.get("members") as? List<*>)
                ?.filterIsInstance<String>() ?: error("The group membership is invalid.")
            check(uid in members) { "You are not a member of this group." }
            transaction.update(group, mapOf(
                "members" to members.filterNot { it == uid },
                "admins" to ((snapshot.get("admins") as? List<*>)
                    ?.filterIsInstance<String>() ?: emptyList()).filterNot { it == uid },
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun removeCircleGroupMember(uid: String, groupId: String, memberUid: String) {
        requireSignedIn(uid)
        require(memberUid != uid)
        val database = database()
        val group = database.collection(CIRCLE_GROUPS).document(groupId)
        database.runTransaction { transaction ->
            val snapshot = transaction.get(group)
            check(snapshot.exists()) { "This group is no longer available." }
            val members = (snapshot.get("members") as? List<*>)
                ?.filterIsInstance<String>() ?: error("The group membership is invalid.")
            check(memberUid in members) { "This person is not a member of the group." }
            transaction.update(group, mapOf(
                "members" to members.filterNot { it == memberUid },
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun promoteCircleGroupAdmin(uid: String, groupId: String, memberUid: String) {
        requireSignedIn(uid)
        val group = database().collection(CIRCLE_GROUPS).document(groupId)
        database().runTransaction { transaction ->
            val snapshot = transaction.get(group)
            check(snapshot.exists()) { "This group is no longer available." }
            val members = (snapshot.get("members") as? List<*>)
                ?.filterIsInstance<String>() ?: error("The group membership is invalid.")
            val admins = (snapshot.get("admins") as? List<*>)
                ?.filterIsInstance<String>() ?: error("The group admins are invalid.")
            check(memberUid in members && memberUid !in admins) {
                "This person cannot be made a group admin."
            }
            transaction.update(group, mapOf(
                "admins" to (admins + memberUid),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    fun listenCircleGroupMessages(
        uid: String,
        groupId: String,
        onChange: (List<SocialChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CIRCLE_GROUPS).document(groupId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_SOCIAL_CHAT_MESSAGES.toLong())
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                } else if (snapshot == null) {
                    onError(IllegalStateException("Could not load group messages."))
                } else {
                    try {
                        onChange(snapshot.documents.map { it.toSocialChatMessage() }
                            .sortedBy { it.createdAtMillis })
                    } catch (exception: Exception) {
                        onError(exception)
                    }
                }
            }
    }

    suspend fun markCircleGroupMessagesRead(uid: String, groupId: String, messages: List<SocialChatMessage>) {
        requireSignedIn(uid)
        require(uid.matches(Regex("[A-Za-z0-9:_-]{1,128}")))
        val unread = messages.filter { it.senderUid != uid && uid !in it.readBy }
        val group = database().collection(CIRCLE_GROUPS).document(groupId)
        unread.chunked(450).forEach { page ->
            val batch = database().batch()
            page.forEach { message ->
                batch.update(
                    group.collection(MESSAGES).document(message.id),
                    "readAtByUid.$uid",
                    FieldValue.serverTimestamp()
                )
            }
            batch.commit().await()
        }
    }

    fun listenCircleGroupTyping(
        uid: String,
        groupId: String,
        onChange: (List<Pair<String, Long>>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return database().collection(CIRCLE_GROUPS).document(groupId)
            .collection(TYPING_INDICATORS)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                } else if (snapshot == null) {
                    onError(IllegalStateException("Could not load typing indicators."))
                } else {
                    val now = System.currentTimeMillis()
                    onChange(snapshot.documents.mapNotNull { document ->
                        val expiresAt = document.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                        if (document.id != uid && expiresAt > now) document.id to expiresAt else null
                    })
                }
            }
    }

    suspend fun setCircleGroupTyping(uid: String, groupId: String, typing: Boolean) {
        requireSignedIn(uid)
        require(uid.matches(Regex("[A-Za-z0-9:_-]{1,128}")))
        val indicator = database().collection(CIRCLE_GROUPS).document(groupId)
            .collection(TYPING_INDICATORS).document(uid)
        if (typing) {
            indicator.set(mapOf(
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 15_000L))
            )).await()
        } else {
            indicator.delete().await()
        }
    }

    suspend fun sendCircleGroupMessage(
        uid: String,
        groupId: String,
        text: String,
        clientMessageId: String
    ) {
        requireSignedIn(uid)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_SOCIAL_MESSAGE_LENGTH)
        require(isValidSocialMessageId(clientMessageId))
        val database = database()
        val group = database.collection(CIRCLE_GROUPS).document(groupId)
        val message = group.collection(MESSAGES).document(clientMessageId)
        val created = database.runTransaction { transaction ->
            val groupSnapshot = transaction.get(group)
            val existing = transaction.get(message)
            check(groupSnapshot.exists() && uid in (groupSnapshot.get("members") as? List<*>).orEmpty()) {
                "This group is not available."
            }
            if (existing.exists()) {
                check(existing.getString("senderUid") == uid && existing.getString("text") == trimmed) {
                    "A different message already uses this ID."
                }
                return@runTransaction false
            }
            transaction.update(group, mapOf(
                "lastMessage" to trimmed,
                "lastMessageSenderUid" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageId" to clientMessageId
            ))
            transaction.set(message, mapOf(
                "senderUid" to uid,
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            true
        }.await()
        if (created) {
            try {
                PeopleMessageNotificationWork.enqueue(
                    applicationContext,
                    activeProfileNamespace(applicationContext),
                    uid,
                    groupId,
                    clientMessageId,
                    circleGroup = true
                )
            } catch (error: Exception) {
                Log.e(TAG, "A sent Circle group message could not be queued for notification delivery.", error)
            }
        }
    }

    suspend fun registerCallToken(uid: String, token: String) {
        requireSignedIn(uid)
        require(token.length in 32..4096) { "Firebase returned an invalid call notification token." }
        database().collection(CALL_TOKENS).document(uid)
            .collection(CALL_TOKEN_DEVICES).document(notificationTokenId(token))
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun registerPeopleMessageToken(uid: String, token: String) {
        requireSignedIn(uid)
        require(token.length in 32..4096) { "Firebase returned an invalid message notification token." }
        database().collection(MESSAGE_TOKENS).document(uid)
            .collection(CALL_TOKEN_DEVICES).document(notificationTokenId(token))
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun removePeopleMessageToken(uid: String, token: String) {
        requireSignedIn(uid)
        database().collection(MESSAGE_TOKENS).document(uid)
            .collection(CALL_TOKEN_DEVICES).document(notificationTokenId(token))
            .delete()
            .await()
    }

    suspend fun removePeopleMessageToken(uid: String) {
        requireSignedIn(uid)
        val firebaseApp = app ?: throw IllegalStateException("Firebase is not configured for People notifications.")
        val token = FirebaseMessaging.getInstance().token.await()
        removePeopleMessageToken(uid, token)
    }

    suspend fun peopleMessageMessagingToken(uid: String): String {
        requireSignedIn(uid)
        val firebaseApp = app ?: throw IllegalStateException("Firebase is not configured for People notifications.")
        return FirebaseMessaging.getInstance().token.await()
            .also { registerPeopleMessageToken(uid, it) }
    }

    suspend fun removeCallToken(uid: String, token: String) {
        requireSignedIn(uid)
        database().collection(CALL_TOKENS).document(uid)
            .collection(CALL_TOKEN_DEVICES).document(notificationTokenId(token))
            .delete()
            .await()
    }

    suspend fun messagingToken(uid: String): String {
        requireSignedIn(uid)
        val firebaseApp = app ?: throw IllegalStateException("Firebase is not configured for People calls.")
        return FirebaseMessaging.getInstance().token.await()
            .also { registerCallToken(uid, it) }
    }

    suspend fun createCall(
        uid: String,
        chatId: String,
        peerUid: String,
        mediaType: String,
        offerSdp: String,
        ringExpiresAtMillis: Long,
        expiresAtMillis: Long
    ): String {
        requireSignedIn(uid)
        require(peerUid.isNotBlank() && peerUid != uid)
        require(mediaType in PEOPLE_CALL_MEDIA_TYPES) { "This call type is not supported." }
        require(offerSdp.isNotBlank() && offerSdp.length <= MAX_CALL_SDP_LENGTH)
        require(ringExpiresAtMillis > System.currentTimeMillis() && expiresAtMillis > ringExpiresAtMillis)
        val call = database().collection(CHATS).document(chatId)
            .collection(CALLS).document(UUID.randomUUID().toString())
        call.set(mapOf(
            "callerUid" to uid,
            "calleeUid" to peerUid,
            "mediaType" to mediaType,
            "status" to "ringing",
            "offer" to mapOf("type" to "offer", "sdp" to offerSdp),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "ringExpiresAt" to Timestamp(Date(ringExpiresAtMillis)),
            "expiresAt" to Timestamp(Date(expiresAtMillis))
        )).await()
        return call.id
    }

    suspend fun getCall(uid: String, chatId: String, callId: String): PeopleCallRecord? {
        requireSignedIn(uid)
        val snapshot = callReference(chatId, callId).get().await()
        return if (snapshot.exists()) snapshot.toPeopleCallRecord(chatId) else null
    }

    fun listenCall(
        uid: String,
        chatId: String,
        callId: String,
        onChange: (PeopleCallRecord?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return callReference(chatId, callId).addSnapshotListener { snapshot, listenerError ->
            if (listenerError != null) {
                onError(listenerError)
                return@addSnapshotListener
            }
            try {
                onChange(snapshot?.takeIf { it.exists() }?.toPeopleCallRecord(chatId))
            } catch (exception: Exception) {
                onError(exception)
            }
        }
    }

    fun listenCallCandidates(
        uid: String,
        chatId: String,
        callId: String,
        remoteIsCallee: Boolean,
        onChange: (List<PeopleCallCandidate>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        val collection = if (remoteIsCallee) CALLEE_CANDIDATES else CALLER_CANDIDATES
        return callReference(chatId, callId).collection(collection)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, listenerError ->
                if (listenerError != null) {
                    onError(listenerError)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    onError(IllegalStateException("Could not load call connection data."))
                    return@addSnapshotListener
                }
                try {
                    onChange(snapshot.documents.map { document ->
                        PeopleCallCandidate(
                            id = document.id,
                            sdp = document.getString("sdp")
                                ?: error("A call candidate is missing its SDP."),
                            sdpMid = document.getString("sdpMid"),
                            sdpMLineIndex = document.getLong("sdpMLineIndex")?.toInt()
                                ?: error("A call candidate is missing its media line.")
                        )
                    })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    suspend fun addCallCandidate(
        uid: String,
        chatId: String,
        callId: String,
        localIsCaller: Boolean,
        candidate: PeopleCallCandidate,
        expiresAtMillis: Long
    ) {
        requireSignedIn(uid)
        require(candidate.sdp.isNotBlank() && candidate.sdp.length <= MAX_CALL_CANDIDATE_LENGTH)
        val collection = if (localIsCaller) CALLER_CANDIDATES else CALLEE_CANDIDATES
        callReference(chatId, callId).collection(collection).document()
            .set(mapOf(
                "sdp" to candidate.sdp,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(expiresAtMillis))
            )).await()
    }

    suspend fun answerCall(uid: String, chatId: String, callId: String, answerSdp: String) {
        requireSignedIn(uid)
        require(answerSdp.isNotBlank() && answerSdp.length <= MAX_CALL_SDP_LENGTH)
        callReference(chatId, callId).update(mapOf(
            "status" to "accepted",
            "answer" to mapOf("type" to "answer", "sdp" to answerSdp),
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun updateCallStatus(uid: String, chatId: String, callId: String, status: String) {
        requireSignedIn(uid)
        require(status in setOf("rejected", "cancelled", "missed", "ended"))
        callReference(chatId, callId).update(mapOf(
            "status" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun notifyIncomingCall(uid: String, chatId: String, callId: String) {
        requireSignedIn(uid)
        callApiClient.notifyIncomingCall(uid, chatId, callId)
    }

    suspend fun notifyNewMessage(uid: String, chatId: String, messageId: String) {
        requireSignedIn(uid)
        callApiClient.notifyNewMessage(uid, chatId, messageId)
    }

    suspend fun notifyCircleGroupMessage(uid: String, groupId: String, messageId: String) {
        requireSignedIn(uid)
        callApiClient.notifyCircleGroupMessage(uid, groupId, messageId)
    }

    suspend fun callIceServers(uid: String, chatId: String, callId: String): List<PeopleIceServer> {
        requireSignedIn(uid)
        return callApiClient.iceServers(uid, chatId, callId)
    }

    suspend fun sendMessage(uid: String, conversationId: String, text: String, clientMessageId: String) {
        requireSignedIn(uid)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_SOCIAL_MESSAGE_LENGTH) {
            "Messages must contain 1–2,000 characters."
        }
        require(isValidSocialMessageId(clientMessageId)) { "The message ID is invalid." }
        val database = database()
        val conversation = database.collection(CHATS).document(conversationId)
        val message = conversation.collection(MESSAGES).document(clientMessageId)
        val created = database.runTransaction { transaction ->
            val conversationSnapshot = transaction.get(conversation)
            val existingMessage = transaction.get(message)
            check(conversationSnapshot.exists()) { "This conversation is not available." }
            val members = conversationSnapshot.get("members") as? List<*>
                ?: error("A conversation is missing its participants.")
            check(uid in members) { "This conversation is not available." }
            if (existingMessage.exists()) {
                check(
                    existingMessage.getString("senderUid") == uid
                        && existingMessage.getString("text") == trimmed
                ) { "A different message already uses this ID." }
                return@runTransaction false
            }
            val peerUid = members.filterIsInstance<String>().firstOrNull { it != uid }
                ?: error("A conversation is missing the other participant.")
            val unreadCounts = conversationSnapshot.get("unreadCounts") as? Map<*, *>
            val ownUnreadCount = (unreadCounts?.get(uid) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            val peerUnreadCount = (unreadCounts?.get(peerUid) as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
            transaction.update(conversation, mapOf(
                "lastMessage" to trimmed,
                "lastMessageSenderUid" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageId" to message.id,
                "unreadCounts" to mapOf(uid to ownUnreadCount, peerUid to peerUnreadCount + 1L)
            ))
            transaction.set(message, mapOf(
                "senderUid" to uid,
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            true
        }.await()
        if (created) {
            try {
                PeopleMessageNotificationWork.enqueue(
                    applicationContext,
                    activeProfileNamespace(applicationContext),
                    uid,
                    conversationId,
                    clientMessageId
                )
            } catch (error: Exception) {
                Log.e(TAG, "A sent People message could not be queued for notification delivery.", error)
            }
        }
    }

    suspend fun blockUser(uid: String, peerUid: String) {
        requireSignedIn(uid)
        require(peerUid.isNotBlank() && peerUid != uid)
        database().collection(BLOCKS).document(uid)
            .collection(BLOCKED_USERS).document(peerUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun reportUser(uid: String, peerUid: String, conversationId: String, reason: String) {
        val trimmed = validatedReportReason(uid, peerUid, reason)
        database().collection(REPORTS).add(mapOf(
            "reporterUid" to uid,
            "targetUid" to peerUid,
            "conversationId" to conversationId,
            "reason" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun reportContactRequest(uid: String, peerUid: String, reason: String) {
        val trimmed = validatedReportReason(uid, peerUid, reason)
        database().collection(REPORTS).add(mapOf(
            "reporterUid" to uid,
            "targetUid" to peerUid,
            "contactRequestId" to peerUid,
            "reason" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun reportPublicProfile(uid: String, peerUid: String, reason: String) {
        val trimmed = validatedReportReason(uid, peerUid, reason)
        database().collection(REPORTS).add(mapOf(
            "reporterUid" to uid,
            "targetUid" to peerUid,
            "profileUid" to peerUid,
            "reason" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    private fun validatedReportReason(uid: String, peerUid: String, reason: String): String {
        requireSignedIn(uid)
        val trimmed = reason.trim()
        require(peerUid.isNotBlank() && peerUid != uid && trimmed.length in 3..500) {
            "Choose a reason between 3 and 500 characters."
        }
        return trimmed
    }

    private fun validateProfile(profile: PublicPersonProfile) {
        require(profile.uid.isNotBlank())
        require(profile.displayName.isNotBlank() && profile.displayName.length <= 60)
        require(profile.ageCohort == "adult")
        require(profile.ageBand in setOf("18–24", "25–34", "35+"))
        require(profile.gender.isNotBlank() && profile.gender.length <= 40)
        require(profile.country.isNotBlank() && profile.country.length <= 80)
        require(profile.bio.length <= 500 && profile.interests.length <= 300)
        require(profile.contactPolicy in PeopleContactPolicy.values())
    }

    private fun callReference(chatId: String, callId: String) =
        database().collection(CHATS).document(chatId).collection(CALLS).document(callId)

    private fun DocumentSnapshot.toPeopleCallRecord(chatId: String): PeopleCallRecord {
        fun description(key: String, required: Boolean): PeopleCallDescription? {
            val fields = get(key) as? Map<*, *> ?: if (required) error("A call is missing its $key.") else return null
            if (!required && fields.isEmpty()) return null
            return PeopleCallDescription(
                type = fields["type"] as? String ?: error("A call has an invalid $key type."),
                sdp = fields["sdp"] as? String ?: error("A call is missing its $key SDP.")
            )
        }
        return PeopleCallRecord(
            id = id,
            chatId = chatId,
            callerUid = getString("callerUid") ?: error("A call is missing its caller."),
            calleeUid = getString("calleeUid") ?: error("A call is missing its recipient."),
            mediaType = (getString("mediaType") ?: "audio").also {
                require(it in PEOPLE_CALL_MEDIA_TYPES) { "A call has an unsupported media type." }
            },
            status = getString("status") ?: error("A call is missing its status."),
            offer = description("offer", true)!!,
            answer = description("answer", false),
            ringExpiresAtMillis = getTimestamp("ringExpiresAt")?.toDate()?.time
                ?: error("A call is missing its ring expiry."),
            expiresAtMillis = getTimestamp("expiresAt")?.toDate()?.time
                ?: error("A call is missing its expiry.")
        )
    }

    private fun PublicPersonProfile.toFirestoreMap(): Map<String, Any> = mapOf(
        "displayName" to displayName,
        "ageBand" to ageBand,
        "ageCohort" to ageCohort,
        "gender" to gender,
        "country" to country,
        "bio" to bio,
        "interests" to interests,
        "contactPolicy" to contactPolicy.firestoreValue,
        "isPublic" to isPublic
    )

    private fun newConversationData(
        createdBy: String,
        firstProfile: PublicPersonProfile,
        secondProfile: PublicPersonProfile
    ): Map<String, Any> {
        val members = listOf(firstProfile.uid, secondProfile.uid).sorted()
        return mapOf(
            "members" to members,
            "ageCohort" to firstProfile.ageCohort,
            "memberProfiles" to mapOf(
                firstProfile.uid to firstProfile.toFirestoreMap().filterKeys { it != "isPublic" },
                secondProfile.uid to secondProfile.toFirestoreMap().filterKeys { it != "isPublic" }
            ),
            "createdBy" to createdBy,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastMessage" to "",
            "lastMessageSenderUid" to "",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "lastMessageId" to "",
            "unreadCounts" to mapOf(firstProfile.uid to 0L, secondProfile.uid to 0L)
        )
    }

    private fun DocumentSnapshot.toPublicPersonProfile(): PublicPersonProfile {
        fun requiredString(field: String): String =
            getString(field)?.takeIf(String::isNotBlank)
                ?: error("A public profile is missing $field.")

        return PublicPersonProfile(
            uid = id,
            displayName = requiredString("displayName"),
            ageBand = requiredString("ageBand"),
            ageCohort = requiredString("ageCohort"),
            gender = requiredString("gender"),
            country = requiredString("country"),
            bio = getString("bio").orEmpty(),
            interests = getString("interests").orEmpty(),
            isPublic = getBoolean("isPublic") ?: error("A public profile is missing its visibility setting."),
            contactPolicy = PeopleContactPolicy.fromFirestore(getString("contactPolicy"))
        )
    }

    private fun DocumentSnapshot.toSocialContactRequest(): SocialContactRequest {
        val profile = get("senderProfile") as? Map<*, *>
            ?: error("A contact request is missing the sender profile.")
        fun requiredString(key: String) = profile[key] as? String
            ?: error("A contact request profile is missing $key.")
        val senderUid = getString("senderUid")
            ?: error("A contact request is missing its sender.")
        return SocialContactRequest(
            id = senderUid,
            sender = PublicPersonProfile(
                uid = senderUid,
                displayName = requiredString("displayName"),
                ageBand = requiredString("ageBand"),
                ageCohort = requiredString("ageCohort"),
                gender = requiredString("gender"),
                country = requiredString("country"),
                bio = profile["bio"] as? String ?: "",
                interests = profile["interests"] as? String ?: "",
                isPublic = false,
                contactPolicy = PeopleContactPolicy.fromFirestore(profile["contactPolicy"] as? String)
            )
        )
    }

    private fun DocumentSnapshot.toSocialConversation(uid: String): SocialConversation {
        val members = get("members") as? List<*>
            ?: error("A conversation is missing its participants.")
        val peerUid = members.filterIsInstance<String>().firstOrNull { it != uid }
            ?: error("A conversation is missing the other participant.")
        val memberProfiles = get("memberProfiles") as? Map<*, *>
            ?: error("A conversation is missing its participant profiles.")
        val peerFields = memberProfiles[peerUid] as? Map<*, *>
            ?: error("A conversation is missing the other participant's profile.")
        fun peerString(key: String) = peerFields[key] as? String
            ?: error("A conversation profile is missing $key.")
        val ageCohort = getString("ageCohort")
            ?: error("A conversation is missing its age group.")
        val peer = PublicPersonProfile(
            uid = peerUid,
            displayName = peerString("displayName"),
            ageBand = peerString("ageBand"),
            ageCohort = ageCohort,
            gender = peerString("gender"),
            country = peerString("country"),
            bio = peerString("bio"),
            interests = peerString("interests"),
            isPublic = false,
            contactPolicy = PeopleContactPolicy.fromFirestore(peerFields["contactPolicy"] as? String)
        )
        return SocialConversation(
            id = id,
            peer = peer,
            lastMessage = getString("lastMessage").orEmpty(),
            lastMessageAtMillis = getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L,
            unreadCount = ((get("unreadCounts") as? Map<*, *>)?.get(uid) as? Number)
                ?.toInt()?.coerceAtLeast(0) ?: 0
        )
    }

    private fun DocumentSnapshot.toCircleGroup(): CircleGroup {
        val members = (get("members") as? List<*>)?.filterIsInstance<String>()
            ?: error("A Circle group is missing its members.")
        val admins = (get("admins") as? List<*>)?.filterIsInstance<String>()
            ?: error("A Circle group is missing its admins.")
        return CircleGroup(
            id = id,
            name = getString("name") ?: error("A Circle group is missing its name."),
            members = members,
            admins = admins,
            lastMessage = getString("lastMessage").orEmpty(),
            lastMessageAtMillis = getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L
        )
    }

    private companion object {
        const val TAG = "PeopleRepository"
        const val PROFILES = "publicProfiles"
        const val CHATS = "peopleChats"
        const val MESSAGES = "messages"
        const val BLOCKS = "peopleBlocks"
        const val BLOCKED_USERS = "blockedUsers"
        const val REPORTS = "peopleReports"
        const val CONTACT_REQUESTS = "peopleContactRequests"
        const val INCOMING_REQUESTS = "incoming"
        const val CONTACT_APPROVALS = "peopleContactApprovals"
        const val CIRCLE_GROUPS = "circleGroups"
        const val CIRCLE_GROUP_INVITES = "invites"
        const val TYPING_INDICATORS = "typingIndicators"
        const val CALLS = "calls"
        const val CALLER_CANDIDATES = "callerCandidates"
        const val CALLEE_CANDIDATES = "calleeCandidates"
        const val CALL_TOKENS = "peopleCallTokens"
        const val MESSAGE_TOKENS = "peopleMessageTokens"
        const val CALL_TOKEN_DEVICES = "devices"
        const val MAX_DISCOVERED_PEOPLE = 100
        const val MAX_CONVERSATIONS = 100
        const val MAX_CONTACT_REQUESTS = 100
        const val MAX_SOCIAL_MESSAGE_LENGTH = 2_000
        const val MAX_CIRCLE_GROUP_MEMBERS = 32
        const val MAX_CALL_SDP_LENGTH = 65_536
        const val MAX_CALL_CANDIDATE_LENGTH = 4_096

        fun notificationTokenId(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
