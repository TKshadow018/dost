package com.snigtus.dost

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

internal data class CipherChat(
    val id: String,
    val members: List<String>,
    val createdBy: String,
    val salt: String,
    val keyCheck: String
)

internal data class StoredCipherMessage(
    val id: String,
    val senderUid: String,
    val encrypted: CipherEncryptedMessage,
    val createdAtMillis: Long
)

internal class CipherRepository(context: android.content.Context) {
    private val firebaseApp = FirebaseApp.getApps(context).firstOrNull()
    private val database: FirebaseFirestore = firebaseApp
        ?.let { FirebaseFirestore.getInstance(it) }
        ?: FirebaseFirestore.getInstance()
    private val callApiClient = PeopleCallApiClient(context, firebaseApp)

    suspend fun getChat(uid: String, chatId: String): CipherChat? {
        require(uid.isNotBlank() && isValidCipherChatId(chatId))
        val snapshot = database.collection(CHATS).document(chatId).get().await()
        if (!snapshot.exists()) return null
        val chat = snapshot.toCipherChat()
        check(uid in chat.members) { "This Cipher chat is unavailable." }
        return chat
    }

    suspend fun createChat(
        uid: String,
        peerUid: String,
        circleChatId: String,
        salt: ByteArray,
        keyCheck: String
    ): CipherChat {
        require(uid.isNotBlank() && peerUid.isNotBlank() && peerUid != uid)
        require(isValidCipherChatId(circleChatId) && salt.size == 16 && keyCheck.isNotBlank())
        val members = listOf(uid, peerUid).sorted()
        val reference = database.collection(CHATS).document(circleChatId)
        val existing = reference.get().await()
        if (existing.exists()) return existing.toCipherChat().also {
            check(uid in it.members) { "This Cipher chat is unavailable." }
        }
        val fields = mapOf(
            "members" to members,
            "createdBy" to uid,
            "sourceCircleChatId" to circleChatId,
            "salt" to java.util.Base64.getEncoder().encodeToString(salt),
            "keyCheck" to keyCheck,
            "v" to CIPHER_VERSION,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            reference.set(fields).await()
            CipherChat(
                id = circleChatId,
                members = members,
                createdBy = uid,
                salt = fields.getValue("salt") as String,
                keyCheck = keyCheck
            )
        } catch (error: Exception) {
            val afterRace = reference.get().await()
            if (!afterRace.exists()) throw error
            afterRace.toCipherChat().also {
                check(uid in it.members) { "This Cipher chat is unavailable." }
            }
        }
    }

    suspend fun sendMessage(uid: String, chatId: String, message: StoredCipherMessage) {
        require(uid == message.senderUid && isValidCipherChatId(chatId))
        require(isValidSocialMessageId(message.id))
        val reference = database.collection(CHATS).document(chatId)
            .collection(MESSAGES).document(message.id)
        reference.set(mapOf(
            "ct" to message.encrypted.ct,
            "nonce" to message.encrypted.nonce,
            "v" to message.encrypted.version,
            "senderUid" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun notifyNewMessage(uid: String, chatId: String, messageId: String) {
        callApiClient.notifyCipherMessage(uid, chatId, messageId)
    }

    fun listenMessages(
        uid: String,
        chatId: String,
        onChange: (List<StoredCipherMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        require(uid.isNotBlank() && isValidCipherChatId(chatId))
        return database.collection(CHATS).document(chatId).collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                try {
                    onChange(snapshot?.documents.orEmpty().map { it.toCipherMessage() })
                } catch (exception: Exception) {
                    onError(exception)
                }
            }
    }

    private fun DocumentSnapshot.toCipherChat(): CipherChat {
        val members = get("members") as? List<*>
            ?: error("Cipher chat has invalid members.")
        check(members.size == 2 && members.all { it is String })
        return CipherChat(
            id = id,
            members = members.filterIsInstance<String>(),
            createdBy = getString("createdBy") ?: error("Cipher chat has no creator."),
            salt = getString("salt") ?: error("Cipher chat has no salt."),
            keyCheck = getString("keyCheck") ?: error("Cipher chat has no key check.")
        )
    }

    private fun DocumentSnapshot.toCipherMessage(): StoredCipherMessage {
        val encrypted = CipherEncryptedMessage(
            ct = getString("ct") ?: error("Cipher message has no ciphertext."),
            nonce = getString("nonce") ?: error("Cipher message has no nonce."),
            version = getLong("v")?.toInt() ?: error("Cipher message has no version.")
        )
        return StoredCipherMessage(
            id = id,
            senderUid = getString("senderUid") ?: error("Cipher message has no sender."),
            encrypted = encrypted,
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L
        )
    }

    private companion object {
        const val CHATS = "cipherChats"
        const val MESSAGES = "messages"
    }
}

internal fun isValidCipherChatId(chatId: String): Boolean =
    chatId.matches(Regex("[A-Za-z0-9_-]{1,128}"))
