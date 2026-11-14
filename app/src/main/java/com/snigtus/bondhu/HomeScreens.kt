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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.*

@Composable
internal fun HomeScreen(language: AppLanguage, showFriendSearchAndCount: Boolean, userName: String, friends: List<Friend>, listState: LazyListState, onBrowseSystemFriends: () -> Unit, onOpenGames: () -> Unit, onOpenSettings: () -> Unit, onOpenFriendProfile: (Friend) -> Unit, onDeleteFriend: (Friend) -> Unit, onToggleFavorite: (Friend) -> Unit, onOpenChat: (Friend) -> Unit, generatingProfileIds: Set<String>, profileFailedIds: Set<String>) {
    val unreadCounts = ConversationManager.unread.collectAsState().value
    val messageMap = ConversationManager.messages.collectAsState().value
    var searchQuery by remember(showFriendSearchAndCount) { mutableStateOf("") }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val customFriendCount = friends.count { it.catalogId.isBlank() }
    val systemFriendCount = friends.size - customFriendCount
    val canAddCustomFriend = customFriendCount < MAX_CUSTOM_FRIENDS
    val canAddAnyFriend = canAddCustomFriend || systemFriendCount < MAX_SYSTEM_FRIENDS
    val visibleFriends = friends
        .filter { friend ->
            val query = searchQuery.trim()
            query.isBlank() || friend.name.contains(query, ignoreCase = true)
                || messageMap[friend.id].orEmpty().filterNot(::isChatNotice).any { it.content.contains(query, ignoreCase = true) }
        }
        .sortedByDescending { it.favorite }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(when { bangla -> "চ্যাট"; hindi -> "चैट"; else -> "Chats" }, style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onOpenGames) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = when { bangla -> "খেলা"; hindi -> "खेल"; else -> "Games" }
                        )
                    }
                    TextButton(onClick = onOpenSettings) { Text(when { bangla -> "সেটিংস"; hindi -> "सेटिंग्स"; else -> "Settings" }) }
                    TextButton(onClick = onBrowseSystemFriends, enabled = canAddAnyFriend) { Text(when { bangla -> "নতুন চ্যাট"; hindi -> "नई चैट"; else -> "New chat" }) }
                }
            )
        },
        floatingActionButton = {
            if (canAddAnyFriend) {
                FloatingActionButton(
                    onClick = onBrowseSystemFriends,
                    modifier = Modifier.semantics {
                        contentDescription = when { bangla -> "বন্ধু যোগ করুন"; hindi -> "दोस्त जोड़ें"; else -> "Add a friend" }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = when { bangla -> "বন্ধু যোগ করুন"; hindi -> "दोस्त जोड़ें"; else -> "Add a friend" }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(when { bangla -> "বন্ধুরা"; hindi -> "दोस्त"; else -> "Friends" }, style = MaterialTheme.typography.titleMedium)
                        if (showFriendSearchAndCount) {
                            Text("${friends.size}/$MAX_TOTAL_FRIENDS", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (showFriendSearchAndCount) {
                        Text(
                            when {
                                bangla -> "নিজস্ব $customFriendCount/$MAX_CUSTOM_FRIENDS · ক্যাটালগ $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                                hindi -> "कस्टम $customFriendCount/$MAX_CUSTOM_FRIENDS · कैटलॉग $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                                else -> "Your own $customFriendCount/$MAX_CUSTOM_FRIENDS · Catalog $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            label = {
                                Text(when {
                                    bangla -> "বন্ধু বা কথোপকথন খুঁজুন"
                                    hindi -> "दोस्त या बातचीत खोजें"
                                    else -> "Search friends or conversations"
                                })
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (friends.any { it.profilePending }) {
                        val creating = friends.any { it.profilePending && it.id !in profileFailedIds }
                        Text(
                            when {
                                creating && bangla -> "প্রোফাইল তৈরি হচ্ছে…"
                                creating && hindi -> "प्रोफ़ाइल बनाई जा रही है…"
                                creating -> "Creating their profile…"
                                bangla -> "প্রোফাইল তৈরি হয়নি। চ্যাটে গিয়ে আবার চেষ্টা করুন।"
                                hindi -> "प्रोफ़ाइल नहीं बनी। चैट खोलकर फिर कोशिश करें।"
                                else -> "A profile wasn't created. Open the chat to retry."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (creating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)) }
            if (friends.isEmpty()) item { EmptyFriendsState(onAdd = onBrowseSystemFriends, language = language) }
            items(visibleFriends, key = { it.id }) { friend ->
                FriendRow(
                    friend,
                    unread = unreadCounts[friend.id] ?: 0,
                    onOpenChat = { onOpenChat(friend) },
                    onOpenFriendProfile = { onOpenFriendProfile(friend) },
                    onDelete = { onDeleteFriend(friend) },
                    onToggleFavorite = { onToggleFavorite(friend) },
                    language = language,
                    profileGenerating = friend.id in generatingProfileIds || friend.id !in profileFailedIds,
                    profileFailed = friend.id in profileFailedIds
                )
            }
            if (visibleFriends.isEmpty() && friends.isNotEmpty() && searchQuery.isNotBlank()) item {
                Text(
                    when (language) {
                        AppLanguage.BANGLA -> "এই খোঁজে কোনো বন্ধু পাওয়া যায়নি।"
                        AppLanguage.HINDI -> "इस खोज में कोई दोस्त नहीं मिला।"
                        AppLanguage.ENGLISH -> "No friends match your search."
                    },
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyFriendsState(onAdd: () -> Unit, language: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DostBrand()
        Text(when (language) {
            AppLanguage.BANGLA -> "এখনও কোনো বন্ধু নেই"
            AppLanguage.HINDI -> "अभी कोई दोस्त नहीं"
            AppLanguage.ENGLISH -> "No friends yet"
        }, style = MaterialTheme.typography.titleLarge)
        Text(when (language) {
            AppLanguage.BANGLA -> "একজন কাল্পনিক AI বন্ধু বেছে নিন—প্রত্যেকের নিজস্ব ব্যক্তিত্ব আছে। কথোপকথনে তারা আপনার সম্পর্কে প্রাসঙ্গিক তথ্য মনে রাখতে পারে; আপনি তা পর্যালোচনা বা মুছে ফেলতে পারবেন।"
            AppLanguage.HINDI -> "एक काल्पनिक AI दोस्त चुनें—हर एक का अपना व्यक्तित्व है। बातचीत से वे आपके बारे में उपयोगी बातें याद रख सकते हैं; आप उन्हें देख या हटा सकते हैं।"
            AppLanguage.ENGLISH -> "Choose a fictional AI friend with their own personality. They can remember useful details from your conversations, and you can review or remove those memories."
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(when (language) {
            AppLanguage.BANGLA -> "বন্ধুরা নিজে থেকে বার্তা দিলে সেটি সেটিংসে চালু বা বন্ধ করতে পারবেন।"
            AppLanguage.HINDI -> "दोस्तों के अपने-आप संदेश भेजने को सेटिंग्स में चालू या बंद कर सकते हैं।"
            AppLanguage.ENGLISH -> "You can turn friend-initiated messages on or off in Settings."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) {
            Text(when (language) {
                AppLanguage.BANGLA -> "বন্ধু যোগ করুন"
                AppLanguage.HINDI -> "दोस्त जोड़ें"
                AppLanguage.ENGLISH -> "Add a friend"
            })
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, unread: Int, onOpenChat: () -> Unit, onOpenFriendProfile: () -> Unit, onDelete: () -> Unit, onToggleFavorite: () -> Unit, language: AppLanguage, profileGenerating: Boolean, profileFailed: Boolean) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lastMessage = ConversationManager.messages.collectAsState().value[friend.id]?.lastOrNull { !isChatNotice(it) }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val subtitle = when {
        friend.profilePending && (profileGenerating || !profileFailed) -> when {
            bangla -> "প্রোফাইল তৈরি হচ্ছে…"
            hindi -> "प्रोफ़ाइल बनाई जा रही है…"
            else -> "Creating their profile…"
        }
        friend.profilePending -> when {
            bangla -> "প্রোফাইল তৈরি হয়নি। আবার চেষ্টা করতে খুলুন।"
            hindi -> "प्रोफ़ाइल नहीं बनी। फिर कोशिश के लिए खोलें।"
            else -> "Profile wasn't created. Open to retry."
        }
        else -> {
            val lastActivity = lastMessage?.content?.take(36) ?: friendshipLevel(friend.friendshipScore, language)
            val available = friend.replyAvailableAt(ZonedDateTime.now(validZoneId(friend.timeZoneId))) == null
            val status = when {
                bangla && available -> "উপলভ্য"
                bangla -> "ব্যস্ত"
                hindi && available -> "उपलब्ध"
                hindi -> "व्यस्त"
                available -> "Available"
                else -> "Busy"
            }
            "$status · $lastActivity"
        }
    }
    androidx.compose.foundation.layout.Box {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChat).padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(friend.photoUri, friend.name, Modifier.size(46.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (if (friend.favorite) "★ " else "") + friend.name +
                        if (friend.mood.isNotBlank()) " ${friend.mood}" else "",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (unread > 0) androidx.compose.ui.text.font.FontWeight.Bold
                        else MaterialTheme.typography.titleMedium.fontWeight
                    )
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (unread > 0) androidx.compose.ui.text.font.FontWeight.Bold
                        else MaterialTheme.typography.bodyMedium.fontWeight
                    ), maxLines = 1,
                    color = if (friend.profilePending && profileFailed && !profileGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.size(width = 56.dp, height = 6.dp))
            if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = when { bangla -> "বন্ধুর বিকল্প"; hindi -> "दोस्त के विकल्प"; else -> "Friend options" }
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(when {
                        bangla -> "বন্ধুর প্রোফাইল"
                        hindi -> "दोस्त की प्रोफ़ाइल"
                        else -> "Friend profile"
                    })
                },
                onClick = { menuExpanded = false; onOpenFriendProfile() }
            )
            DropdownMenuItem(
                text = { Text(when {
                    bangla && friend.favorite -> "প্রিয় থেকে সরান"
                    bangla -> "প্রিয় হিসেবে চিহ্নিত করুন"
                    hindi && friend.favorite -> "पसंदीदा से हटाएँ"
                    hindi -> "पसंदीदा बनाएँ"
                    friend.favorite -> "Remove from favorites"
                    else -> "Add to favorites"
                }) },
                onClick = { menuExpanded = false; onToggleFavorite() }
            )
            DropdownMenuItem(text = { Text(when { language == AppLanguage.BANGLA -> "মুছুন"; language == AppLanguage.HINDI -> "हटाएं"; else -> "Delete" }, color = MaterialTheme.colorScheme.error) }, onClick = { menuExpanded = false; confirmDelete = true })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(when { bangla -> "বন্ধুকে মুছবেন?"; hindi -> "दोस्त हटाएँ?"; else -> "Delete friend?" }) },
            text = { Text(when { bangla -> "${friend.name} এবং তার চ্যাট মুছে যাবে।"; hindi -> "${friend.name} और उसकी चैट हटा दी जाएगी।"; else -> "${friend.name} and this chat will be removed." }) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(when { bangla -> "মুছুন"; hindi -> "हटाएँ"; else -> "Delete" }, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" })
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFriendDialog(language: AppLanguage, onDismiss: () -> Unit, onAdd: (Friend) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }
    var workSchedule by remember { mutableStateOf<Boolean?>(null) }
    var customStartHour by remember { mutableStateOf<Int?>(null) }
    var customDurationHours by remember { mutableStateOf<Int?>(null) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var friendTimeZoneId by remember { mutableStateOf(ZoneId.systemDefault().id) }
    val context = LocalContext.current
    val isWorkSchedule = workSchedule ?: ((age.toIntOrNull() ?: 18) >= 18)
    val busyStartHour = customStartHour ?: if (isWorkSchedule) 9 else 8
    val busyDurationHours = customDurationHours ?: if (isWorkSchedule) 8 else 6
    val busyEndTime = LocalTime.of(busyStartHour, 0).plusHours(busyDurationHours.toLong())
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            photoUri = it.toString()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(LocalizedText.ADD_FRIEND_TITLE.resolve(language)) }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(LocalizedText.FRIEND_NAME.resolve(language)) })
        OutlinedTextField(age, { age = it }, label = { Text(LocalizedText.FRIEND_AGE.resolve(language)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        friendAgeError(age, language)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        friendUnder18RomanceNote(age, language)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        GenderField(gender, onValueChange = { gender = it }, language = language)
        OutlinedTextField(
            value = personality,
            onValueChange = { personality = it },
            label = { Text(when (language) { AppLanguage.BANGLA -> "ব্যক্তিত্ব"; AppLanguage.HINDI -> "व्यक्तित्व"; AppLanguage.ENGLISH -> "Personality" }) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        OutlinedTextField(
            value = greeting,
            onValueChange = { greeting = it },
            label = { Text(when (language) { AppLanguage.BANGLA -> "প্রথম বার্তা"; AppLanguage.HINDI -> "पहला संदेश"; AppLanguage.ENGLISH -> "First message" }) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        TimeZonePicker(LocalizedText.FRIEND_TIMEZONE.resolve(language), friendTimeZoneId, onTimeZoneChange = { friendTimeZoneId = it })
        Text(LocalizedText.DAILY_BUSY_SCHEDULE.resolve(language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isWorkSchedule) Button(onClick = { workSchedule = false; customStartHour = null; customDurationHours = null }) { Text(LocalizedText.SCHOOL.resolve(language)) }
            else OutlinedButton(onClick = { workSchedule = false; customStartHour = null; customDurationHours = null }) { Text(LocalizedText.SCHOOL.resolve(language)) }
            if (isWorkSchedule) Button(onClick = { workSchedule = true; customStartHour = null; customDurationHours = null }) { Text(LocalizedText.WORK.resolve(language)) }
            else OutlinedButton(onClick = { workSchedule = true; customStartHour = null; customDurationHours = null }) { Text(LocalizedText.WORK.resolve(language)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                android.app.TimePickerDialog(context, { _, hour, _ -> customStartHour = hour }, busyStartHour, 0, false).show()
            }) { Text(LocalizedText.SCHEDULE_STARTS.resolve(language, LocalTime.of(busyStartHour, 0).format(DateTimeFormatter.ofPattern("h:mm a")))) }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { durationMenuExpanded = true }) { Text(LocalizedText.SCHEDULE_DURATION.resolve(language, busyDurationHours)) }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    (4..8).forEach { hours ->
                        DropdownMenuItem(text = { Text(LocalizedText.SCHEDULE_HOURS.resolve(language, hours)) }, onClick = { customDurationHours = hours; durationMenuExpanded = false })
                    }
                }
            }
            Text(LocalizedText.SCHEDULE_UNTIL.resolve(language, busyEndTime.format(DateTimeFormatter.ofPattern("h:mm a"))))
        }
        Text(LocalizedText.BUSY_SCHEDULE_NOTE.resolve(language, friendTimeZoneId), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(photoUri, name.ifBlank { "?" }, Modifier.size(56.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (photoUri.isBlank()) LocalizedText.NO_PHOTO_SELECTED.resolve(language) else LocalizedText.PHOTO_SELECTED.resolve(language))
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (photoUri.isBlank()) LocalizedText.CHOOSE_PHOTO.resolve(language) else LocalizedText.CHANGE_PHOTO.resolve(language)) }
            }
            if (photoUri.isNotBlank()) TextButton(onClick = { photoUri = "" }) { Text(LocalizedText.REMOVE.resolve(language)) }
        }
    } }, confirmButton = { Button(onClick = { onAdd(Friend(id = java.util.UUID.randomUUID().toString(), name = name, age = age.trim(), gender = gender, personality = personality.trim(), photoUri = photoUri, busyStartHour = busyStartHour, busyDurationHours = busyDurationHours, busyReason = if (isWorkSchedule) "Work" else "School", timeZoneId = friendTimeZoneId, greeting = greeting.trim())) }, enabled = name.isNotBlank() && friendAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank() && personality.isNotBlank() && greeting.isNotBlank()) { Text(LocalizedText.ADD.resolve(language)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizedText.CANCEL.resolve(language)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeZonePicker(label: String, timeZoneId: String, onTimeZoneChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val availableZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val matchingZones = if (query.isBlank()) {
        listOf(timeZoneId, "UTC", "Asia/Dhaka", "America/New_York", "Europe/London").distinct()
    } else {
        availableZones.filter { it.contains(query.trim(), ignoreCase = true) }.take(50)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (expanded) query else "$timeZoneId · ${ZonedDateTime.now(validZoneId(timeZoneId)).format(DateTimeFormatter.ofPattern("h:mm a"))}",
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded && matchingZones.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matchingZones.forEach { zone ->
                DropdownMenuItem(
                    text = { Text("$zone · ${ZonedDateTime.now(validZoneId(zone)).format(DateTimeFormatter.ofPattern("h:mm a"))}") },
                    onClick = { onTimeZoneChange(zone); query = ""; expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    language: AppLanguage,
    chatLanguage: AppLanguage,
    showFriendSearchAndCount: Boolean,
    friends: List<Friend>,
    userTimeZoneId: String,
    signedInEmail: String?,
    openRouterApiKey: String,
    aiModel: String,
    anonymousTelemetryEnabled: Boolean,
    proactiveEnabled: Boolean,
    proactiveCadenceMinutes: Int,
    automaticFollowUpMinutes: Int,
    quietHoursStartMinute: Int,
    quietHoursEndMinute: Int,
    settingsNotice: String,
    onBack: () -> Unit,
    onDeleteFriend: (Friend) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onChatLanguageChange: (AppLanguage) -> Unit,
    onShowFriendSearchAndCountChange: (Boolean) -> Unit,
    onUserTimeZoneChange: (String) -> Unit,
    onAnonymousTelemetryChange: (Boolean) -> Unit,
    onProactiveEnabledChange: (Boolean) -> Unit,
    onProactiveCadenceChange: (Int) -> Unit,
    onAutomaticFollowUpChange: (Int) -> Unit,
    onQuietHoursChange: (Int, Int) -> Unit,
    onAiSettingsSave: (String, String) -> Unit,
    onKnowledge: () -> Unit,
    onSignOut: () -> Unit,
    onExportLocalData: () -> Unit,
    onImportLocalData: () -> Unit,
    onDeleteServerArchive: () -> Unit
) {
    var showAiSettings by remember { mutableStateOf(false) }
    var confirmArchiveDelete by remember { mutableStateOf(false) }
    var friendToDelete by remember { mutableStateOf<Friend?>(null) }
    val context = LocalContext.current
    val title = when (language) {
        AppLanguage.BANGLA -> "সেটিংস"
        AppLanguage.HINDI -> "सेटिंग्स"
        AppLanguage.ENGLISH -> "Settings"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!signedInEmail.isNullOrBlank()) item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "অ্যাকাউন্ট"
                    AppLanguage.HINDI -> "खाता"
                    AppLanguage.ENGLISH -> "Account"
                }) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "সাইন ইন করা অ্যাকাউন্ট: $signedInEmail"
                        AppLanguage.HINDI -> "साइन इन खाता: $signedInEmail"
                        AppLanguage.ENGLISH -> "Signed in as $signedInEmail"
                    }, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(when (language) {
                            AppLanguage.BANGLA -> "সাইন আউট"
                            AppLanguage.HINDI -> "साइन आउट"
                            AppLanguage.ENGLISH -> "Sign out"
                        })
                    }
                }
            }
            item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "অ্যাপ পছন্দ"
                    AppLanguage.HINDI -> "ऐप प्राथमिकताएँ"
                    AppLanguage.ENGLISH -> "App preferences"
                }) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "অ্যাপের ভাষা"
                        AppLanguage.HINDI -> "ऐप की भाषा"
                        AppLanguage.ENGLISH -> "App language"
                    }, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                            enabled = language != AppLanguage.ENGLISH,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.ENGLISH, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("English")
                        }
                        Button(
                            onClick = { onLanguageChange(AppLanguage.BANGLA) },
                            enabled = language != AppLanguage.BANGLA,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.BANGLA, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("বাংলা")
                        }
                        Button(
                            onClick = { onLanguageChange(AppLanguage.HINDI) },
                            enabled = language != AppLanguage.HINDI,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.HINDI, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("हिन्दी")
                        }
                    }
                    Text(when (language) {
                        AppLanguage.BANGLA -> "চ্যাটের ভাষা"
                        AppLanguage.HINDI -> "चैट की भाषा"
                        AppLanguage.ENGLISH -> "Chat language"
                    }, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onChatLanguageChange(AppLanguage.ENGLISH) },
                            enabled = chatLanguage != AppLanguage.ENGLISH,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.ENGLISH, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("English")
                        }
                        Button(
                            onClick = { onChatLanguageChange(AppLanguage.BANGLA) },
                            enabled = chatLanguage != AppLanguage.BANGLA,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.BANGLA, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("বাংলা")
                        }
                        Button(
                            onClick = { onChatLanguageChange(AppLanguage.HINDI) },
                            enabled = chatLanguage != AppLanguage.HINDI,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            LanguageFlagIcon(AppLanguage.HINDI, Modifier.size(width = 20.dp, height = 13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("हिन्दी")
                        }
                    }
                    Text(when (language) {
                        AppLanguage.BANGLA -> "এই পছন্দটি AI-এর উত্তরের ভাষা বদলায়, অ্যাপের মেনুর ভাষা নয়।"
                        AppLanguage.HINDI -> "यह विकल्प AI के जवाबों की भाषा बदलता है, ऐप के मेनू की नहीं।"
                        AppLanguage.ENGLISH -> "This changes the language AI replies in, not the app menus."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (language) {
                                AppLanguage.BANGLA -> "বন্ধুর সংখ্যা ও খোঁজ দেখান"
                                AppLanguage.HINDI -> "दोस्तों की संख्या और खोज दिखाएँ"
                                AppLanguage.ENGLISH -> "Show friend count and search"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Switch(
                            checked = showFriendSearchAndCount,
                            onCheckedChange = onShowFriendSearchAndCountChange
                        )
                    }
                    TimeZonePicker(LocalizedText.YOUR_TIMEZONE.resolve(language), userTimeZoneId, onTimeZoneChange = onUserTimeZoneChange)
                }
            }
            item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "ঐচ্ছিক ব্যবহার বিশ্লেষণ"
                    AppLanguage.HINDI -> "वैकल्पिक उपयोग विश्लेषण"
                    AppLanguage.ENGLISH -> "Optional usage analytics"
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (language) {
                                AppLanguage.BANGLA -> "বেনামী ইভেন্ট কাউন্টার শেয়ার করুন"
                                AppLanguage.HINDI -> "गुमनाम इवेंट काउंटर साझा करें"
                                AppLanguage.ENGLISH -> "Share anonymous event counts"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Switch(
                            checked = anonymousTelemetryEnabled,
                            onCheckedChange = onAnonymousTelemetryChange
                        )
                    }
                    Text(
                        when (language) {
                            AppLanguage.BANGLA -> "ঐচ্ছিক। বার্তা, প্রোফাইলের তথ্য, অ্যাকাউন্ট বা ইনস্টলেশন আইডি পাঠানো হয় না। সার্ভার শুধু ইভেন্টের দৈনিক মোট সংখ্যা ৯০ দিন রাখে; নেটওয়ার্ক অনুরোধে সার্ভার আপনার IP ঠিকানা দেখতে পারে।"
                            AppLanguage.HINDI -> "वैकल्पिक। संदेश, प्रोफ़ाइल जानकारी, खाता या इंस्टॉलेशन ID नहीं भेजी जाती। सर्वर केवल इवेंट की दैनिक कुल संख्या 90 दिनों तक रखता है; नेटवर्क अनुरोध में सर्वर आपका IP पता देख सकता है।"
                            AppLanguage.ENGLISH -> "Optional. Messages, profile details, account data, and installation IDs are not sent. The server keeps only daily event totals for 90 days; it may see your IP address as part of the network request."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "বন্ধুর বার্তা"
                    AppLanguage.HINDI -> "दोस्तों के संदेश"
                    AppLanguage.ENGLISH -> "Friend messages"
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (language) {
                                AppLanguage.BANGLA -> "বন্ধুদের নিজে থেকে বার্তা দিতে দিন"
                                AppLanguage.HINDI -> "दोस्तों को बातचीत शुरू करने दें"
                                AppLanguage.ENGLISH -> "Let friends start conversations"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Switch(checked = proactiveEnabled, onCheckedChange = onProactiveEnabledChange)
                    }
                    Text(
                        when (language) {
                            AppLanguage.BANGLA -> "এটি ডিফল্টভাবে বন্ধ। চালু করলে রুটিন শুভেচ্ছা ও স্বয়ংক্রিয় ফলো-আপ নিয়ন্ত্রিত হবে; আপনার নির্ধারিত অনুস্মারক চালু থাকবে। Android 13 বা নতুন সংস্করণে চালু করার পর নোটিফিকেশনের অনুমতি চাওয়া হবে।"
                            AppLanguage.HINDI -> "यह डिफ़ॉल्ट रूप से बंद है। चालू करने पर नियमित अभिवादन और स्वचालित फ़ॉलो-अप नियंत्रित होंगे; आपके सेट किए रिमाइंडर चलते रहेंगे। Android 13 या नए संस्करण पर चालू करने के बाद नोटिफ़िकेशन अनुमति माँगी जाएगी।"
                            AppLanguage.ENGLISH -> "Off by default. When enabled, this controls routine greetings and automatic check-ins; reminders you set yourself still run. On Android 13+, notification permission is requested after you turn this on."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (language) {
                            AppLanguage.BANGLA -> "শান্ত সময়"
                            AppLanguage.HINDI -> "शांत समय"
                            AppLanguage.ENGLISH -> "Quiet hours"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            when (language) { AppLanguage.BANGLA -> "শুরু"; AppLanguage.HINDI -> "शुरू"; else -> "Start" } to quietHoursStartMinute,
                            when (language) { AppLanguage.BANGLA -> "শেষ"; AppLanguage.HINDI -> "समाप्त"; else -> "End" } to quietHoursEndMinute
                        ).forEachIndexed { index, (label, minute) ->
                            OutlinedButton(
                                enabled = proactiveEnabled,
                                onClick = {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, hour, selectedMinute ->
                                            val selected = hour * 60 + selectedMinute
                                            val start = if (index == 0) selected else quietHoursStartMinute
                                            val end = if (index == 1) selected else quietHoursEndMinute
                                            if (start != end) onQuietHoursChange(start, end)
                                        },
                                        minute / 60,
                                        minute % 60,
                                        false
                                    ).show()
                                }
                            ) {
                                Text("$label ${LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern("h:mm a"))}")
                            }
                        }
                    }
                    Text(
                        when (language) {
                            AppLanguage.BANGLA -> "বন্ধুদের বার্তার মধ্যে ন্যূনতম ব্যবধান: $proactiveCadenceMinutes মিনিট"
                            AppLanguage.HINDI -> "दोस्तों के संदेशों के बीच न्यूनतम अंतर: $proactiveCadenceMinutes मिनट"
                            AppLanguage.ENGLISH -> "Minimum interval between proactive messages: $proactiveCadenceMinutes minutes"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(60, 180, 360, 720).forEach { minutes ->
                        TextButton(
                            enabled = proactiveEnabled && minutes != proactiveCadenceMinutes,
                            onClick = { onProactiveCadenceChange(minutes) }
                        ) {
                            Text(when (language) {
                                AppLanguage.BANGLA -> "$minutes মিনিট"
                                AppLanguage.HINDI -> "$minutes मिनट"
                                AppLanguage.ENGLISH -> "$minutes min"
                            })
                        }
                    }
                    Text(
                        when (language) {
                            AppLanguage.BANGLA -> "স্বয়ংক্রিয় কথোপকথন ফলো-আপ: $automaticFollowUpMinutes মিনিট"
                            AppLanguage.HINDI -> "स्वचालित बातचीत फ़ॉलो-अप: $automaticFollowUpMinutes मिनट"
                            AppLanguage.ENGLISH -> "Automatic conversation follow-up: $automaticFollowUpMinutes minutes"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(5, 15, 30, 60, 120).forEach { minutes ->
                        TextButton(
                            enabled = proactiveEnabled && minutes != automaticFollowUpMinutes,
                            onClick = { onAutomaticFollowUpChange(minutes) }
                        ) {
                            Text(when (language) {
                                AppLanguage.BANGLA -> "$minutes মিনিট"
                                AppLanguage.HINDI -> "$minutes मिनट"
                                AppLanguage.ENGLISH -> "$minutes min"
                            })
                        }
                    }
                }
            }
            item {
                SettingsSection(LocalizedText.AI_PROVIDER_MODEL.resolve(language)) {
                    Button(onClick = { showAiSettings = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(LocalizedText.AI_PROVIDER_MODEL.resolve(language))
                    }
                    Text(LocalizedText.CHAT_ARCHIVE_NOTICE.resolve(language), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SettingsSection("Local data") {
                    Text("Move your profile, friends, routines, reminders, memories, summaries, and chat history with a passphrase-encrypted backup. Your API key and installation id are not included. Keep the passphrase separate; it cannot be recovered.", style = MaterialTheme.typography.bodySmall)
                    Text("The installation id is not transferred. Delete the old phone's server archive from that phone before moving to a new one.", style = MaterialTheme.typography.bodySmall)
                    Text("Importing replaces the current profile, friends, memories, summaries, and chat history. Older unencrypted JSON backups can still be imported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onExportLocalData, modifier = Modifier.fillMaxWidth()) { Text("Export encrypted backup") }
                    OutlinedButton(onClick = onImportLocalData, modifier = Modifier.fillMaxWidth()) { Text("Import backup") }
                    if (settingsNotice.isNotBlank()) {
                        Text(
                            settingsNotice,
                            color = if (settingsNotice.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                SettingsSection("Server archive") {
                    Text("Delete this installation's indexed chat archive and attached images from Dost's server. Older records without an installation id cannot be matched.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmArchiveDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete server archive")
                    }
                }
            }
            item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "আপনার তথ্য"
                    AppLanguage.HINDI -> "आपकी जानकारी"
                    AppLanguage.ENGLISH -> "Your information"
                }) {
                    Button(onClick = onKnowledge, modifier = Modifier.fillMaxWidth()) {
                        Text(when (language) {
                            AppLanguage.BANGLA -> "আমার সম্পর্কে জানা তথ্য"
                            AppLanguage.HINDI -> "मेरे बारे में जानकारी"
                            AppLanguage.ENGLISH -> "Knowledge about me"
                        })
                    }
                }
            }
            item {
                SettingsSection(when (language) {
                    AppLanguage.BANGLA -> "বন্ধু পরিচালনা"
                    AppLanguage.HINDI -> "दोस्त प्रबंधित करें"
                    AppLanguage.ENGLISH -> "Manage friends"
                }) {
                    if (friends.isEmpty()) {
                        Text(when (language) {
                            AppLanguage.BANGLA -> "এখনও কোনো বন্ধু নেই"
                            AppLanguage.HINDI -> "अभी कोई दोस्त नहीं"
                            AppLanguage.ENGLISH -> "No friends yet"
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    friends.forEach { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(friend.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { friendToDelete = friend }) {
                                Text(when (language) {
                                    AppLanguage.BANGLA -> "মুছুন"
                                    AppLanguage.HINDI -> "हटाएँ"
                                    AppLanguage.ENGLISH -> "Delete"
                                }, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAiSettings) AiSettingsDialog(
        language = language,
        apiKey = openRouterApiKey,
        initialModel = aiModel,
        onDismiss = { showAiSettings = false },
        onSave = { apiKey, model -> onAiSettingsSave(apiKey, model); showAiSettings = false }
    )
    if (confirmArchiveDelete) {
        AlertDialog(
            onDismissRequest = { confirmArchiveDelete = false },
            title = { Text("Delete server archive?") },
            text = { Text("This permanently removes the indexed chat sessions and images for this installation from Dost's server. It does not delete copies held by OpenRouter or model providers.") },
            confirmButton = {
                TextButton(onClick = { confirmArchiveDelete = false; onDeleteServerArchive() }) {
                    Text("Delete archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmArchiveDelete = false }) { Text("Cancel") } }
        )
    }
    friendToDelete?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text(when (language) {
                AppLanguage.BANGLA -> "বন্ধুকে মুছবেন?"
                AppLanguage.HINDI -> "दोस्त हटाएँ?"
                AppLanguage.ENGLISH -> "Delete friend?"
            }) },
            text = { Text(when (language) {
                AppLanguage.BANGLA -> "${friend.name} এবং তার চ্যাট মুছে যাবে।"
                AppLanguage.HINDI -> "${friend.name} और उसकी चैट हटा दी जाएगी।"
                AppLanguage.ENGLISH -> "${friend.name} and this chat will be removed."
            }) },
            confirmButton = {
                TextButton(onClick = { friendToDelete = null; onDeleteFriend(friend) }) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "মুছুন"
                        AppLanguage.HINDI -> "हटाएँ"
                        AppLanguage.ENGLISH -> "Delete"
                    }, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToDelete = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        )
    }
}

@Composable
private fun AiSettingsDialog(language: AppLanguage, apiKey: String, initialModel: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var editedApiKey by remember { mutableStateOf(apiKey) }
    var model by remember { mutableStateOf(initialModel) }
    var modelSearch by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val matchingModels = OpenRouterClient.modelCatalog.filter { modelSearch.isBlank() || it.contains(modelSearch, ignoreCase = true) }
    val modelIsValid = model.matches(Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)?"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalizedText.AI_PROVIDER_MODEL.resolve(language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editedApiKey,
                    onValueChange = { editedApiKey = it },
                    label = { Text(LocalizedText.OPENROUTER_API_KEY.resolve(language)) },
                    placeholder = { Text(LocalizedText.SERVER_KEY_PLACEHOLDER.resolve(language)) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) LocalizedText.HIDE.resolve(language) else LocalizedText.SHOW.resolve(language)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(LocalizedText.API_KEY_GUIDANCE.resolve(language), style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(expanded = modelMenuExpanded, onExpandedChange = {
                    modelMenuExpanded = it
                    if (it) modelSearch = ""
                }) {
                    OutlinedTextField(
                        value = if (modelMenuExpanded) modelSearch else model,
                        onValueChange = { modelSearch = it; model = it; modelMenuExpanded = true },
                        label = { Text(LocalizedText.PREFERRED_MODEL.resolve(language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        singleLine = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modelMenuExpanded && matchingModels.isNotEmpty(), onDismissRequest = { modelMenuExpanded = false }) {
                        matchingModels.forEach { option ->
                            DropdownMenuItem(text = { Text(if (OpenRouterClient.isUnstableFreeModel(option)) "$option ${LocalizedText.MODEL_MAY_BE_SLOW.resolve(language)}" else option) }, onClick = { model = option; modelSearch = ""; modelMenuExpanded = false })
                        }
                    }
                }
                Text(LocalizedText.MODEL_GUIDANCE.resolve(language), style = MaterialTheme.typography.bodySmall)
                if (!modelIsValid) Text(LocalizedText.MODEL_FORMAT_ERROR.resolve(language), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(editedApiKey.trim(), model.trim()) }, enabled = modelIsValid) { Text(LocalizedText.SAVE.resolve(language)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizedText.CANCEL.resolve(language)) } }
    )
}
