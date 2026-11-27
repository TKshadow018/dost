package com.snigtus.dost

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

@Composable
internal fun SuperSecureScreen(
    context: Context,
    language: AppLanguage,
    uid: String,
    displayName: String,
    onBack: () -> Unit
) {
    val repository = remember(context) { SuperSecureRepository(context) }
    val scope = rememberCoroutineScope()
    var organization by remember { mutableStateOf<SecureOrgSummary?>(null) }
    var organizationName by remember { mutableStateOf("") }
    var organizationId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    BackHandler {
        if (organization != null) {
            organization = null
            message = ""
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(organization?.name ?: when (language) {
                            AppLanguage.BANGLA -> "সুপার সিকিউর"
                            AppLanguage.HINDI -> "सुपर सिक्योर"
                            AppLanguage.ENGLISH -> "Super Secure"
                        })
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (organization != null) {
                            organization = null
                            message = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val selectedOrganization = organization
        if (selectedOrganization != null) {
            SecureOrganizationWorkspace(
                repository = repository,
                organization = selectedOrganization,
                uid = uid,
                modifier = Modifier.padding(padding),
                onBack = { organization = null }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Private organization messaging",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Text only. There are no AI chats, public People discovery, calls, media, or analytics in this space. Group messages are visible to current members; direct messages are visible to their participants. Firebase project administrators can technically access stored data. Messages are not end-to-end encrypted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Enter an organization ID shared by its owner or manager, or create an organization. Members are admitted by Firebase UID; share the organization ID with them separately.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = organizationId,
                    onValueChange = { organizationId = it.take(128) },
                    label = { Text("Organization ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
                Button(
                    enabled = uid.isNotBlank() && organizationId.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                organization = repository.openOrganization(uid, organizationId.trim())
                                organizationId = ""
                            } catch (_: Exception) {
                                message = "Could not open this organization. Check your connection, ID, and membership."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(if (busy) "Checking membership…" else "Open organization")
                }
                OutlinedTextField(
                    value = organizationName,
                    onValueChange = { organizationName = it.take(80) },
                    label = { Text("New organization name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedButton(
                    enabled = uid.isNotBlank() && organizationName.trim().length >= 2 && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                organization = repository.createOrganization(uid, organizationName, displayName)
                                organizationName = ""
                            } catch (_: Exception) {
                                message = "Could not create the organization. Check your connection and try again."
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Create organization")
                }
                if (uid.isBlank()) {
                    Text(
                        "A signed-in Firebase account is required.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Owners can request organization deletion; members can ask Dost support about their own messages. Automated export and customer-managed hosting are not available in this MVP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SecureOrganizationWorkspace(
    repository: SuperSecureRepository,
    organization: SecureOrgSummary,
    uid: String,
    modifier: Modifier,
    onBack: () -> Unit
) {
    var membershipState by remember(organization.id, uid) {
        mutableStateOf<SecureMembershipState>(SecureMembershipState.Checking)
    }
    var members by remember(organization.id, uid) { mutableStateOf<List<SecureOrgMember>?>(null) }
    var selectedConversation by remember(organization.id) {
        mutableStateOf(SecureOrgConversation("main"))
    }
    var notice by remember { mutableStateOf("") }
    var showMembers by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    var auditEntries by remember(organization.id, uid) {
        mutableStateOf<List<SecureOrgAuditEntry>?>(null)
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(repository, organization.id, uid) {
        val registrations = mutableListOf<ListenerRegistration>()
        try {
            registrations += repository.listenMembership(uid, organization.id) {
                membershipState = it
            }
            registrations += repository.listenMembers(uid, organization.id) {
                members = it
            }
            if (secureCanManageMembers(organization.role)) {
                registrations += repository.listenAuditLog(uid, organization.id) {
                    auditEntries = it
                }
            }
        } catch (_: Exception) {
            membershipState = SecureMembershipState.Unavailable
            members = null
            auditEntries = null
        }
        onDispose { registrations.forEach(ListenerRegistration::remove) }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Organization ID: ${organization.id}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Members: ${members?.size ?: organization.memberCount}/$MAX_SECURE_ORG_MEMBERS",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Current members only. Text messages are encrypted in transit and at rest by Firebase, but are not end-to-end encrypted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (val state = membershipState) {
            SecureMembershipState.Checking -> Text("Verifying membership…")
            SecureMembershipState.Unavailable -> Text("Connect to verify membership and view organization data.")
            SecureMembershipState.Removed -> {
                Text("Your organization access has ended.")
                Button(onClick = onBack) { Text("Close") }
            }
            is SecureMembershipState.Active -> {
                Text(
                    "Role: ${state.member.role.value}",
                    style = MaterialTheme.typography.bodySmall
                )
                TabRow(
                    selectedTabIndex = when {
                        showAudit -> 2
                        showMembers -> 1
                        else -> 0
                    }
                ) {
                    Tab(selected = !showMembers && !showAudit, onClick = {
                        showMembers = false
                        showAudit = false
                    }, text = { Text("Group chat") })
                    Tab(selected = showMembers, onClick = {
                        showMembers = true
                        showAudit = false
                    }, text = { Text("Members") })
                    if (secureCanManageMembers(state.member.role)) {
                        Tab(selected = showAudit, onClick = {
                            showMembers = false
                            showAudit = true
                        }, text = { Text("Audit") })
                    }
                }
                when {
                    showAudit -> SecureAuditList(auditEntries)
                    showMembers -> SecureMemberList(
                        repository = repository,
                        organization = organization,
                        uid = uid,
                        currentMember = state.member,
                        members = members,
                        onOpenDirect = { member ->
                            notice = ""
                            scope.launch {
                                try {
                                    selectedConversation = repository.openDirectConversation(
                                        uid, organization.id, member.uid, member.displayName
                                    )
                                    showMembers = false
                                    showAudit = false
                                } catch (_: Exception) {
                                    notice = "Could not open this direct chat. Check membership and connection."
                                }
                            }
                        },
                        onNotice = { notice = it }
                    )
                    else -> SecureChatPanel(
                        repository = repository,
                        organization = organization,
                        conversation = selectedConversation,
                        uid = uid,
                        members = members.orEmpty(),
                        onBackToGroup = {
                            selectedConversation = SecureOrgConversation("main")
                            showMembers = false
                            showAudit = false
                        },
                        onNotice = { notice = it }
                    )
                }
                if (notice.isNotBlank()) {
                    Text(notice, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SecureMemberList(
    repository: SuperSecureRepository,
    organization: SecureOrgSummary,
    uid: String,
    currentMember: SecureOrgMember,
    members: List<SecureOrgMember>?,
    onOpenDirect: (SecureOrgMember) -> Unit,
    onNotice: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<SecureOrgMember, SuperSecureRepository.MemberAction>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showAddDialog) {
        AddSecureMemberDialog(
            canPromote = secureCanManageRoles(currentMember.role),
            onDismiss = { showAddDialog = false },
            onAdd = { memberUid, name, role ->
                busy = true
                scope.launch {
                    try {
                        repository.addMember(uid, organization.id, memberUid, name, role)
                        onNotice("Member added.")
                        showAddDialog = false
                    } catch (_: Exception) {
                        onNotice("Could not add that member. Check the Firebase UID and your manager permissions.")
                    } finally {
                        busy = false
                    }
                }
            }
        )
    }
    pendingAction?.let { (member, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == SuperSecureRepository.MemberAction.REMOVE) "Remove member?" else "Change manager role?") },
            text = { Text("This change takes effect immediately for organization access.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repository.updateMember(uid, organization.id, member.uid, action)
                            onNotice(if (action == SuperSecureRepository.MemberAction.REMOVE) "Member removed." else "Manager role updated.")
                        } catch (_: Exception) {
                            onNotice("Could not update membership. Check your manager permissions.")
                        } finally {
                            busy = false
                            pendingAction = null
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (secureCanManageMembers(currentMember.role)) {
            OutlinedButton(
                onClick = { showAddDialog = true },
                enabled = !busy && members != null && members.size < MAX_SECURE_ORG_MEMBERS,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add member by Firebase UID")
            }
        }
        if (members == null) {
            Text("Connect to view the current member list.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(members, key = { it.uid }) { member ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(member.role.value, style = MaterialTheme.typography.bodySmall)
                                }
                                if (member.uid != uid) {
                                    TextButton(onClick = { onOpenDirect(member) }) { Text("Message") }
                                }
                            }
                            if (secureCanRemoveMember(
                                    currentMember.role,
                                    member.role,
                                    member.uid == uid
                                )
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (secureCanManageRoles(currentMember.role)) {
                                        val action = if (member.role == SecureOrgRole.MANAGER) {
                                            SuperSecureRepository.MemberAction.DEMOTE
                                        } else {
                                            SuperSecureRepository.MemberAction.PROMOTE
                                        }
                                        TextButton(onClick = { pendingAction = member to action }) {
                                            Text(if (action == SuperSecureRepository.MemberAction.PROMOTE) "Make manager" else "Remove manager")
                                        }
                                    }
                                    if (member.role == SecureOrgRole.MEMBER ||
                                        secureCanManageRoles(currentMember.role)
                                    ) {
                                        TextButton(onClick = {
                                            pendingAction = member to SuperSecureRepository.MemberAction.REMOVE
                                        }) { Text("Remove") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSecureMemberDialog(
    canPromote: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, SecureOrgRole) -> Unit
) {
    var uid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add organization member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uid,
                    onValueChange = { uid = it.take(128) },
                    label = { Text("Firebase UID") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Display name (optional)") },
                    singleLine = true
                )
                if (canPromote) {
                    TextButton(onClick = { manager = !manager }) {
                        Text(if (manager) "Role: manager (tap for member)" else "Role: member (tap for manager)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = uid.isNotBlank(),
                onClick = {
                    onAdd(uid.trim(), name.trim(), if (manager) SecureOrgRole.MANAGER else SecureOrgRole.MEMBER)
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SecureAuditList(entries: List<SecureOrgAuditEntry>?) {
    if (entries == null) {
        Text("Connect to view the organization audit log.")
    } else if (entries.isEmpty()) {
        Text("No membership changes yet.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(entry.action.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                        Text("Actor: ${entry.actorUid}")
                        Text("Target: ${entry.targetUid}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SecureChatPanel(
    repository: SuperSecureRepository,
    organization: SecureOrgSummary,
    conversation: SecureOrgConversation,
    uid: String,
    members: List<SecureOrgMember>,
    onBackToGroup: () -> Unit,
    onNotice: (String) -> Unit
) {
    var messages by remember(organization.id, conversation.id) { mutableStateOf<List<SecureOrgMessage>?>(null) }
    var draft by remember(organization.id, conversation.id) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    DisposableEffect(repository, organization.id, conversation.id, uid) {
        val registration = try {
            repository.listenMessages(uid, organization.id, conversation) {
                messages = it
            }
        } catch (_: Exception) {
            messages = null
            null
        }
        onDispose { registration?.remove() }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (conversation.peerUid != null) {
            TextButton(onClick = onBackToGroup) { Text("Back to group chat") }
            Text(conversation.title, style = MaterialTheme.typography.titleMedium)
        }
        if (messages == null) {
            Text("Connect to view and send organization messages.")
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages.orEmpty(), key = { it.id }) { message ->
                    val senderName = if (message.senderUid == uid) "You" else {
                        members.firstOrNull { it.uid == message.senderUid }?.displayName ?: "Member"
                    }
                    Text("$senderName: ${message.text}")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4_000) },
                enabled = messages != null && !sending,
                label = { Text("Text message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            IconButton(
                enabled = messages != null && draft.isNotBlank() && !sending,
                onClick = {
                    val text = draft
                    sending = true
                    scope.launch {
                        try {
                            repository.sendMessage(uid, organization.id, conversation, text)
                            draft = ""
                            onNotice("")
                        } catch (_: Exception) {
                            onNotice("Message not sent. Verify membership and connection, then try again.")
                        } finally {
                            sending = false
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send message", modifier = Modifier.size(24.dp))
            }
        }
    }
}
