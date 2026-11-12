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
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.*

@Composable
internal fun ChatScreen(friend: Friend, store: DostStore, onBack: () -> Unit, language: AppLanguage, onFloatingChat: () -> Unit, onFriendTimeZoneChange: (String) -> Unit, profileGenerating: Boolean, profileError: ProfileFailure?, onRetryProfile: () -> Unit, onUseBasicProfile: () -> Unit) {
    val context = LocalContext.current
    val messageMap = ConversationManager.messages.collectAsState().value
    val messages = messageMap[friend.id] ?: store.loadMessages(friend.id)
    var draft by remember { mutableStateOf("") }
    var selectedImagePath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { storeChatImage(context, uri) }
                .onSuccess { selectedImagePath = it; error = null }
                .onFailure { error = when (it.message) {
                    "Invalid image" -> "That file is not a valid image."
                    "Image is too large" -> "That photo is too large. Try a smaller one."
                    else -> "Could not load that image. Try a smaller photo."
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
    var showFriendInfo by remember { mutableStateOf(false) }
    var showUserInfo by remember { mutableStateOf(false) }
    var showSessionSummaries by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sessionSummaries by remember(friend.id) { mutableStateOf(store.loadSessionSummaries(friend.id).takeLast(10).reversed()) }
    val displayedMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }
    val profileBlocked = friend.profilePending
    val busyUntil = friend.replyAvailableAt(currentTime)
    val dailyBusyStart = LocalTime.of(friend.busyStartHour.coerceIn(0, 23), 0)
    val dailyBusyEnd = dailyBusyStart.plusHours(friend.busyDurationHours.coerceIn(4, 8).toLong())
    LaunchedEffect(friend.id) {
        while (true) {
            delay(60_000)
            currentTime = ZonedDateTime.now(validZoneId(store.userTimeZoneId()))
        }
    }
    DisposableEffect(friend.id) {
        ConversationManager.openChat(friend.id)
        onDispose { ConversationManager.closeChat(friend.id) }
    }
    LaunchedEffect(messages.size, sending, error) {
        val itemCount = messages.size + (if (sending) 1 else 0) + (if (error != null) 1 else 0)
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    Scaffold(topBar = { TopAppBar(title = { Row(modifier = Modifier.clickable { showFriendInfo = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { FriendPhoto(friend.photoUri, friend.name, Modifier.size(40.dp).clip(CircleShape)); Column { Text(friend.name + if (friend.mood.isNotBlank()) " ${friend.mood}" else ""); Text("${friendshipLevel(friend.friendshipScore)} · ${friend.friendshipScore}/100", style = MaterialTheme.typography.labelSmall); Text(if (busyUntil != null) "${friend.name} will reply at ${busyUntil.format(DateTimeFormatter.ofPattern("h:mm a"))}" else "Daily busy ${dailyBusyStart.format(DateTimeFormatter.ofPattern("h:mm a"))}–${dailyBusyEnd.format(DateTimeFormatter.ofPattern("h:mm a"))} (${friend.timeZoneId})", style = MaterialTheme.typography.labelSmall) } } }, navigationIcon = { TextButton(onClick = onBack) { Text(when { bangla -> "ফিরুন"; hindi -> "वापस"; else -> "Back" }) } }, actions = { androidx.compose.foundation.layout.Box { IconButton(onClick = { showChatMenu = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }; DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) { DropdownMenuItem(text = { Text("Search") }, onClick = { showChatMenu = false; showSearch = !showSearch; if (!showSearch) searchQuery = "" }); DropdownMenuItem(text = { Text("Export chat") }, onClick = { showChatMenu = false; exportChat(context, friend, messages) }); DropdownMenuItem(text = { Text("Your profile") }, onClick = { showChatMenu = false; showUserInfo = true }); DropdownMenuItem(text = { Text("Session summaries") }, onClick = { showChatMenu = false; sessionSummaries = store.loadSessionSummaries(friend.id).takeLast(10).reversed(); showSessionSummaries = true }); DropdownMenuItem(text = { Text("Minimize chat") }, onClick = { showChatMenu = false; onFloatingChat() }) } } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RelationshipMeters(friend = friend, language = language, userAge = store.userAge())
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(when { bangla -> "মেসেজ খুঁজুন..."; hindi -> "संदेश खोजें..."; else -> "Search messages..." }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                if (displayedMessages.isEmpty() && !profileBlocked) item { Text(when { bangla -> "${friend.name}-এর সাথে কথা বলা শুরু করুন।"; hindi -> "${friend.name} के साथ बातचीत शुरू करें।"; else -> "Start a conversation with ${friend.name}." }, modifier = Modifier.padding(12.dp)) }
                items(displayedMessages) { message ->
                    MessageBubble(
                        message = message,
                        friendName = friend.name,
                        onRegenerate = if (message.role == "assistant" && message.messageId == displayedMessages.lastOrNull { it.role == "assistant" }?.messageId) {
                            { ConversationManager.regenerateReply(friend, language) }
                        } else null
                    )
                }
                if (sending && waitingForSchedule) item { Text("${friend.name} will reply after the busy schedule.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                else if (sending) item { ThinkingBubble(friendName = friend.name, bangla = bangla, hindi = hindi) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) } }
            }
            if (profileBlocked) {
                ProfileCreationNotice(language, profileGenerating, profileError, onRetryProfile, onUseBasicProfile)
            }
            Surface(shadowElevation = 4.dp, tonalElevation = 2.dp, modifier = Modifier.imePadding()) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedImagePath.isNotBlank()) {
                        val preview = remember(selectedImagePath) { BitmapFactory.decodeFile(selectedImagePath)?.asImageBitmap() }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (preview != null) Image(preview, contentDescription = "Selected image", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                            TextButton(onClick = {
                                File(selectedImagePath).takeIf { it.isFile }?.delete()
                                selectedImagePath = ""
                            }) { Text("Remove image") }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            enabled = !profileBlocked
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_gallery),
                                contentDescription = when {
                                    bangla -> "ছবি সংযুক্ত করুন"
                                    hindi -> "फ़ोटो जोड़ें"
                                    else -> "Attach photo"
                                }
                            )
                        }
                        OutlinedTextField(draft, { draft = it }, enabled = !profileBlocked, modifier = Modifier.weight(1f), placeholder = { Text(when { bangla -> "একটি মেসেজ লিখুন..."; hindi -> "संदेश लिखें..."; else -> "Write a message..." }) }, maxLines = 4)
                        Button(enabled = !profileBlocked && (draft.isNotBlank() || selectedImagePath.isNotBlank()), onClick = {
                            if (friend.profilePending) return@Button
                            val text = draft.trim()
                            val imagePath = selectedImagePath
                            draft = ""
                            selectedImagePath = ""
                            error = null
                            ConversationManager.send(friend, text, language, imagePath)
                        }) { Text(when { bangla -> "পাঠান"; hindi -> "भेजें"; else -> "Send" }) }
                    }
                }
            }
        }
    }
    if (showFriendInfo) FriendInfoDialog(friend, store.userAge(), onTimeZoneChange = onFriendTimeZoneChange, onDismiss = { showFriendInfo = false })
    if (showUserInfo) ConversationProfileDialog(
        friend = friend,
        memories = store.loadUserMemories(friend.id),
        language = language,
        onDismiss = { showUserInfo = false }
    )
    if (showSessionSummaries) SessionSummariesDialog(sessionSummaries, language, onDismiss = { showSessionSummaries = false })
}

@Composable
private fun FriendInfoDialog(friend: Friend, userAge: String, onTimeZoneChange: (String) -> Unit, onDismiss: () -> Unit) {
    val start = LocalTime.of(friend.busyStartHour.coerceIn(0, 23), 0)
    val end = start.plusHours(friend.busyDurationHours.coerceIn(4, 8).toLong())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${friend.name}'s profile") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeZonePicker("Friend timezone", friend.timeZoneId, onTimeZoneChange)
                listOf(
                    "Age" to friend.age, "Gender" to friend.gender, "Personality" to friend.personality,
                    "Interests" to friend.interests, "Memories" to friend.memories, "Conversation style" to friend.conversationStyle,
                    "Family" to friend.family, "Family members" to friend.familyMembers, "Family activities" to friend.familyActivities,
                    "Financial condition" to friend.financialCondition, "Address" to friend.address, "Height" to friend.height,
                    "Weight" to friend.weight, "Facial features" to friend.facialFeatures, "Body features" to friend.bodyFeatures,
                    "Daily busy hours" to "${start.format(DateTimeFormatter.ofPattern("h:mm a"))}–${end.format(DateTimeFormatter.ofPattern("h:mm a"))} (${friend.timeZoneId})",
                    "Friendship" to "${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)",
                    "Love" to if (!romanceAllowed(userAge, friend.age)) "Off (only when you and this friend are both 18 or older)" else if (friend.friendshipScore > 70) "${loveLevel(friend.loveScore)} (${friend.loveScore}/100)" else "Locked"
                ).forEach { (label, value) ->
                    if (value.isNotBlank()) Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ConversationProfileDialog(friend: Friend, memories: List<UserMemory>, language: AppLanguage, onDismiss: () -> Unit) {
    val profileFacts = memories.filter { it.fact.isNotBlank() }.asReversed()
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
                    items(profileFacts) { memory ->
                        Text("• ${memory.fact}")
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
}

@Composable
private fun SessionSummariesDialog(summaries: List<SessionSummary>, language: AppLanguage, onDismiss: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MessageBubble(message: ChatMessage, friendName: String, onRegenerate: (() -> Unit)? = null) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val timeText = remember(message.timestampMillis) {
        Instant.ofEpochMilli(message.timestampMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        androidx.compose.foundation.layout.Box {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (isUser) "You" else friendName, style = MaterialTheme.typography.labelSmall)
                    val image = remember(message.imagePath) {
                        message.imagePath.takeIf { it.isNotBlank() && File(it).isFile }
                            ?.let(BitmapFactory::decodeFile)?.asImageBitmap()
                    }
                    if (image != null) Image(image, contentDescription = "Attached image", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                    Text(message.content, style = MaterialTheme.typography.bodyLarge)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Copy") }, onClick = {
                    menuOpen = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.content))
                })
                if (!isUser && onRegenerate != null) {
                    DropdownMenuItem(text = { Text("Reply differently") }, onClick = { menuOpen = false; onRegenerate() })
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
private fun ThinkingBubble(friendName: String, bangla: Boolean, hindi: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
        Text(when { bangla -> "$friendName ভাবছে..."; hindi -> "$friendName सोच रहा है..."; else -> "$friendName is thinking..." }, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RelationshipMeters(friend: Friend, language: AppLanguage, userAge: String) {
    val romance = romanceAllowed(userAge, friend.age)
    val loveUnlocked = romance && friend.friendshipScore > 70
    val hindi = language == AppLanguage.HINDI
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when { language == AppLanguage.BANGLA -> "বন্ধুত্ব: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)"; hindi -> "दोस्ती: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)"; else -> "Friendship: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)" }, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.fillMaxWidth())
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
        }, style = MaterialTheme.typography.labelMedium)
        if (loveUnlocked) LinearProgressIndicator(progress = { friend.loveScore / 100f }, modifier = Modifier.fillMaxWidth())
    }
}
