@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.snigtus.dost

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.*
import android.os.*
import android.provider.*
import java.io.*
import java.time.*
import java.time.format.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.*

@Composable
internal fun ChatScreen(
    friend: Friend, store: DostStore, onBack: () -> Unit, language: AppLanguage,
    initialDraft: String, initialImagePath: String,
    onDraftChange: (String) -> Unit, onImageDraftChange: (String) -> Unit,
    onFriendProfile: () -> Unit, onFriendTimeZoneChange: (String) -> Unit, onFloatingChat: () -> Unit,
    profileGenerating: Boolean, profileError: ProfileFailure?, onRetryProfile: () -> Unit, onUseBasicProfile: () -> Unit
) {
    val context = LocalContext.current
    val messageMap = ConversationManager.messages.collectAsState().value
    val messages = messageMap[friend.id] ?: store.loadMessages(friend.id)
    var draft by remember(friend.id) { mutableStateOf(initialDraft) }
    var selectedImagePath by remember(friend.id) { mutableStateOf(initialImagePath) }
    var error by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { storeChatImage(context, uri) }
                .onSuccess { selectedImagePath = it; onImageDraftChange(it); error = null }
                .onFailure { error = when (it.message) {
                    "Invalid image" -> chatLabel(language, "That file is not a valid image.", "এই ফাইলটি বৈধ ছবি নয়।", "यह फ़ाइल मान्य छवि नहीं है।")
                    "Image is too large" -> chatLabel(language, "That photo is too large. Try a smaller one.", "ছবিটি খুব বড়। ছোট ছবি বেছে নিন।", "फ़ोटो बहुत बड़ी है। छोटी फ़ोटो चुनें।")
                    else -> chatLabel(language, "Could not load that image. Try a smaller photo.", "ছবিটি লোড করা যায়নি। ছোট ছবি বেছে নিন।", "फ़ोटो लोड नहीं हुई। छोटी फ़ोटो आज़माएँ।")
                } }
        }
    }
    val sendingIds = ConversationManager.sending.collectAsState().value
    val sending = friend.id in sendingIds
    val scheduledWaitingIds = ConversationManager.waitingForSchedule.collectAsState().value
    val waitingForSchedule = friend.id in scheduledWaitingIds
    val listState = rememberLazyListState()
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    var currentTime by remember { mutableStateOf(ZonedDateTime.now(validZoneId(store.userTimeZoneId()))) }
    var showUserInfo by remember { mutableStateOf(false) }
    var showSessionSummaries by remember { mutableStateOf(false) }
    var showReminderEditor by remember { mutableStateOf(false) }
    var reminderDraft by remember(friend.id) { mutableStateOf<PlannedEvent?>(null) }
    var showSharedTopicEditor by remember { mutableStateOf(false) }
    var showSharedRitualEditor by remember { mutableStateOf(false) }
    var showQuickGame by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showConversationStarters by remember(friend.id) { mutableStateOf(true) }
    var dismissedRecapSessionId by remember(friend.id) { mutableStateOf("") }
    var sharedTopicRevision by remember(friend.id) { mutableIntStateOf(0) }
    var pendingMemory by remember { mutableStateOf<ChatMessage?>(null) }
    var memoryRevision by remember(friend.id) { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sessionSummaries by remember(friend.id) { mutableStateOf(store.loadSessionSummaries(friend.id).takeLast(10).reversed()) }
    val latestRecap = sessionSummaries.firstOrNull { it.summary.isNotBlank() }
    val sharedTopics = remember(friend.id, sharedTopicRevision) {
        store.loadSharedMoments(friend.id).filter { it.kind == "shared_topic" }
    }
    val sharedRituals = remember(friend.id, sharedTopicRevision) {
        store.loadSharedMoments(friend.id).filter { it.kind == "shared_ritual" }
    }
    val userMemories = remember(friend.id, memoryRevision) { store.loadUserMemories(friend.id) }
    var showFriendInfo by remember { mutableStateOf(false) }
    val displayedMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }
    val profileBlocked = friend.profilePending
    val busyUntil = friend.replyAvailableAt(currentTime)
    LaunchedEffect(friend.id) {
        while (true) {
            delay(60_000)
            currentTime = ZonedDateTime.now(validZoneId(store.userTimeZoneId()))
        }
    }
    DisposableEffect(friend.id) {
        ConversationManager.openChat(friend.id)
        ConversationManager.seedInitialGreeting(friend)
        onDispose { ConversationManager.closeChat(friend.id) }
    }
    LaunchedEffect(messages.size, sending, error) {
        val itemCount = messages.size + (if (sending) 1 else 0) + (if (error != null) 1 else 0)
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onFriendProfile),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FriendPhoto(friend.photoUri, friend.name, Modifier.size(44.dp).clip(CircleShape))
                        Column {
                            Text(
                                friend.name + if (friend.mood.isNotBlank()) " ${friend.mood}" else "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                busyUntil?.let {
                                    chatLabel(
                                        language,
                                        "Replies at ${it.format(DateTimeFormatter.ofPattern("h:mm a", chatLocale(language)))}",
                                        "${it.format(DateTimeFormatter.ofPattern("h:mm a", chatLocale(language)))}-এ উত্তর দেবে",
                                        "${it.format(DateTimeFormatter.ofPattern("h:mm a", chatLocale(language)))} बजे जवाब देगा"
                                    )
                                }
                                    ?: "${friendshipLevel(friend.friendshipScore, language)} · ${friend.friendshipScore}/100",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showChatMenu = true },
                            modifier = Modifier.semantics {
                                contentDescription = when { bangla -> "বিকল্প"; hindi -> "विकल्प"; else -> "Chat options" }
                            }
                        ) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                        }
                        DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                            DropdownMenuItem(text = { Text(LocalizedText.SEARCH.resolve(language)) }, onClick = { showChatMenu = false; showSearch = !showSearch; if (!showSearch) searchQuery = "" })
                            DropdownMenuItem(text = { Text(LocalizedText.EXPORT_CHAT.resolve(language)) }, onClick = { showChatMenu = false; exportChat(context, friend, messages) })
                            DropdownMenuItem(
                                text = { Text(chatLabel(language, "Friend details", "বন্ধুর তথ্য", "दोस्त की जानकारी")) },
                                onClick = { showChatMenu = false; showFriendInfo = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(when (language) {
                                        AppLanguage.BANGLA -> "বন্ধুর প্রোফাইল"
                                        AppLanguage.HINDI -> "दोस्त की प्रोफ़ाइल"
                                        AppLanguage.ENGLISH -> "Friend profile"
                                    })
                                },
                                onClick = { showChatMenu = false; onFriendProfile() }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(when (language) {
                                        AppLanguage.BANGLA -> "অনুস্মারক যোগ করুন"
                                        AppLanguage.HINDI -> "रिमाइंडर जोड़ें"
                                        AppLanguage.ENGLISH -> "Add a reminder"
                                    })
                                },
                                onClick = {
                                    showChatMenu = false
                                    reminderDraft = null
                                    showReminderEditor = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(chatLabel(language, "Shared topic", "একসঙ্গে আলোচনার বিষয়", "साझा विषय")) },
                                onClick = { showChatMenu = false; showSharedTopicEditor = true }
                            )
                            DropdownMenuItem(
                                text = { Text(chatLabel(language, "Shared ritual", "একসঙ্গে করার অভ্যাস", "साझा गतिविधि")) },
                                onClick = { showChatMenu = false; showSharedRitualEditor = true }
                            )
                            DropdownMenuItem(
                                text = { Text(chatLabel(language, "Play a quick game", "ছোট খেলা খেলুন", "छोटा खेल खेलें")) },
                                onClick = { showChatMenu = false; showQuickGame = true }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(when (language) {
                                        AppLanguage.BANGLA -> "এই বন্ধু কী মনে রেখেছে"
                                        AppLanguage.HINDI -> "यह दोस्त क्या याद रखता है"
                                        AppLanguage.ENGLISH -> "What this friend remembers"
                                    })
                                },
                                onClick = { showChatMenu = false; showUserInfo = true }
                            )
                            DropdownMenuItem(text = { Text(LocalizedText.SESSION_SUMMARIES.resolve(language)) }, onClick = { showChatMenu = false; sessionSummaries = store.loadSessionSummaries(friend.id).takeLast(10).reversed(); showSessionSummaries = true })
                            DropdownMenuItem(text = { Text(LocalizedText.MINIMIZE_CHAT.resolve(language)) }, onClick = { showChatMenu = false; onFloatingChat() })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RelationshipMeters(friend = friend, language = language, userAge = store.userAge())
            if (latestRecap != null && latestRecap.sessionId != dismissedRecapSessionId) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(chatLabel(language, "Since you last talked", "শেষবার কথা বলার পর", "पिछली बातचीत के बाद"), style = MaterialTheme.typography.titleSmall)
                        Text(latestRecap.summary, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSessionSummaries = true }) {
                                Text(chatLabel(language, "View recap", "সারাংশ দেখুন", "सारांश देखें"))
                            }
                            TextButton(onClick = { dismissedRecapSessionId = latestRecap.sessionId }) {
                                Text(chatLabel(language, "Dismiss", "বন্ধ করুন", "हटाएँ"))
                            }
                        }
                    }
                }
            }
            if (sharedTopics.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sharedTopics.forEach { topic ->
                        AssistChip(
                            onClick = {
                                draft = when (language) {
                                    AppLanguage.BANGLA -> "চলো ${topic.title} নিয়ে আরও কথা বলি।"
                                    AppLanguage.HINDI -> "चलो ${topic.title} के बारे में और बात करें।"
                                    AppLanguage.ENGLISH -> "Let's talk more about ${topic.title}."
                                }
                                onDraftChange(draft)
                            },
                            label = { Text(topic.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
            if (sharedRituals.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sharedRituals.forEach { ritual ->
                        AssistChip(
                            onClick = {
                                draft = when (language) {
                                    AppLanguage.BANGLA -> "চলো আমাদের ${ritual.title} আবার করি। ${ritual.detail}".trim()
                                    AppLanguage.HINDI -> "चलो अपना ${ritual.title} फिर से करें। ${ritual.detail}".trim()
                                    AppLanguage.ENGLISH -> "Let's continue our ${ritual.title}. ${ritual.detail}".trim()
                                }
                                onDraftChange(draft)
                            },
                            label = {
                                Text(
                                    chatLabel(language, "Ritual: ${ritual.title}", "অভ্যাস: ${ritual.title}", "गतिविधि: ${ritual.title}"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(when { bangla -> "মেসেজ খুঁজুন..."; hindi -> "संदेश खोजें..."; else -> "Search messages..." }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                if (showConversationStarters && messages.none { it.role == "user" } && !profileBlocked) item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    chatLabel(language, "Not sure what to say?", "কী বলবেন বুঝতে পারছেন না?", "समझ नहीं आ रहा क्या कहें?"),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TextButton(onClick = { showConversationStarters = false }) {
                                    Text(chatLabel(language, "Hide", "লুকান", "छिपाएँ"))
                                }
                            }
                            conversationStarters(friend, language).forEach { starter ->
                                SuggestionChip(
                                    onClick = { draft = starter; onDraftChange(starter) },
                                    label = { Text(starter, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }
                if (displayedMessages.isEmpty() && !profileBlocked) item {
                    Text(
                        when { bangla -> "${friend.name}-এর সাথে কথা বলা শুরু করুন।"; hindi -> "${friend.name} के साथ बातचीत शुरू करें।"; else -> "Start a conversation with ${friend.name}." },
                        modifier = Modifier.padding(12.dp)
                    )
                }
                items(displayedMessages, key = { it.messageId }) { message ->
                    MessageBubble(
                        message = message,
                        language = language,
                        friendName = friend.name,
                        onRemember = if (message.role == "user" && message.content.isNotBlank()) {
                            { pendingMemory = message }
                        } else null,
                        onSaveMoment = if (message.content.isNotBlank() && message.category != ERROR_MESSAGE_CATEGORY) {
                            {
                                store.saveSharedMoment(
                                    friend.id,
                                    SharedMoment(
                                        id = stableIdempotencyKey("saved-message:${friend.id}:${message.messageId}"),
                                        title = chatLabel(language, "Saved conversation moment", "সংরক্ষিত কথোপকথনের মুহূর্ত", "सहेजी गई बातचीत की याद"),
                                        detail = message.content.take(2_000),
                                        createdAtMillis = message.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                        kind = "user_saved",
                                        pinned = true
                                    )
                                )
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(chatLabel(language, "Saved to shared moments", "একসঙ্গে কাটানো মুহূর্তে সংরক্ষিত", "साझा यादों में सेव"))
                                }
                            }
                        } else null,
                        onRegenerate = if (message.role == "assistant" && message.messageId == displayedMessages.lastOrNull { it.role == "assistant" }?.messageId) {
                            {
                                if (message.category == ERROR_MESSAGE_CATEGORY) {
                                    coroutineScope.launch {
                                        AnonymousTelemetry.record(store.anonymousTelemetryEnabled(), AnonymousTelemetryEvent.FAILED_SEND_RETRIED)
                                    }
                                }
                                ConversationManager.regenerateReply(friend, language)
                            }
                        } else null,
                        onSnooze = if (message.category in setOf("morning_greeting", "follow_up")) {
                            {
                                ConversationManager.pauseFriendProactive(friend.id, System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(chatLabel(language, "Check-ins snoozed for 24 hours", "খোঁজ নেওয়া ২৪ ঘণ্টার জন্য বিরতি", "हालचाल 24 घंटे के लिए स्थगित"))
                                }
                            }
                        } else null,
                        onPause = if (message.category in setOf("morning_greeting", "follow_up")) {
                            {
                                ConversationManager.pauseFriendProactive(friend.id, Long.MAX_VALUE)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(chatLabel(language, "Check-ins paused until you resume them", "আবার চালু না করা পর্যন্ত খোঁজ নেওয়া বন্ধ", "आपके फिर शुरू करने तक हालचाल रुका"))
                                }
                            }
                        } else null
                    )
                }
                if (sending && waitingForSchedule) item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when { bangla -> "${friend.name} ব্যস্ততার সময়সূচির পরে উত্তর দেবে।"; hindi -> "${friend.name} व्यस्त समय के बाद जवाब देगा।"; else -> "${friend.name} will reply after the busy schedule." },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { ConversationManager.replyScheduledMessageNow(friend.id) }) {
                            Text(when {
                                bangla -> "এখনই উত্তর"
                                hindi -> "अभी जवाब"
                                else -> "Reply now"
                            })
                        }
                    }
                } else if (sending) item { ThinkingBubble(friendName = friend.name, language = language) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) } }
            }
            if (profileBlocked) {
                ProfileCreationNotice(language, profileGenerating, profileError, onRetryProfile, onUseBasicProfile)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.imePadding()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedImagePath.isNotBlank()) {
                        val preview = remember(selectedImagePath) { BitmapFactory.decodeFile(selectedImagePath)?.asImageBitmap() }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (preview != null) Image(
                                preview,
                                contentDescription = chatLabel(language, "Selected image", "নির্বাচিত ছবি", "चुनी गई फ़ोटो"),
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                            )
                            TextButton(onClick = {
                                File(selectedImagePath).takeIf { it.isFile }?.delete()
                                selectedImagePath = ""
                                onImageDraftChange("")
                            }) { Text(chatLabel(language, "Remove image", "ছবি সরান", "फ़ोटो हटाएँ")) }
                        }
                    }
                    val canSend = !profileBlocked && (draft.isNotBlank() || selectedImagePath.isNotBlank())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !profileBlocked,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo),
                                contentDescription = when {
                                    bangla -> "ছবি সংযুক্ত করুন"
                                    hindi -> "फ़ोटो जोड़ें"
                                    else -> "Attach photo"
                                },
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it; onDraftChange(it) },
                            enabled = !profileBlocked,
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .semantics {
                                    contentDescription = chatLabel(language, "Message", "বার্তা", "संदेश")
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            maxLines = 4,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (draft.isBlank()) {
                                        Text(
                                            when { bangla -> "একটি মেসেজ লিখুন..."; hindi -> "संदेश लिखें..."; else -> "Write a message..." },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            enabled = canSend,
                            onClick = {
                            if (friend.profilePending) return@IconButton
                            val text = draft.trim()
                            val imagePath = selectedImagePath
                            val parsedReminder = parseExplicitReminderRequest(
                                text,
                                System.currentTimeMillis(),
                                store.userTimeZoneId()
                            )
                            if (messages.none { it.role == "user" }) {
                                coroutineScope.launch {
                                    AnonymousTelemetry.record(
                                        store.anonymousTelemetryEnabled(),
                                        AnonymousTelemetryEvent.FIRST_MESSAGE_TO_FRIEND
                                    )
                                }
                            }
                            draft = ""
                            selectedImagePath = ""
                            onDraftChange("")
                            onImageDraftChange("")
                            error = null
                            ConversationManager.send(friend, text, language, imagePath)
                            if (parsedReminder != null) {
                                reminderDraft = parsedReminder
                                showReminderEditor = true
                            }
                        },
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_send),
                                contentDescription = when { bangla -> "পাঠান"; hindi -> "भेजें"; else -> "Send" },
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFriendInfo) FriendInfoDialog(
        friend = friend,
        userAge = store.userAge(),
        language = language,
        onTimeZoneChange = onFriendTimeZoneChange,
        onDismiss = { showFriendInfo = false }
    )
    if (showUserInfo) ConversationProfileDialog(
        friend = friend,
        memories = userMemories,
        language = language,
        onForget = { memory ->
            store.deleteUserMemory(friend.id, memory)
            memoryRevision += 1
            coroutineScope.launch {
                AnonymousTelemetry.record(store.anonymousTelemetryEnabled(), AnonymousTelemetryEvent.MEMORY_FORGOTTEN)
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar(chatLabel(language, "Memory forgotten", "স্মৃতি মুছে ফেলা হয়েছে", "याद हटा दी गई"))
            }
        },
        onDismiss = { showUserInfo = false }
    )
    if (showSessionSummaries) SessionSummariesDialog(sessionSummaries, language, onDismiss = { showSessionSummaries = false })
    if (showSharedTopicEditor) {
        SharedTopicDialog(
            language = language,
            onDismiss = { showSharedTopicEditor = false },
            onSave = { title, detail ->
                store.saveSharedMoment(
                    friend.id,
                    SharedMoment(
                        id = java.util.UUID.randomUUID().toString(),
                        title = title,
                        detail = detail,
                        createdAtMillis = System.currentTimeMillis(),
                        kind = "shared_topic",
                        pinned = true
                    )
                )
                sharedTopicRevision += 1
                showSharedTopicEditor = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(chatLabel(language, "Shared topic saved", "একসঙ্গে আলোচনার বিষয় সংরক্ষিত", "साझा विषय सेव हुआ"))
                }
            }
        )
    }
    if (showSharedRitualEditor) {
        SharedRitualDialog(
            language = language,
            onDismiss = { showSharedRitualEditor = false },
            onSave = { title, detail ->
                store.saveSharedMoment(
                    friend.id,
                    SharedMoment(
                        id = java.util.UUID.randomUUID().toString(),
                        title = title,
                        detail = detail,
                        createdAtMillis = System.currentTimeMillis(),
                        kind = "shared_ritual",
                        pinned = true
                    )
                )
                sharedTopicRevision += 1
                showSharedRitualEditor = false
                coroutineScope.launch {
                    AnonymousTelemetry.record(store.anonymousTelemetryEnabled(), AnonymousTelemetryEvent.SHARED_RITUAL_SAVED)
                    snackbarHostState.showSnackbar(chatLabel(language, "Shared ritual saved. Tap it to prepare it again.", "একসঙ্গে করার অভ্যাস সংরক্ষিত। আবার শুরু করতে ট্যাপ করুন।", "साझा गतिविधि सेव हुई। फिर शुरू करने के लिए टैप करें।"))
                }
            }
        )
    }
    if (showQuickGame) QuickNumberGameDialog(friend.name, language, onDismiss = { showQuickGame = false })
    if (showReminderEditor) {
        ReminderEditorDialog(
            language = language,
            userTimeZoneId = store.userTimeZoneId(),
            existing = reminderDraft,
            confirmSuggestion = reminderDraft != null,
            onDismiss = {
                showReminderEditor = false
                reminderDraft = null
            },
            onSave = { event ->
                try {
                    store.savePlannedEvent(friend.id, event)
                    if (event.state == PlannedEventState.UPCOMING && event.followUpRequested) {
                        ConversationWorkScheduler.scheduleUserReminder(context, friend, event, language)
                    } else {
                        ConversationWorkScheduler.cancelUserReminder(context, event.id)
                    }
                    showReminderEditor = false
                    reminderDraft = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(chatLabel(language, "Reminder saved", "অনুস্মারক সংরক্ষিত", "रिमाइंडर सेव"))
                    }
                } catch (failure: IllegalArgumentException) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(failure.message ?: chatLabel(language, "Could not save the reminder.", "অনুস্মারক সংরক্ষণ করা যায়নি।", "रिमाइंडर सेव नहीं हुआ।"))
                    }
                }
            }
        )
    }
    pendingMemory?.let { message ->
        var fact by remember(message.messageId) { mutableStateOf(message.content.take(500)) }
        AlertDialog(
            onDismissRequest = { pendingMemory = null },
            title = {
                Text(when (language) {
                    AppLanguage.BANGLA -> "এই বন্ধুর জন্য স্মৃতি সংরক্ষণ করবেন?"
                    AppLanguage.HINDI -> "इस दोस्त के लिए याद सेव करें?"
                    AppLanguage.ENGLISH -> "Remember this for ${friend.name}?"
                })
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "এটি শুধু এই বন্ধুর স্মৃতিতে সংরক্ষিত হবে। চাইলে লেখাটি ছোট করে নিন।"
                        AppLanguage.HINDI -> "यह केवल इसी दोस्त की यादों में सेव होगा। चाहें तो इसे छोटा करें।"
                        AppLanguage.ENGLISH -> "This is saved only to this friend's memories. You can shorten it first."
                    }, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = fact,
                        onValueChange = { if (it.length <= 500) fact = it },
                        label = { Text(when (language) {
                            AppLanguage.BANGLA -> "স্মৃতি"
                            AppLanguage.HINDI -> "याद"
                            AppLanguage.ENGLISH -> "Memory"
                        }) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        supportingText = { Text("${fact.length}/500") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = fact.isNotBlank(),
                    onClick = {
                        store.addUserMemories(friend.id, listOf(UserMemory("other", fact.trim())))
                        memoryRevision += 1
                        pendingMemory = null
                        coroutineScope.launch {
                            AnonymousTelemetry.record(store.anonymousTelemetryEnabled(), AnonymousTelemetryEvent.MEMORY_SAVED)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(chatLabel(language, "Saved to ${friend.name}'s memories", "${friend.name}-এর স্মৃতিতে সংরক্ষিত", "${friend.name} की यादों में सेव"))
                        }
                    }
                ) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "সংরক্ষণ"
                        AppLanguage.HINDI -> "सेव करें"
                        AppLanguage.ENGLISH -> "Save"
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMemory = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }
}

@Composable
private fun FriendInfoDialog(
    friend: Friend,
    userAge: String,
    language: AppLanguage,
    onTimeZoneChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val routines = friend.effectiveRoutineBlocks()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chatLabel(language, "${friend.name}'s profile", "${friend.name}-এর প্রোফাইল", "${friend.name} की प्रोफ़ाइल")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeZonePicker(
                    chatLabel(language, "Friend timezone", "বন্ধুর সময় অঞ্চল", "दोस्त का समय क्षेत्र"),
                    friend.timeZoneId,
                    onTimeZoneChange
                )
                listOf(
                    chatLabel(language, "Age", "বয়স", "उम्र") to friend.age,
                    chatLabel(language, "Gender", "লিঙ্গ", "लिंग") to friend.gender,
                    chatLabel(language, "Personality", "ব্যক্তিত্ব", "व्यक्तित्व") to friend.personality,
                    chatLabel(language, "How you met", "কীভাবে পরিচয়", "कैसे मिले") to friend.relationship,
                    chatLabel(language, "First message", "প্রথম বার্তা", "पहला संदेश") to friend.greeting,
                    chatLabel(language, "Interests", "আগ্রহ", "रुचियाँ") to friend.interests,
                    chatLabel(language, "Memories", "স্মৃতি", "यादें") to friend.memories,
                    chatLabel(language, "Conversation style", "কথোপকথনের ধরন", "बातचीत की शैली") to friend.conversationStyle,
                    chatLabel(language, "Family", "পরিবার", "परिवार") to friend.family,
                    chatLabel(language, "Family members", "পরিবারের সদস্য", "परिवार के सदस्य") to friend.familyMembers,
                    chatLabel(language, "Family activities", "পারিবারিক কার্যক্রম", "पारिवारिक गतिविधियाँ") to friend.familyActivities,
                    chatLabel(language, "Financial condition", "আর্থিক অবস্থা", "आर्थिक स्थिति") to friend.financialCondition,
                    chatLabel(language, "Address", "ঠিকানা", "पता") to friend.address,
                    chatLabel(language, "Height", "উচ্চতা", "कद") to friend.height,
                    chatLabel(language, "Weight", "ওজন", "वज़न") to friend.weight,
                    chatLabel(language, "Facial features", "মুখের বৈশিষ্ট্য", "चेहरे की विशेषताएँ") to friend.facialFeatures,
                    chatLabel(language, "Body features", "শারীরিক বৈশিষ্ট্য", "शारीरिक विशेषताएँ") to friend.bodyFeatures,
                    chatLabel(language, "Friendship", "বন্ধুত্ব", "दोस्ती") to
                        "${friendshipLevel(friend.friendshipScore, language)} (${friend.friendshipScore}/100)",
                    chatLabel(language, "Love", "ভালোবাসা", "प्यार") to when {
                        !romanceAllowed(userAge, friend.age) -> chatLabel(language, "Off", "বন্ধ", "बंद")
                        friend.friendshipScore <= 70 -> chatLabel(language, "Locked", "লক করা", "लॉक")
                        else -> "${loveLevel(friend.loveScore)} (${friend.loveScore}/100)"
                    }
                ).forEach { (label, value) ->
                    if (value.isNotBlank()) Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
                }
                Text(chatLabel(language, "Busy routine", "ব্যস্ততার রুটিন", "व्यस्त दिनचर्या"), style = MaterialTheme.typography.titleSmall)
                routines.forEach { routine ->
                    val days = routine.daysOfWeek.sorted().joinToString(", ") {
                        java.time.DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, chatLocale(language))
                    }
                    val start = LocalTime.of(routine.startMinute / 60, routine.startMinute % 60)
                        .format(DateTimeFormatter.ofPattern("h:mm a", chatLocale(language)))
                    Text(
                        "${routine.reason.ifBlank { chatLabel(language, "Routine", "রুটিন", "दिनचर्या") }}: $days, $start (${routine.durationMinutes} min)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalizedText.CLOSE.resolve(language)) }
        }
    )
}

