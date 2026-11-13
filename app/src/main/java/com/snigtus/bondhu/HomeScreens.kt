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
internal fun HomeScreen(language: AppLanguage, userName: String, friends: List<Friend>, onAddFriend: (Friend) -> Unit, onAddSystemFriend: (Friend) -> Unit, onDeleteFriend: (Friend) -> Unit, onOpenChat: (Friend) -> Unit, userTimeZoneId: String, onUserTimeZoneChange: (String) -> Unit, openRouterApiKey: String, aiModel: String, onAiSettingsSave: (String, String) -> Unit, onLanguageChange: (AppLanguage) -> Unit, onKnowledge: () -> Unit, generatingProfileIds: Set<String>, profileFailedIds: Set<String>) {
    var showAddFriend by remember { mutableStateOf(false) }
    var showAddOptions by remember { mutableStateOf(false) }
    var showSystemFriendPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var systemFriendCatalog by remember { mutableStateOf(emptyList<SystemFriendListing>()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var addingCatalogId by remember { mutableStateOf<String?>(null) }
    val unreadCounts = ConversationManager.unread.collectAsState().value
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemFriendClient = remember { SystemFriendClient() }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val customFriendCount = friends.count { it.catalogId.isBlank() }
    val systemFriendCount = friends.size - customFriendCount
    val canAddCustomFriend = customFriendCount < MAX_CUSTOM_FRIENDS
    val canAddAnyFriend = canAddCustomFriend || systemFriendCount < MAX_SYSTEM_FRIENDS
    val addedCatalogIds = friends.mapNotNull { it.catalogId.takeIf(String::isNotBlank) }.toSet()

    fun loadSystemFriendCatalog() {
        catalogLoading = true
        catalogError = null
        scope.launch {
            try {
                systemFriendCatalog = systemFriendClient.getCatalog()
            } catch (error: java.io.IOException) {
                catalogError = LocalizedText.LOAD_FRIENDS_FAILED.resolve(language)
            } catch (error: org.json.JSONException) {
                catalogError = LocalizedText.INVALID_FRIEND_CATALOG.resolve(language)
            } finally {
                catalogLoading = false
            }
        }
    }

    fun addCatalogFriend(listing: SystemFriendListing) {
        if (addingCatalogId != null || listing.id in addedCatalogIds || systemFriendCount >= MAX_SYSTEM_FRIENDS) return
        addingCatalogId = listing.id
        catalogError = null
        scope.launch {
            try {
                val profile = systemFriendClient.getProfile(listing.id)
                val friendWithCachedPhoto = systemFriendClient.savePhotoForOfflineUse(context, profile)
                onAddSystemFriend(friendWithCachedPhoto)
                showSystemFriendPicker = false
            } catch (error: java.io.IOException) {
                catalogError = LocalizedText.ADD_SYSTEM_FRIEND_FAILED.resolve(language)
            } catch (error: org.json.JSONException) {
                catalogError = LocalizedText.INVALID_SYSTEM_FRIEND.resolve(language)
            } finally {
                addingCatalogId = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(when { bangla -> "চ্যাট"; hindi -> "चैट"; else -> "Chats" }, style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    TextButton(onClick = { showSettings = true }) { Text(when { bangla -> "সেটিংস"; hindi -> "सेटिंग्स"; else -> "Settings" }) }
                    TextButton(onClick = { showAddOptions = true }, enabled = canAddAnyFriend) { Text(when { bangla -> "নতুন চ্যাট"; hindi -> "नई चैट"; else -> "New chat" }) }
                }
            )
        },
        floatingActionButton = {
            if (canAddAnyFriend) {
                FloatingActionButton(onClick = { showAddOptions = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text("+") }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(when { bangla -> "বন্ধুরা"; hindi -> "दोस्त"; else -> "Friends" }, style = MaterialTheme.typography.titleMedium)
                        Text("${friends.size}/$MAX_TOTAL_FRIENDS", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        when {
                            bangla -> "নিজস্ব $customFriendCount/$MAX_CUSTOM_FRIENDS · সিস্টেম $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                            hindi -> "कस्टम $customFriendCount/$MAX_CUSTOM_FRIENDS · सिस्टम $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                            else -> "Custom $customFriendCount/$MAX_CUSTOM_FRIENDS · System $systemFriendCount/$MAX_SYSTEM_FRIENDS"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            if (friends.isEmpty()) item { EmptyFriendsState(onAdd = { showAddOptions = true }, language = language) }
            items(friends) { friend ->
                FriendRow(
                    friend,
                    unread = unreadCounts[friend.id] ?: 0,
                    onOpenChat = { onOpenChat(friend) },
                    onDelete = { onDeleteFriend(friend) },
                    language = language,
                    profileGenerating = friend.id in generatingProfileIds || friend.id !in profileFailedIds,
                    profileFailed = friend.id in profileFailedIds
                )
            }
        }
    }
    if (showAddFriend) AddFriendDialog(language, onDismiss = { showAddFriend = false }, onAdd = { onAddFriend(it); showAddFriend = false })
    if (showAddOptions) AddFriendOptionsDialog(
        language = language,
        customCount = customFriendCount,
        systemCount = systemFriendCount,
        onCreateCustom = { showAddOptions = false; showAddFriend = true },
        onBrowseSystem = {
            showAddOptions = false
            showSystemFriendPicker = true
            loadSystemFriendCatalog()
        },
        onDismiss = { showAddOptions = false }
    )
    if (showSystemFriendPicker) SystemFriendPickerDialog(
        language = language,
        catalog = systemFriendCatalog,
        isLoading = catalogLoading,
        error = catalogError,
        addedCatalogIds = addedCatalogIds,
        addingCatalogId = addingCatalogId,
        systemLimitReached = systemFriendCount >= MAX_SYSTEM_FRIENDS,
        onRefresh = ::loadSystemFriendCatalog,
        onSelect = ::addCatalogFriend,
        onDismiss = { showSystemFriendPicker = false }
    )
    if (showSettings) SettingsDialog(language, friends, userTimeZoneId, onDismiss = { showSettings = false }, onDeleteFriend = { onDeleteFriend(it) }, onLanguageChange = { onLanguageChange(it) }, onUserTimeZoneChange = onUserTimeZoneChange, onAiSettings = { showSettings = false; showAiSettings = true }, onKnowledge = { showSettings = false; onKnowledge() })
    if (showAiSettings) AiSettingsDialog(language, openRouterApiKey, aiModel, onDismiss = { showAiSettings = false }, onSave = { apiKey, model -> onAiSettingsSave(apiKey, model); showAiSettings = false })
}

@Composable
private fun AddFriendOptionsDialog(
    language: AppLanguage,
    customCount: Int,
    systemCount: Int,
    onCreateCustom: () -> Unit,
    onBrowseSystem: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (language) {
                AppLanguage.BANGLA -> "বন্ধু যোগ করুন"
                AppLanguage.HINDI -> "दोस्त जोड़ें"
                AppLanguage.ENGLISH -> "Add a friend"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when (language) {
                    AppLanguage.BANGLA -> "নিজস্ব $customCount/$MAX_CUSTOM_FRIENDS · সিস্টেম $systemCount/$MAX_SYSTEM_FRIENDS"
                    AppLanguage.HINDI -> "कस्टम $customCount/$MAX_CUSTOM_FRIENDS · सिस्टम $systemCount/$MAX_SYSTEM_FRIENDS"
                    AppLanguage.ENGLISH -> "Custom $customCount/$MAX_CUSTOM_FRIENDS · System $systemCount/$MAX_SYSTEM_FRIENDS"
                })
                Button(onClick = onCreateCustom, enabled = customCount < MAX_CUSTOM_FRIENDS, modifier = Modifier.fillMaxWidth()) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "নিজের বন্ধু তৈরি করুন"
                        AppLanguage.HINDI -> "अपना दोस्त बनाएं"
                        AppLanguage.ENGLISH -> "Create a custom friend"
                    })
                }
                OutlinedButton(onClick = onBrowseSystem, modifier = Modifier.fillMaxWidth()) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "সিস্টেম বন্ধু বেছে নিন"
                        AppLanguage.HINDI -> "सिस्टम दोस्त चुनें"
                        AppLanguage.ENGLISH -> "Choose a system friend"
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(LocalizedText.CLOSE.resolve(language)) } }
    )
}

