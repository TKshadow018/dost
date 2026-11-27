package com.snigtus.dost

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

internal enum class SecureOrgRole(val value: String) {
    OWNER("owner"),
    MANAGER("manager"),
    MEMBER("member");

    companion object {
        fun parse(value: String?): SecureOrgRole =
            entries.firstOrNull { it.value == value }
                ?: error("Invalid organization role")
    }
}

internal object SuperSecureSession {
    @Volatile
    var active: Boolean = false
}

internal const val MAX_SECURE_ORG_MEMBERS = 25

internal fun secureCanAddMembers(memberCount: Int): Boolean =
    memberCount in 1 until MAX_SECURE_ORG_MEMBERS

internal fun superSecureSessionAllowsAi(): Boolean = !SuperSecureSession.active
internal fun superSecureSessionAllowsTelemetry(): Boolean = !SuperSecureSession.active

internal data class SecureOrgSummary(
    val id: String,
    val name: String,
    val role: SecureOrgRole,
    val memberCount: Int
)

internal data class SecureOrgMember(
    val uid: String,
    val displayName: String,
    val role: SecureOrgRole
)

internal data class SecureOrgMessage(
    val id: String,
    val senderUid: String,
    val text: String,
    val createdAtMillis: Long
)

internal data class SecureOrgAuditEntry(
    val id: String,
    val actorUid: String,
    val targetUid: String,
    val action: String,
    val createdAtMillis: Long
)

internal data class SecureOrgConversation(
    val id: String,
    val peerUid: String? = null,
    val title: String = "Group chat"
)

internal sealed interface SecureMembershipState {
    data object Checking : SecureMembershipState
    data class Active(val member: SecureOrgMember) : SecureMembershipState
    data object Removed : SecureMembershipState
    data object Unavailable : SecureMembershipState
}

internal fun secureCanManageMembers(role: SecureOrgRole): Boolean =
    role == SecureOrgRole.OWNER || role == SecureOrgRole.MANAGER

internal fun secureCanManageRoles(role: SecureOrgRole): Boolean =
    role == SecureOrgRole.OWNER

internal fun secureCanRemoveMember(actor: SecureOrgRole, target: SecureOrgRole, isSelf: Boolean): Boolean =
    !isSelf && target != SecureOrgRole.OWNER &&
        (target == SecureOrgRole.MEMBER || actor == SecureOrgRole.OWNER)

internal class SuperSecureRepository(context: Context) {
    private val app = FirebaseApp.getApps(context.applicationContext).firstOrNull()
    private val firestore = app?.let { FirebaseFirestore.getInstance(it) }

    private fun database(): FirebaseFirestore =
        firestore ?: throw IllegalStateException("Firebase is not configured.")

    private fun requireSignedIn(uid: String) {
        val currentUid = app?.let { FirebaseAuth.getInstance(it).currentUser?.uid }
        check(uid.isNotBlank() && uid == currentUid) { "Sign in to use Super Secure." }
    }

    suspend fun openOrganization(uid: String, organizationId: String): SecureOrgSummary {
        requireSignedIn(uid)
        require(organizationId.matches(ORG_ID_PATTERN)) { "Enter a valid organization ID." }
        val organization = organizationRef(organizationId)
        val member = organization.collection(MEMBERS).document(uid).get(Source.SERVER).await()
        check(member.exists() && member.getString("status") == ACTIVE) {
            "You are not an active member of this organization."
        }
        val orgSnapshot = organization.get(Source.SERVER).await()
        check(orgSnapshot.exists()) { "This organization is unavailable." }
        return SecureOrgSummary(
            id = organizationId,
            name = orgSnapshot.getString("name") ?: error("Organization name is unavailable."),
            role = SecureOrgRole.parse(member.getString("role")),
            memberCount = orgSnapshot.getLong("memberCount")?.toInt()
                ?.takeIf { it in 1..MAX_SECURE_ORG_MEMBERS }
                ?: error("Organization membership is unavailable.")
        )
    }