@Composable
private fun ConversationProfileDialog(friend: Friend, memories: List<UserMemory>, language: AppLanguage, onForget: (UserMemory) -> Unit, onDismiss: () -> Unit) {
    val profileFacts = memories.filter { it.fact.isNotBlank() }.asReversed()
    var forgetting by remember { mutableStateOf<UserMemory?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (language) {
                AppLanguage.BANGLA -> "${friend.name}-এর সঙ্গে আপনার পরিচিতি"
                AppLanguage.HINDI -> "${friend.name} के साथ आपकी प्रोफ़ाइल"
                AppLanguage.ENGLISH -> "Your profile with ${friend.name}"
            })
        },
        text = {
            if (profileFacts.isEmpty()) {
                Text(when (language) {
                    AppLanguage.BANGLA -> "এই কথোপকথন থেকে এখনও আপনার সম্পর্কে কোনো তথ্য শেখা হয়নি।"
                    AppLanguage.HINDI -> "इस बातचीत से अभी तक आपके बारे में कोई जानकारी नहीं सीखी गई है।"
                    AppLanguage.ENGLISH -> "No information about you has been learned from this conversation yet."
                })
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        profileFacts,
                        key = { memory -> "${memory.category}\u0000${memory.fact}\u0000${memory.replaces}" }
                    ) { memory ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("• ${memory.fact}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { forgetting = memory }) {
                                Text(when (language) {
                                    AppLanguage.BANGLA -> "ভুলে যান"
                                    AppLanguage.HINDI -> "भूल जाएँ"
                                    AppLanguage.ENGLISH -> "Forget"
                                }, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(when (language) {
                    AppLanguage.BANGLA -> "বন্ধ করুন"
                    AppLanguage.HINDI -> "बंद करें"
                    AppLanguage.ENGLISH -> "Close"
                })
            }
        }
    )
    forgetting?.let { memory ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = {
                Text(when (language) {
                    AppLanguage.BANGLA -> "এই স্মৃতি ভুলে যাবেন?"
                    AppLanguage.HINDI -> "यह याद भुलाएँ?"
                    AppLanguage.ENGLISH -> "Forget this memory?"
                })
            },
            text = { Text(memory.fact) },
            confirmButton = {
                TextButton(onClick = { onForget(memory); forgetting = null }) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "ভুলে যান"
                        AppLanguage.HINDI -> "भूल जाएँ"
                        AppLanguage.ENGLISH -> "Forget"
                    }, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetting = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }
}