@Composable
private fun SystemFriendPickerDialog(
    language: AppLanguage,
    catalog: List<SystemFriendListing>,
    isLoading: Boolean,
    error: String?,
    addedCatalogIds: Set<String>,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    onRefresh: () -> Unit,
    onSelect: (SystemFriendListing) -> Unit,
    onDismiss: () -> Unit
) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val notProvided = when {
        bangla -> "দেওয়া হয়নি"
        hindi -> "उपलब्ध नहीं"
        else -> "Not provided"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { bangla -> "সিস্টেম বন্ধু"; hindi -> "सिस्टम दोस्त"; else -> "System friends" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    isLoading && catalog.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(when { bangla -> "বন্ধুদের তালিকা আনা হচ্ছে..."; hindi -> "दोस्तों की सूची लोड हो रही है..."; else -> "Loading friends..." })
                    }
                    catalog.isEmpty() && error == null -> Text(when { bangla -> "এখনও কোনো সিস্টেম বন্ধু নেই।"; hindi -> "अभी कोई सिस्टम दोस्त उपलब्ध नहीं है।"; else -> "No system friends are available yet." })
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 540.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(catalog, key = { it.id }) { listing ->
                            val alreadyAdded = listing.id in addedCatalogIds
                            val scheduleStart = LocalTime.of(listing.busyStartHour.coerceIn(0, 23), 0)
                            val scheduleEnd = scheduleStart.plusHours(listing.busyDurationHours.coerceIn(4, 8).toLong())
                            val details = listOf(
                                (when { bangla -> "ব্যক্তিত্ব"; hindi -> "व्यक्तित्व"; else -> "Personality" }) to listing.personality,
                                (when { bangla -> "আগ্রহ"; hindi -> "रुचियाँ"; else -> "Interests" }) to listing.interests,
                                (when { bangla -> "স্মৃতি"; hindi -> "यादें"; else -> "Memories" }) to listing.memories,
                                (when { bangla -> "কথোপকথনের ধরন"; hindi -> "बातचीत की शैली"; else -> "Conversation style" }) to listing.conversationStyle,
                                (when { bangla -> "পরিবার"; hindi -> "परिवार"; else -> "Family" }) to listing.family,
                                (when { bangla -> "পরিবারের সদস্য"; hindi -> "परिवार के सदस्य"; else -> "Family members" }) to listing.familyMembers,
                                (when { bangla -> "পারিবারিক কাজকর্ম"; hindi -> "पारिवारिक गतिविधियाँ"; else -> "Family activities" }) to listing.familyActivities,
                                (when { bangla -> "আর্থিক অবস্থা"; hindi -> "आर्थिक स्थिति"; else -> "Financial condition" }) to listing.financialCondition,
                                (when { bangla -> "ঠিকানা"; hindi -> "पता"; else -> "Address" }) to listing.address,
                                (when { bangla -> "উচ্চতা"; hindi -> "कद"; else -> "Height" }) to listing.height,
                                (when { bangla -> "ওজন"; hindi -> "वज़न"; else -> "Weight" }) to listing.weight,
                                (when { bangla -> "মুখের বৈশিষ্ট্য"; hindi -> "चेहरे की विशेषताएँ"; else -> "Facial features" }) to listing.facialFeatures,
                                (when { bangla -> "শারীরিক গঠন"; hindi -> "शारीरिक बनावट"; else -> "Body features" }) to listing.bodyFeatures,
                                (when { bangla -> "ব্যস্ততার কারণ"; hindi -> "व्यस्त रहने का कारण"; else -> "Busy with" }) to listing.busyReason,
                                (when { bangla -> "দৈনিক সময়সূচি"; hindi -> "दैनिक समय-सारणी"; else -> "Daily schedule" }) to "${scheduleStart.format(DateTimeFormatter.ofPattern("h:mm a"))}–${scheduleEnd.format(DateTimeFormatter.ofPattern("h:mm a"))} (${listing.timeZoneId})",
                                (when { bangla -> "সময় অঞ্চল"; hindi -> "समय क्षेत्र"; else -> "Time zone" }) to listing.timeZoneId
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FriendPhoto(
                                        listing.photoUrl,
                                        listing.name,
                                        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(listing.name, style = MaterialTheme.typography.headlineSmall)
                                        Text(
                                            "${listing.age} · ${listing.gender}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    HorizontalDivider()
                                    details.forEach { (label, value) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(value.ifBlank { notProvided }, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Button(
                                        onClick = { onSelect(listing) },
                                        enabled = !alreadyAdded && !systemLimitReached && addingCatalogId == null,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Text(when {
                                            addingCatalogId == listing.id -> if (bangla) "যোগ হচ্ছে..." else if (hindi) "जोड़ा जा रहा है..." else "Adding..."
                                            alreadyAdded -> if (bangla) "যোগ হয়েছে" else if (hindi) "जोड़ा गया" else "Added"
                                            bangla -> "এই বন্ধুকে যোগ করুন"
                                            hindi -> "इस दोस्त को जोड़ें"
                                            else -> "Add this friend"
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (systemLimitReached) {
                    Text(when { bangla -> "সর্বোচ্চ ৭টি সিস্টেম বন্ধু যোগ করা যাবে।"; hindi -> "अधिकतम 7 सिस्टम दोस्त जोड़े जा सकते हैं।"; else -> "You can add up to 7 system friends." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (error != null && catalog.isEmpty()) {
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        Text(when { bangla -> "আবার চেষ্টা করুন"; hindi -> "फिर कोशिश करें"; else -> "Retry" })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(when { bangla -> "বন্ধ করুন"; hindi -> "बंद करें"; else -> "Close" }) } }
    )
}

@Composable
private fun EmptyFriendsState(onAdd: () -> Unit, language: AppLanguage) {
    val hindi = language == AppLanguage.HINDI
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DostBrand()
        Text(when { language == AppLanguage.BANGLA -> "এখনও কোনো বন্ধু নেই"; hindi -> "अभी कोई दोस्त नहीं"; else -> "No friends yet" }, style = MaterialTheme.typography.titleLarge)
        Text(when { language == AppLanguage.BANGLA -> "একজন AI বন্ধু যোগ করুন এবং কথা বলা শুরু করুন।"; hindi -> "एक AI दोस्त जोड़ें और बात करना शुरू करें।"; else -> "Add an AI friend and start a conversation." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAdd) { Text(when { language == AppLanguage.BANGLA -> "বন্ধু যোগ করুন"; hindi -> "दोस्त जोड़ें"; else -> "Add a friend" }) }
    }
}

@Composable
private fun FriendRow(friend: Friend, unread: Int, onOpenChat: () -> Unit, onDelete: () -> Unit, language: AppLanguage, profileGenerating: Boolean, profileFailed: Boolean) {
    var confirmDelete by remember { mutableStateOf(false) }
    val lastMessage = ConversationManager.messages.collectAsState().value[friend.id]?.lastOrNull()
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
        else -> lastMessage?.content?.take(48) ?: "${friendshipLevel(friend.friendshipScore, language)} · ${friend.friendshipScore}/100"
    }
    androidx.compose.foundation.layout.Box {
        Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpenChat, onLongClick = { confirmDelete = true }).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(friend.photoUri, friend.name, Modifier.size(46.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.name + if (friend.mood.isNotBlank()) " ${friend.mood}" else "", style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    color = if (friend.profilePending && profileFailed && !profileGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.size(width = 56.dp, height = 6.dp))
            if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
        }
        DropdownMenu(expanded = confirmDelete, onDismissRequest = { confirmDelete = false }) {
            DropdownMenuItem(text = { Text(when { language == AppLanguage.BANGLA -> "মুছুন"; language == AppLanguage.HINDI -> "हटाएं"; else -> "Delete" }, color = MaterialTheme.colorScheme.error) }, onClick = { confirmDelete = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendDialog(language: AppLanguage, onDismiss: () -> Unit, onAdd: (Friend) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
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
    } }, confirmButton = { Button(onClick = { onAdd(Friend(id = java.util.UUID.randomUUID().toString(), name = name, age = age.trim(), gender = gender, photoUri = photoUri, busyStartHour = busyStartHour, busyDurationHours = busyDurationHours, busyReason = if (isWorkSchedule) "Work" else "School", timeZoneId = friendTimeZoneId)) }, enabled = name.isNotBlank() && friendAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank()) { Text(LocalizedText.ADD.resolve(language)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizedText.CANCEL.resolve(language)) } })
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
private fun SettingsDialog(language: AppLanguage, friends: List<Friend>, userTimeZoneId: String, onDismiss: () -> Unit, onDeleteFriend: (Friend) -> Unit, onLanguageChange: (AppLanguage) -> Unit, onUserTimeZoneChange: (String) -> Unit, onAiSettings: () -> Unit, onKnowledge: () -> Unit) {
    val hindi = language == AppLanguage.HINDI
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { language == AppLanguage.BANGLA -> "সেটিংস"; hindi -> "सेटिंग्स"; else -> "Settings" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when { language == AppLanguage.BANGLA -> "অ্যাপের ভাষা"; hindi -> "ऐप की भाषा"; else -> "App language" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLanguageChange(AppLanguage.ENGLISH) }, enabled = language != AppLanguage.ENGLISH) { Text("English") }
                    Button(onClick = { onLanguageChange(AppLanguage.BANGLA) }, enabled = language != AppLanguage.BANGLA) { Text("বাংলা") }
                    Button(onClick = { onLanguageChange(AppLanguage.HINDI) }, enabled = language != AppLanguage.HINDI) { Text("हिन्दी") }
                }
                HorizontalDivider()
                TimeZonePicker(LocalizedText.YOUR_TIMEZONE.resolve(language), userTimeZoneId, onTimeZoneChange = onUserTimeZoneChange)
                HorizontalDivider()
                Button(onClick = onAiSettings, modifier = Modifier.fillMaxWidth()) { Text(LocalizedText.AI_PROVIDER_MODEL.resolve(language)) }
                Text(LocalizedText.CHAT_ARCHIVE_NOTICE.resolve(language), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Button(onClick = onKnowledge, modifier = Modifier.fillMaxWidth()) {
                    Text(when { language == AppLanguage.BANGLA -> "আমার সম্পর্কে জানা তথ্য"; hindi -> "मेरे बारे में जानकारी"; else -> "Knowledge about me" })
                }
                HorizontalDivider()
                Text(when { language == AppLanguage.BANGLA -> "বন্ধু মুছুন"; hindi -> "दोस्त हटाएं"; else -> "Delete a friend" }, style = MaterialTheme.typography.titleMedium)
                friends.forEach { friend ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.name)
                        TextButton(onClick = { onDeleteFriend(friend) }) { Text(when { language == AppLanguage.BANGLA -> "মুছুন"; hindi -> "हटाएं"; else -> "Delete" }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(when { language == AppLanguage.BANGLA -> "বন্ধ করুন"; hindi -> "बंद करें"; else -> "Close" }) } }
    )
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
