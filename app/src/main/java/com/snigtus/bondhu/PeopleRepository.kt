package com.snigtus.dost

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

internal data class PeopleProfileDraft(
    val country: String = "",
    val bio: String = "",
    val interests: String = ""
)

internal enum class PeopleContactPolicy(val firestoreValue: String) {
    EVERYONE("everyone"),
    REQUESTS("requests"),
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

internal data class SocialConversation(
    val id: String,
    val peer: PublicPersonProfile,
    val lastMessage: String,
    val lastMessageAtMillis: Long,
    val unreadCount: Int = 0
)

internal data class SocialContactRequest(
    val id: String,
    val sender: PublicPersonProfile
)

internal sealed class PeopleContactStartResult {
    data class Conversation(val value: SocialConversation) : PeopleContactStartResult()
    object RequestSent : PeopleContactStartResult()
}

internal data class SocialChatMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAtMillis: Long
)

internal fun socialAgeCohort(age: String): String? {
    val years = age.toIntOrNull() ?: return null
    if (years !in MINIMUM_ACCOUNT_AGE..MAX_PROFILE_AGE) return null
    return "adult"
}

internal fun socialAgeBand(age: String): String? {
    val years = age.toIntOrNull() ?: return null
    if (years !in ROMANCE_MINIMUM_AGE..MAX_PROFILE_AGE) return null
    return when {
        years <= 15 -> "13–15"
        years <= 17 -> "16–17"
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
    private val app = FirebaseApp.getApps(context.applicationContext).firstOrNull()
    private val firestore = app?.let(FirebaseFirestore::getInstance)

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
                check(currentProfile.isPublic && currentPeer.isPublic
                    && currentProfile.ageCohort == currentPeer.ageCohort
                ) { "This profile is no longer available for a chat." }
                when (currentPeer.contactPolicy) {
                    PeopleContactPolicy.EVERYONE -> {
                        transaction.set(conversationRef, newConversationData(uid, currentProfile, currentPeer))
                        PeopleContactStartResult.Conversation(
                            SocialConversation(conversationRef.id, currentPeer, "", 0L)
                        )
                    }
                    PeopleContactPolicy.REQUESTS -> {
                        val existingRequest = transaction.get(requestRef)
                        check(!existingRequest.exists()) {
                            if (existingRequest.getString("status") == "pending") {
                                "A contact request is already pending."
                            } else {
                                "This contact request was already handled."
                            }
                        }
                        transaction.set(requestRef, mapOf(
                            "senderUid" to uid,
                            "recipientUid" to peer.uid,
                            "conversationId" to conversationRef.id,
                            "status" to "pending",
                            "senderProfile" to currentProfile.toFirestoreMap().filterKeys { it != "isPublic" },
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                        PeopleContactStartResult.RequestSent
                    }
                    PeopleContactPolicy.NOBODY ->
                        error("This person is not accepting new chats.")
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
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(MAX_CHAT_MESSAGES.toLong())
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
                    onChange(snapshot.documents.map { document ->
                        SocialChatMessage(
                            id = document.id,
                            senderUid = document.getString("senderUid")
                                ?: error("A chat message is missing its sender."),
                            text = document.getString("text")
                                ?: error("A chat message is missing its text."),
                            createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    suspend fun sendMessage(uid: String, conversationId: String, text: String) {
        requireSignedIn(uid)
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_SOCIAL_MESSAGE_LENGTH) {
            "Messages must contain 1–2,000 characters."
        }
        val database = database()
        val conversation = database.collection(CHATS).document(conversationId)
        val message = conversation.collection(MESSAGES).document()
        database.runTransaction { transaction ->
            val conversationSnapshot = transaction.get(conversation)
            check(conversationSnapshot.exists()) { "This conversation is not available." }
            val members = conversationSnapshot.get("members") as? List<*>
                ?: error("A conversation is missing its participants.")
            check(uid in members) { "This conversation is not available." }
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
        }.await()
    }

    suspend fun blockUser(uid: String, peerUid: String) {
        requireSignedIn(uid)
        require(peerUid.isNotBlank() && peerUid != uid)
        database().collection(BLOCKS).document(uid)
            .collection(BLOCKED_USERS).document(peerUid)
            .set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun reportUser(uid: String, peerUid: String, conversationId: String, reason: String) {
        requireSignedIn(uid)
        val trimmed = reason.trim()
        require(peerUid.isNotBlank() && peerUid != uid && trimmed.length in 3..500) {
            "Choose a reason between 3 and 500 characters."
        }
        database().collection(REPORTS).add(mapOf(
            "reporterUid" to uid,
            "targetUid" to peerUid,
            "conversationId" to conversationId,
            "reason" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun reportContactRequest(uid: String, peerUid: String, reason: String) {
        requireSignedIn(uid)
        val trimmed = reason.trim()
        require(peerUid.isNotBlank() && peerUid != uid && trimmed.length in 3..500) {
            "Choose a reason between 3 and 500 characters."
        }
        database().collection(REPORTS).add(mapOf(
            "reporterUid" to uid,
            "targetUid" to peerUid,
            "contactRequestId" to peerUid,
            "reason" to trimmed,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
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

    private companion object {
        const val PROFILES = "publicProfiles"
        const val CHATS = "peopleChats"
        const val MESSAGES = "messages"
        const val BLOCKS = "peopleBlocks"
        const val BLOCKED_USERS = "blockedUsers"
        const val REPORTS = "peopleReports"
        const val CONTACT_REQUESTS = "peopleContactRequests"
        const val INCOMING_REQUESTS = "incoming"
        const val CONTACT_APPROVALS = "peopleContactApprovals"
        const val MAX_DISCOVERED_PEOPLE = 100
        const val MAX_CONVERSATIONS = 100
        const val MAX_CHAT_MESSAGES = 500
        const val MAX_CONTACT_REQUESTS = 100
        const val MAX_SOCIAL_MESSAGE_LENGTH = 2_000
    }
}