@Composable
private fun SharedTopicDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chatLabel(language, "Add an ongoing shared topic", "একসঙ্গে আলোচনার বিষয় যোগ করুন", "साझा विषय जोड़ें")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    chatLabel(
                        language,
                        "Save a topic you both want to return to. It stays in this friend’s profile and is not added as a personal memory.",
                        "যে বিষয়টি পরে আবার আলোচনা করতে চান তা সংরক্ষণ করুন। এটি বন্ধুর প্রোফাইলে থাকবে, ব্যক্তিগত স্মৃতিতে যোগ হবে না।",
                        "जिस विषय पर फिर बात करनी है उसे सेव करें। यह दोस्त की प्रोफ़ाइल में रहेगा, निजी याद के रूप में नहीं।"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text(chatLabel(language, "Topic", "বিষয়", "विषय")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(500) },
                    label = { Text(chatLabel(language, "Optional note", "ঐচ্ছিক নোট", "वैकल्पिक नोट")) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), detail.trim()) }
            ) { Text(chatLabel(language, "Save topic", "সংরক্ষণ করুন", "सेव करें")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalizedText.CANCEL.resolve(language)) }
        }
    )
}

@Composable
private fun SharedRitualDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chatLabel(language, "Save a shared ritual", "একসঙ্গে করার অভ্যাস সংরক্ষণ করুন", "साझा गतिविधि सेव करें")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    chatLabel(
                        language,
                        "Save an activity you want to return to with this friend. It never schedules a message or starts by itself; tapping it prepares a prompt you can edit.",
                        "এই বন্ধুর সঙ্গে পরে আবার করতে চান এমন কিছু সংরক্ষণ করুন। এটি নিজে থেকে বার্তা পাঠায় না বা শুরু হয় না; ট্যাপ করলে সম্পাদনাযোগ্য বার্তা তৈরি হবে।",
                        "इस दोस्त के साथ दोबारा करने के लिए गतिविधि सेव करें। यह अपने-आप संदेश नहीं भेजती या शुरू नहीं होती; टैप करने पर आप संपादित करने के लिए संदेश मिलेगा।"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text(chatLabel(language, "Activity name", "অভ্যাসের নাম", "गतिविधि का नाम")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it.take(500) },
                    label = { Text(chatLabel(language, "What should you do together?", "একসঙ্গে কী করবেন?", "आप साथ में क्या करेंगे?")) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), activity.trim()) }
            ) { Text(chatLabel(language, "Save ritual", "সংরক্ষণ করুন", "सेव करें")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalizedText.CANCEL.resolve(language)) }
        }
    )
}