    suspend fun createOrganization(uid: String, name: String, ownerName: String): SecureOrgSummary {
        requireSignedIn(uid)
        val trimmedName = name.trim()
        require(trimmedName.length in 2..MAX_ORG_NAME_LENGTH) {
            "Organization name must be between 2 and $MAX_ORG_NAME_LENGTH characters."
        }
        val safeOwnerName = ownerName.trim().take(MAX_MEMBER_NAME_LENGTH)

        val organization = database().collection(ORGS).document()
        val auditId = UUID.randomUUID().toString()
        val batch = database().batch()
        batch.set(organization, mapOf(
            "name" to trimmedName,
            "ownerUid" to uid,
            "memberCount" to 1,
            "memberCountAuditEventId" to auditId,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.set(organization.collection(MEMBERS).document(uid), memberFields(
            uid = uid,
            displayName = safeOwnerName.ifBlank { "Owner" },
            role = SecureOrgRole.OWNER,
            actorUid = uid,
            auditEventId = auditId,
            organizationName = trimmedName
        ))
        batch.set(organization.collection(GROUP_CHATS).document(MAIN_GROUP_CHAT), mapOf(
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.set(organization.collection(AUDIT_LOG).document(auditId), auditFields(
            uid,
            uid,
            "org_created"
        ))
        batch.commit().await()
        return SecureOrgSummary(organization.id, trimmedName, SecureOrgRole.OWNER, 1)
    }

    fun listenMembership(
        uid: String,
        organizationId: String,
        onChange: (SecureMembershipState) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return organizationRef(organizationId).collection(MEMBERS).document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    error != null -> onChange(
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
                            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                        ) SecureMembershipState.Removed else SecureMembershipState.Unavailable
                    )
                    snapshot == null || snapshot.metadata.isFromCache ->
                        onChange(SecureMembershipState.Unavailable)
                    !snapshot.exists() || snapshot.getString("status") != ACTIVE ->
                        onChange(SecureMembershipState.Removed)
                    else -> onChange(SecureMembershipState.Active(snapshot.toSecureMember()))
                }
            }
    }

    fun listenMembers(
        uid: String,
        organizationId: String,
        onChange: (List<SecureOrgMember>?) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return organizationRef(organizationId).collection(MEMBERS)
            .whereEqualTo("status", ACTIVE)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null || snapshot.metadata.isFromCache) {
                    onChange(null)
                } else {
                    onChange(snapshot.documents
                        .filter { it.getString("status") == ACTIVE }
                        .map(DocumentSnapshot::toSecureMember)
                        .sortedBy { it.displayName.lowercase() })
                }
            }
    }

    fun listenMessages(
        uid: String,
        organizationId: String,
        conversation: SecureOrgConversation,
        onChange: (List<SecureOrgMessage>?) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return messagesCollection(organizationId, conversation).orderBy("createdAt")
            .limitToLast(MAX_VISIBLE_MESSAGES.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null || snapshot.metadata.isFromCache) {
                    onChange(null)
                } else {
                    onChange(snapshot.documents.map { it.toSecureMessage() })
                }
            }
    }

    fun listenAuditLog(
        uid: String,
        organizationId: String,
        onChange: (List<SecureOrgAuditEntry>?) -> Unit
    ): ListenerRegistration {
        requireSignedIn(uid)
        return organizationRef(organizationId).collection(AUDIT_LOG)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_AUDIT_ENTRIES.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null || snapshot.metadata.isFromCache) {
                    onChange(null)
                } else {
                    onChange(snapshot.documents.map { it.toSecureAuditEntry() })
                }
            }
    }

    suspend fun addMember(
        uid: String,
        organizationId: String,
        memberUid: String,
        displayName: String,
        role: SecureOrgRole
    ) {
        requireSignedIn(uid)
        require(memberUid.matches(UID_PATTERN) && memberUid != uid) { "Enter another valid Firebase UID." }
        require(displayName.trim().length <= MAX_MEMBER_NAME_LENGTH) { "Member name is too long." }
        require(role != SecureOrgRole.OWNER) { "A new owner cannot be added." }
        val organization = organizationRef(organizationId)
        val memberRef = organization.collection(MEMBERS).document(memberUid)
        val auditId = UUID.randomUUID().toString()
        database().runTransaction { transaction ->
            val orgSnapshot = transaction.get(organization)
            val existingMember = transaction.get(memberRef)
            check(orgSnapshot.exists()) { "This organization is unavailable." }
            check(!existingMember.exists() || existingMember.getString("status") == REMOVED) {
                "This person is already a member."
            }
            val memberCount = orgSnapshot.getLong("memberCount")?.toInt()
                ?: error("Organization membership is unavailable.")
            check(secureCanAddMembers(memberCount)) {
                "This organization has reached its $MAX_SECURE_ORG_MEMBERS-member limit."
            }
            val name = displayName.trim().ifBlank { "Member" }
            val fields = memberFields(
                uid = memberUid,
                displayName = name,
                role = role,
                actorUid = uid,
                auditEventId = auditId,
                organizationName = orgSnapshot.getString("name").orEmpty()
            )
            if (existingMember.exists()) {
                transaction.update(memberRef, fields - "createdAt")
            } else {
                transaction.set(memberRef, fields)
            }
            transaction.update(organization, mapOf(
                "memberCount" to memberCount + 1,
                "memberCountAuditEventId" to auditId
            ))
            val auditAction = when {
                role == SecureOrgRole.MEMBER -> "member_added"
                else -> "manager_added"
            }
            transaction.set(
                organization.collection(AUDIT_LOG).document(auditId),
                auditFields(uid, memberUid, auditAction)
            )
            null
        }.await()
    }

    suspend fun updateMember(
        uid: String,
        organizationId: String,
        memberUid: String,
        action: MemberAction
    ) {
        requireSignedIn(uid)
        require(memberUid != uid) { "You cannot change your own owner membership." }
        val organization = organizationRef(organizationId)
        val memberRef = organization.collection(MEMBERS).document(memberUid)
        val auditId = UUID.randomUUID().toString()
        database().runTransaction { transaction ->
            val orgSnapshot = transaction.get(organization)
            val member = transaction.get(memberRef)
            check(member.exists() && member.getString("status") == ACTIVE) {
                "This person is not an active member."
            }
            check(orgSnapshot.exists()) { "This organization is unavailable." }
            val previousRole = SecureOrgRole.parse(member.getString("role"))
            check(previousRole != SecureOrgRole.OWNER) { "The organization owner cannot be removed or changed." }
            val (role, status, auditAction) = when (action) {
                MemberAction.PROMOTE -> {
                    check(previousRole == SecureOrgRole.MEMBER) { "This person is already a manager." }
                    Triple(SecureOrgRole.MANAGER, ACTIVE, "manager_promoted")
                }
                MemberAction.DEMOTE -> {
                    check(previousRole == SecureOrgRole.MANAGER) { "This person is not a manager." }
                    Triple(SecureOrgRole.MEMBER, ACTIVE, "manager_demoted")
                }
                MemberAction.REMOVE -> Triple(
                    previousRole,
                    REMOVED,
                    if (previousRole == SecureOrgRole.MANAGER) "manager_removed" else "member_removed"
                )
            }
            transaction.update(memberRef, mapOf(
                "role" to role.value,
                "status" to status,
                "actorUid" to uid,
                "auditEventId" to auditId,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            transaction.set(
                organization.collection(AUDIT_LOG).document(auditId),
                auditFields(uid, memberUid, auditAction)
            )
            if (action == MemberAction.REMOVE) {
                val memberCount = orgSnapshot.getLong("memberCount")?.toInt()
                    ?: error("Organization membership is unavailable.")
                check(memberCount > 1) { "At least one organization member must remain." }
                transaction.update(organization, mapOf(
                    "memberCount" to memberCount - 1,
                    "memberCountAuditEventId" to auditId
                ))
            }
            null
        }.await()
    }

    suspend fun openDirectConversation(
        uid: String,
        organizationId: String,
        peerUid: String,
        peerName: String
    ): SecureOrgConversation {
        requireSignedIn(uid)
        require(peerUid != uid && peerUid.matches(UID_PATTERN)) { "This member is unavailable." }
        val organization = organizationRef(organizationId)
        val members = listOf(uid, peerUid).sorted()
        val id = secureDirectConversationId(uid, peerUid)
        val directChat = organization.collection(DIRECT_CHATS).document(id)
        database().runTransaction { transaction ->
            val ownMembership = transaction.get(organization.collection(MEMBERS).document(uid))
            val peerMembership = transaction.get(organization.collection(MEMBERS).document(peerUid))
            val conversation = transaction.get(directChat)
            check(ownMembership.getString("status") == ACTIVE && peerMembership.getString("status") == ACTIVE) {
                "Direct chats are only available between active organization members."
            }
            if (!conversation.exists()) {
                transaction.set(directChat, mapOf(
                    "members" to members,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            } else {
                check(conversation.get("members") == members) { "This conversation is unavailable." }
            }
            null
        }.await()
        return SecureOrgConversation(id, peerUid, peerName)
    }

    suspend fun sendMessage(
        uid: String,
        organizationId: String,
        conversation: SecureOrgConversation,
        text: String
    ) {
        requireSignedIn(uid)
        val trimmedText = text.trim()
        require(trimmedText.isNotEmpty() && trimmedText.length <= MAX_MESSAGE_LENGTH) {
            "Messages must be between 1 and $MAX_MESSAGE_LENGTH characters."
        }
        val organization = organizationRef(organizationId)
        val message = messagesCollection(organizationId, conversation).document()
        database().runTransaction { transaction ->
            val ownMembership = transaction.get(organization.collection(MEMBERS).document(uid))
            check(ownMembership.getString("status") == ACTIVE) { "Your membership is no longer active." }
            if (conversation.peerUid != null) {
                val peerMembership = transaction.get(organization.collection(MEMBERS).document(conversation.peerUid))
                val directChat = organization.collection(DIRECT_CHATS).document(conversation.id)
                val existingChat = transaction.get(directChat)
                check(peerMembership.getString("status") == ACTIVE) { "This member is no longer available." }
                if (!existingChat.exists()) {
                    transaction.set(directChat, mapOf(
                        "members" to listOf(uid, conversation.peerUid).sorted(),
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
            }
            transaction.set(message, mapOf(
                "senderUid" to uid,
                "text" to trimmedText,
                "createdAt" to FieldValue.serverTimestamp()
            ))
            null
        }.await()
    }

    private fun messagesCollection(
        organizationId: String,
        conversation: SecureOrgConversation
    ) = if (conversation.peerUid == null) {
        organizationRef(organizationId).collection(GROUP_CHATS)
            .document(MAIN_GROUP_CHAT).collection(MESSAGES)
    } else {
        organizationRef(organizationId).collection(DIRECT_CHATS)
            .document(conversation.id).collection(MESSAGES)
    }

    private fun organizationRef(organizationId: String) =
        database().collection(ORGS).document(organizationId)

    private fun memberFields(
        uid: String,
        displayName: String,
        role: SecureOrgRole,
        actorUid: String,
        auditEventId: String,
        organizationName: String
    ) = mapOf(
        "uid" to uid,
        "displayName" to displayName.trim().ifBlank { "Member" }.take(MAX_MEMBER_NAME_LENGTH),
        "role" to role.value,
        "status" to ACTIVE,
        "actorUid" to actorUid,
        "auditEventId" to auditEventId,
        "organizationName" to organizationName,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun auditFields(actorUid: String, targetUid: String, action: String) = mapOf(
        "actorUid" to actorUid,
        "targetUid" to targetUid,
        "action" to action,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun DocumentSnapshot.toSecureMember() = SecureOrgMember(
        uid = getString("uid") ?: id,
        displayName = getString("displayName") ?: "Member",
        role = SecureOrgRole.parse(getString("role"))
    )

    private fun DocumentSnapshot.toSecureMessage() = SecureOrgMessage(
        id = id,
        senderUid = getString("senderUid") ?: "",
        text = getString("text") ?: "",
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )

    private fun DocumentSnapshot.toSecureAuditEntry() = SecureOrgAuditEntry(
        id = id,
        actorUid = getString("actorUid") ?: "",
        targetUid = getString("targetUid") ?: "",
        action = getString("action") ?: "",
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )

    internal enum class MemberAction { PROMOTE, DEMOTE, REMOVE }

    private companion object {
        const val ORGS = "secureOrgs"
        const val MEMBERS = "members"
        const val GROUP_CHATS = "groupChats"
        const val DIRECT_CHATS = "directChats"
        const val MESSAGES = "messages"
        const val AUDIT_LOG = "auditLog"
        const val MAIN_GROUP_CHAT = "main"
        const val ACTIVE = "active"
        const val REMOVED = "removed"
        const val MAX_ORG_NAME_LENGTH = 80
        const val MAX_MEMBER_NAME_LENGTH = 60
        const val MAX_MESSAGE_LENGTH = 4_000
        const val MAX_VISIBLE_MESSAGES = 100
        const val MAX_AUDIT_ENTRIES = 100
        val ORG_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
        val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,128}")
    }
}

internal fun secureDirectConversationId(firstUid: String, secondUid: String): String {
    require(firstUid.isNotBlank() && secondUid.isNotBlank() && firstUid != secondUid)
    val canonicalUids = listOf(firstUid, secondUid).sorted().joinToString(":")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonicalUids.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