@Composable
private fun QuickNumberGameDialog(friendName: String, language: AppLanguage, onDismiss: () -> Unit) {
    var target by remember { mutableIntStateOf((1..10).random()) }
    var guess by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    val hasWon = result.isNotBlank() && guess.toIntOrNull() == target
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chatLabel(language, "Quick game with $friendName", "$friendName-এর সঙ্গে ছোট খেলা", "$friendName के साथ छोटा खेल")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    chatLabel(
                        language,
                        "Guess a number from 1 to 10. This lightweight game stays on your device and is not saved as chat or memory.",
                        "১ থেকে ১০-এর মধ্যে একটি সংখ্যা অনুমান করুন। এই ছোট খেলাটি ফোনেই থাকে; চ্যাট বা স্মৃতি হিসেবে সংরক্ষিত হয় না।",
                        "1 से 10 तक की संख्या का अनुमान लगाएँ। यह छोटा खेल डिवाइस पर ही रहता है और चैट या याद के रूप में सेव नहीं होता।"
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = guess,
                    onValueChange = { guess = it.filter(Char::isDigit).take(2); result = "" },
                    label = { Text(chatLabel(language, "Your guess", "আপনার অনুমান", "आपका अनुमान")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (result.isNotBlank()) Text(result, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasWon || (guess.toIntOrNull()?.let { it in 1..10 } == true),
                onClick = {
                    if (hasWon) {
                        target = (1..10).random()
                        guess = ""
                        result = ""
                        attempts = 0
                    } else {
                        attempts++
                        val guessNumber = guess.toIntOrNull()
                        result = when {
                            guessNumber == target -> chatLabel(language, "You got it in $attempts guesses!", "$attempts বারেই পেরেছেন!", "$attempts कोशिशों में सही जवाब!")
                            guessNumber != null && guessNumber < target -> chatLabel(language, "A little higher.", "আরও একটু বেশি।", "थोड़ा बड़ा।")
                            else -> chatLabel(language, "A little lower.", "আরও একটু কম।", "थोड़ा छोटा।")
                        }
                    }
                }
            ) {
                Text(if (hasWon) chatLabel(language, "Play again", "আবার খেলুন", "फिर खेलें") else chatLabel(language, "Guess", "অনুমান করুন", "अनुमान लगाएँ"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LocalizedText.CLOSE.resolve(language)) }
        }
    )
}

@Composable
private fun SessionSummariesDialog(summaries: List<SessionSummary>, language: AppLanguage, onDismiss: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", chatLocale(language))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (language) { AppLanguage.BANGLA -> "আগের সেশনের সারাংশ"; AppLanguage.HINDI -> "पिछले सत्रों का सारांश"; AppLanguage.ENGLISH -> "Previous session summaries" }) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (summaries.isEmpty()) {
                    Text(when (language) { AppLanguage.BANGLA -> "এখনও কোনো সেশন শেষ হয়নি"; AppLanguage.HINDI -> "अभी तक कोई सत्र समाप्त नहीं हुआ"; AppLanguage.ENGLISH -> "No sessions have ended yet" })
                } else summaries.forEach { summary ->
                    val endedAt = Instant.ofEpochMilli(summary.endedAtMillis).atZone(ZoneId.systemDefault())
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(endedAt.format(formatter), style = MaterialTheme.typography.labelLarge)
                        Text(summary.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(LocalizedText.CLOSE.resolve(language)) } }
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    language: AppLanguage,
    friendName: String? = null,
    onRemember: (() -> Unit)? = null,
    onSaveMoment: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onSnooze: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val timeText = remember(message.timestampMillis, language) {
        Instant.ofEpochMilli(message.timestampMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", chatLocale(language)))
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        androidx.compose.foundation.layout.Box {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 20.dp, topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isUser || !friendName.isNullOrBlank()) {
                        Text(
                            if (isUser) chatLabel(language, "You", "আপনি", "आप") else friendName.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val reasonLabel = if (isUser) null else when (message.category) {
                        "morning_greeting" -> chatLabel(language, "Friend started the conversation", "বন্ধু কথোপকথন শুরু করেছে", "दोस्त ने बातचीत शुरू की")
                        "follow_up" -> chatLabel(language, "Friend checked in", "বন্ধু খোঁজ নিয়েছে", "दोस्त ने हालचाल पूछा")
                        "busy_reply" -> chatLabel(language, "Scheduled reply", "সময় অনুযায়ী উত্তর", "तय समय पर जवाब")
                        "reminder" -> chatLabel(language, "Your reminder", "আপনার অনুস্মারক", "आपका रिमाइंडर")
                        else -> null
                    }
                    if (reasonLabel != null) {
                        Text(reasonLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val image = remember(message.imagePath) {
                        message.imagePath.takeIf { it.isNotBlank() && File(it).isFile }
                            ?.let(BitmapFactory::decodeFile)?.asImageBitmap()
                    }
                    if (image != null) Image(
                        image,
                        contentDescription = chatLabel(language, "Attached image", "সংযুক্ত ছবি", "संलग्न फ़ोटो"),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    )
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(LocalizedText.COPY.resolve(language)) }, onClick = {
                    menuOpen = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.content))
                })
                if (isUser && onRemember != null) {
                    DropdownMenuItem(
                        text = {
                            Text(when (language) {
                                AppLanguage.BANGLA -> "এই কথা মনে রাখুন"
                                AppLanguage.HINDI -> "इसे याद रखें"
                                AppLanguage.ENGLISH -> "Remember this"
                            })
                        },
                        onClick = { menuOpen = false; onRemember() }
                    )
                }
                if (onSaveMoment != null) {
                    DropdownMenuItem(
                        text = { Text(chatLabel(language, "Save as a shared moment", "একসঙ্গে কাটানো মুহূর্ত হিসেবে রাখুন", "साझा याद के रूप में सेव करें")) },
                        onClick = { menuOpen = false; onSaveMoment() }
                    )
                }
                if (!isUser && onRegenerate != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (message.category == ERROR_MESSAGE_CATEGORY) when (language) {
                                    AppLanguage.BANGLA -> "আবার চেষ্টা করুন"
                                    AppLanguage.HINDI -> "फिर कोशिश करें"
                                    AppLanguage.ENGLISH -> "Try again"
                                } else LocalizedText.REPLY_DIFFERENTLY.resolve(language)
                            )
                        },
                        onClick = { menuOpen = false; onRegenerate() }
                    )
                }
                if (onSnooze != null) {
                    DropdownMenuItem(
                        text = { Text(chatLabel(language, "Snooze 24 hours", "২৪ ঘণ্টা বিরতি", "24 घंटे स्थगित")) },
                        onClick = { menuOpen = false; onSnooze() }
                    )
                }
                if (onPause != null) {
                    DropdownMenuItem(
                        text = { Text(chatLabel(language, "Pause this friend's check-ins", "এই বন্ধুর খোঁজ নেওয়া বন্ধ", "इस दोस्त के हालचाल रोकें")) },
                        onClick = { menuOpen = false; onPause() }
                    )
                }
            }
        }
        Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private fun exportChat(context: Context, friend: Friend, messages: List<ChatMessage>) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    val transcript = messages.joinToString("\n") { message ->
        val who = if (message.role == "user") "You" else friend.name
        val time = Instant.ofEpochMilli(message.timestampMillis).atZone(ZoneId.systemDefault()).format(formatter)
        "[$time] $who: ${message.content}"
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Chat with ${friend.name}")
        putExtra(Intent.EXTRA_TEXT, transcript)
    }
    context.startActivity(Intent.createChooser(intent, "Export chat"))
}

private fun chatLabel(language: AppLanguage, english: String, bangla: String, hindi: String): String = when (language) {
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
    AppLanguage.ENGLISH -> english
}

private fun chatLocale(language: AppLanguage): java.util.Locale = when (language) {
    AppLanguage.BANGLA -> java.util.Locale.forLanguageTag("bn")
    AppLanguage.HINDI -> java.util.Locale.forLanguageTag("hi")
    AppLanguage.ENGLISH -> java.util.Locale.ENGLISH
}

private fun storeChatImage(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Unable to read image")
    val maxDimension = maxOf(bitmap.width, bitmap.height)
    if (maxDimension > 1280) {
        val scale = 1280f / maxDimension
        val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        if (resized !== bitmap) bitmap.recycle()
        bitmap = resized
    }
    var compressed: ByteArray
    var quality = 78
    do {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        compressed = output.toByteArray()
        quality -= 10
    } while (compressed.size > 300_000 && quality >= 48)
    if (compressed.size > 300_000) {
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.75f).toInt(), (bitmap.height * 0.75f).toInt(), true)
        if (scaled !== bitmap) bitmap.recycle()
        bitmap = scaled
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 55, output)
        compressed = output.toByteArray()
    }
    bitmap.recycle()
    require(compressed.size <= 300_000) { "Image is too large" }
    val directory = File(context.filesDir, "chat_images").apply { mkdirs() }
    val destination = File(directory, "${java.util.UUID.randomUUID()}.jpg")
    destination.writeBytes(compressed)
    return destination.absolutePath
}

@Composable
private fun ProfileCreationNotice(language: AppLanguage, generating: Boolean, failure: ProfileFailure?, onRetry: () -> Unit, onContinue: () -> Unit) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val recordedFailure = failure
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (generating || recordedFailure == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(when {
                        bangla -> "প্রোফাইল তৈরি হচ্ছে…"
                        hindi -> "प्रोफ़ाइल बनाई जा रही है…"
                        else -> "Creating their profile…"
                    })
                }
            } else {
                Text(recordedFailure.message, color = MaterialTheme.colorScheme.error)
                recordedFailure.detail?.let { detail ->
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        bangla -> "মেসেজ পাঠানোর আগে আবার চেষ্টা করুন, অথবা সাধারণ প্রোফাইল নিয়ে এগিয়ে যান।"
                        hindi -> "संदेश भेजने से पहले फिर कोशिश करें, या साधारण प्रोफ़ाइल के साथ आगे बढ़ें।"
                        else -> "Retry before sending, or continue with a basic profile."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) {
                        Text(when { bangla -> "আবার চেষ্টা করুন"; hindi -> "फिर कोशिश करें"; else -> "Retry" })
                    }
                    OutlinedButton(onClick = onContinue) {
                        Text(when { bangla -> "সাধারণ প্রোফাইল"; hindi -> "साधारण प्रोफ़ाइल"; else -> "Use basic profile" })
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble(friendName: String, language: AppLanguage) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
        Text(
            when (language) {
                AppLanguage.BANGLA -> "$friendName ভাবছে..."
                AppLanguage.HINDI -> "$friendName सोच रहा है..."
                AppLanguage.ENGLISH -> "$friendName is thinking..."
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RelationshipMeters(friend: Friend, language: AppLanguage, userAge: String) {
    val romance = romanceAllowed(userAge, friend.age)
    val loveUnlocked = romance && friend.friendshipScore > 70
    val hindi = language == AppLanguage.HINDI
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(when { language == AppLanguage.BANGLA -> "বন্ধুত্ব: ${friendshipLevel(friend.friendshipScore, language)}"; hindi -> "दोस्ती: ${friendshipLevel(friend.friendshipScore, language)}"; else -> "Friendship: ${friendshipLevel(friend.friendshipScore, language)}" }, style = MaterialTheme.typography.labelSmall)
            Text("${friend.friendshipScore}/100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
        Text(when {
            !romance -> when (language) {
                AppLanguage.BANGLA -> "ভালোবাসা: বন্ধ (তুমি এবং এই বন্ধু দুজনেই ১৮ বা তার বেশি হলে তবেই)"
                AppLanguage.HINDI -> "प्यार: बंद (सिर्फ़ तब, जब आप और यह दोस्त दोनों 18 या उससे बड़े हों)"
                AppLanguage.ENGLISH -> "Love: off (only if you and this friend are both 18 or older)"
            }
            loveUnlocked -> when (language) {
                AppLanguage.BANGLA -> "ভালোবাসা: ${loveLevel(friend.loveScore)} (${friend.loveScore}/100)"
                AppLanguage.HINDI -> "प्यार: ${loveLevel(friend.loveScore)} (${friend.loveScore}/100)"
                AppLanguage.ENGLISH -> "Love: ${loveLevel(friend.loveScore)} (${friend.loveScore}/100)"
            }
            else -> when (language) {
                AppLanguage.BANGLA -> "ভালোবাসা: বন্ধ (বন্ধুত্ব ৭১-এর বেশি হলে খুলবে)"
                AppLanguage.HINDI -> "प्यार: बंद (दोस्ती 70 से ऊपर होने पर खुलेगा)"
                AppLanguage.ENGLISH -> "Love: locked (unlocks above 70 friendship)"
            }
        }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (loveUnlocked) LinearProgressIndicator(progress = { friend.loveScore / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp))
    }
}
