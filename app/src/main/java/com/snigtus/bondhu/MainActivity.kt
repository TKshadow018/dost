@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.snigtus.dost

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.snigtus.dost.ui.theme.DostTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DostTheme { DostApp() } }
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, FloatingChatService::class.java))
    }
}


@Composable
fun DostApp() {
    val context = LocalContext.current
    val store = remember(context) { DostStore(context) }
    ConversationManager.initialize(context)
    var language by remember { mutableStateOf(store.language()) }
    var screen by remember { mutableStateOf(AppScreen.SPLASH) }
    var registerStep by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    val friends = remember { mutableStateListOf<Friend>().apply { addAll(store.loadFriends()) } }
    var selectedFriend by remember { mutableStateOf<Friend?>(null) }
    var closeAfterOverlayPermission by remember { mutableStateOf(false) }
    var bubbleFriendId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun minimizeApp() {
        (context as? Activity)?.moveTaskToBack(true)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, FloatingChatService::class.java)
                .putExtra(FloatingChatService.EXTRA_FRIEND_ID, bubbleFriendId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            if (closeAfterOverlayPermission) minimizeApp()
        }
        closeAfterOverlayPermission = false
    }

    fun requestFloatingChat(friendId: String? = null, closeApp: Boolean = false) {
        bubbleFriendId = friendId ?: selectedFriend?.id ?: friends.firstOrNull()?.id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            closeAfterOverlayPermission = closeApp
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } else {
            val serviceIntent = Intent(context, FloatingChatService::class.java)
                .putExtra(FloatingChatService.EXTRA_FRIEND_ID, bubbleFriendId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            if (closeApp) minimizeApp()
        }
    }

    

    fun addFriendWithProfile(friend: Friend) {
        if (friends.size >= 5) return
        friends.add(friend)
        store.saveFriends(friends)
        scope.launch {
            runCatching { OpenRouterClient().generateFriendProfile(store.apiKey(), friend.name, friend.age, friend.gender, language) }
                .onSuccess { profile ->
                    val index = friends.indexOfFirst { it.id == friend.id }
                    if (index >= 0) {
                        friends[index] = friend.copy(
                            personality = profile.personality, interests = profile.interests, family = profile.family,
                            familyMembers = profile.familyMembers, familyActivities = profile.familyActivities,
                            financialCondition = profile.financialCondition, address = profile.address, height = profile.height,
                            weight = profile.weight, facialFeatures = profile.facialFeatures, bodyFeatures = profile.bodyFeatures,
                            conversationStyle = profile.conversationStyle
                        )
                        store.saveFriends(friends)
                    }
                }
        }
    }

    fun updateRelationship(friendId: String, friendshipScore: Int, loveScore: Int) {
        val index = friends.indexOfFirst { it.id == friendId }
        if (index < 0) return
        val current = friends[index]
        val friendship = friendshipScore.coerceIn(0, 100)
        val love = if (friendship > 70) loveScore.coerceIn(0, 100) else 0
        val updated = current.copy(friendshipScore = friendship, loveScore = love)
        friends[index] = updated
        selectedFriend = updated
        store.saveFriends(friends)
    }

    Crossfade(targetState = screen, label = "pageTransition") { currentScreen ->
    when (currentScreen) {
        AppScreen.SPLASH -> DostSplashScreen {
            screen = if (store.hasUserProfile()) AppScreen.HOME else AppScreen.REGISTER
        }
        AppScreen.REGISTER -> RegisterScreen(
            language = language,
            step = registerStep,
            name = name,
            email = email,
            age = age,
            gender = gender,
            onNameChange = { name = it },
            onEmailChange = { email = it },
            onAgeChange = { age = it },
            onGenderChange = { gender = it },
            onNext = { registerStep = 2 },
            onFinish = { friend ->
                store.saveUserProfile(name, email, age, gender)
                friend?.let { addFriendWithProfile(it) }
                screen = AppScreen.HOME
            },
            onBack = { if (registerStep == 2) registerStep = 1 else screen = AppScreen.REGISTER }
        )
        AppScreen.HOME -> HomeScreen(
            language = language,
            userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
            friends = friends,
            onAddFriend = { addFriendWithProfile(it) },
            onDeleteFriend = { friends.remove(it); store.saveFriends(friends) },
            onOpenChat = { selectedFriend = it; screen = AppScreen.CHAT },
            hasApiKey = store.apiKey().isNotBlank(),
            onSaveApiKey = { store.saveApiKey(it) },
            onLanguageChange = { language = it; store.saveLanguage(it) },
            onKnowledge = { screen = AppScreen.KNOWLEDGE }
        )
        AppScreen.CHAT -> selectedFriend?.let { friend ->
            ChatScreen(
                friend = friend,
                store = store,
                onBack = { screen = AppScreen.HOME },
                language = language,
                onFloatingChat = { requestFloatingChat(friend.id, closeApp = true) },
                onRelationshipUpdate = { friendship, love -> updateRelationship(friend.id, friendship, love) }
            )
        }
        AppScreen.KNOWLEDGE -> KnowledgeScreen(
            language = language,
            friends = friends,
            store = store,
            onBack = { screen = AppScreen.HOME }
        )
    }
    }
}

@Composable
private fun HomeScreen(language: AppLanguage, userName: String, friends: List<Friend>, onAddFriend: (Friend) -> Unit, onDeleteFriend: (Friend) -> Unit, onOpenChat: (Friend) -> Unit, hasApiKey: Boolean, onSaveApiKey: (String) -> Unit, onLanguageChange: (AppLanguage) -> Unit, onKnowledge: () -> Unit) {
    var showAddFriend by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyIsSet by remember(hasApiKey) { mutableStateOf(hasApiKey) }
    val unreadCounts = ConversationManager.unread.collectAsState().value
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Scaffold(topBar = { TopAppBar(title = { Text(when { bangla -> "চ্যাট"; hindi -> "चैट"; else -> "Chats" }, style = MaterialTheme.typography.headlineSmall) }, actions = { TextButton(onClick = { showSettings = true }) { Text(when { bangla -> "সেটিংস"; hindi -> "सेटिंग्स"; else -> "Settings" }) }; TextButton(onClick = { if (friends.size < 5) showAddFriend = true }) { Text(when { bangla -> "নতুন চ্যাট"; hindi -> "नई चैट"; else -> "New chat" }) } }) }, floatingActionButton = { FloatingActionButton(onClick = { if (friends.size < 5) showAddFriend = true }) { Text("+") } }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(when { bangla -> "বন্ধুরা"; hindi -> "दोस्त"; else -> "Friends" }, style = MaterialTheme.typography.titleMedium); Text("${friends.size}/5", style = MaterialTheme.typography.labelLarge) }
                }
            }
            item { HorizontalDivider() }
            if (friends.isEmpty()) item { EmptyFriendsState(onAdd = { showAddFriend = true }, language = language) }
            items(friends) { friend -> FriendRow(friend, unread = unreadCounts[friend.id] ?: 0, onOpenChat = { onOpenChat(friend) }, language = language) }
        }
    }
    if (showAddFriend) AddFriendDialog(onDismiss = { showAddFriend = false }, onAdd = { onAddFriend(it); showAddFriend = false })
    if (showSettings) SettingsDialog(language, friends, apiKeyIsSet, onDismiss = { showSettings = false }, onDeleteFriend = { onDeleteFriend(it) }, onLanguageChange = { onLanguageChange(it) }, onKnowledge = { showSettings = false; onKnowledge() }, onApiKey = { showSettings = false; showApiKey = true })
    if (showApiKey) ApiKeyDialog(onDismiss = { showApiKey = false }, onSave = { onSaveApiKey(it); apiKeyIsSet = true; showApiKey = false })
}

@Composable
private fun EmptyFriendsState(onAdd: () -> Unit, language: AppLanguage) {
    val hindi = language == AppLanguage.HINDI
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(when { language == AppLanguage.BANGLA -> "এখনও কোনো মেসেজ নেই"; hindi -> "अभी कोई संदेश नहीं है"; else -> "No messages yet" }, style = MaterialTheme.typography.titleLarge)
        Text(when { language == AppLanguage.BANGLA -> "চ্যাট তালিকা শুরু করতে একজন বন্ধু যোগ করুন।"; hindi -> "अपनी चैट सूची शुरू करने के लिए एक दोस्त जोड़ें।"; else -> "Add a friend to start your chat list." })
        Button(onClick = onAdd) { Text(when { language == AppLanguage.BANGLA -> "বন্ধু যোগ করুন"; hindi -> "दोस्त जोड़ें"; else -> "Add a friend" }) }
    }
}

@Composable
private fun FriendRow(friend: Friend, unread: Int, onOpenChat: () -> Unit, language: AppLanguage) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChat).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(friend.photoUri, friend.name, Modifier.size(46.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.name, style = MaterialTheme.typography.titleMedium)
                Text("${friendshipLevel(friend.friendshipScore)} · ${friend.friendshipScore}/100", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.size(width = 64.dp, height = 6.dp))
            if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onAdd: (Friend) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }
    var workSchedule by remember { mutableStateOf<Boolean?>(null) }
    var customStartHour by remember { mutableStateOf<Int?>(null) }
    var customDurationHours by remember { mutableStateOf<Int?>(null) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a friend") }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") })
        OutlinedTextField(age, { age = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        GenderField(gender, onValueChange = { gender = it })
        Text("Daily busy schedule", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { workSchedule = false; customStartHour = null; customDurationHours = null }) { Text("School") }
            OutlinedButton(onClick = { workSchedule = true; customStartHour = null; customDurationHours = null }) { Text("Work") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                android.app.TimePickerDialog(context, { _, hour, _ -> customStartHour = hour }, busyStartHour, 0, true).show()
            }) { Text("Starts ${"%02d:00".format(busyStartHour)}") }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { durationMenuExpanded = true }) { Text("${busyDurationHours}h") }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    (4..8).forEach { hours ->
                        DropdownMenuItem(text = { Text("$hours hours") }, onClick = { customDurationHours = hours; durationMenuExpanded = false })
                    }
                }
            }
            Text("until ${"%02d:%02d".format(busyEndTime.hour, busyEndTime.minute)}")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(photoUri, name.ifBlank { "?" }, Modifier.size(56.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (photoUri.isBlank()) "No photo selected" else "Photo selected")
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (photoUri.isBlank()) "Choose photo" else "Change photo") }
            }
            if (photoUri.isNotBlank()) TextButton(onClick = { photoUri = "" }) { Text("Remove") }
        }
    } }, confirmButton = { Button(onClick = { onAdd(Friend(id = java.util.UUID.randomUUID().toString(), name = name, age = age, gender = gender, photoUri = photoUri, busyStartHour = busyStartHour, busyDurationHours = busyDurationHours)) }, enabled = name.isNotBlank() && age.isNotBlank() && gender.isNotBlank()) { Text("Add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SettingsDialog(language: AppLanguage, friends: List<Friend>, hasApiKey: Boolean, onDismiss: () -> Unit, onDeleteFriend: (Friend) -> Unit, onLanguageChange: (AppLanguage) -> Unit, onKnowledge: () -> Unit, onApiKey: () -> Unit) {
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
                Button(onClick = onApiKey, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hasApiKey) "Update OpenRouter API key" else "Set OpenRouter API key")
                }
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
private fun KnowledgeScreen(language: AppLanguage, friends: List<Friend>, store: DostStore, onBack: () -> Unit) {
    val knowledge = friends.mapNotNull { friend ->
        val memories = store.loadUserMemories(friend.id)
            .filter { it.fact.isNotBlank() }
        if (memories.isEmpty()) null else friend to memories
    }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when { bangla -> "কে আমার সম্পর্কে কী জানে"; hindi -> "मेरे बारे में कौन क्या जानता है"; else -> "Who knows what about me" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text(when { bangla -> "ফিরুন"; hindi -> "वापस"; else -> "Back" }) } }
            )
        }
    ) { padding ->
        if (knowledge.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(when { bangla -> "এখনও কোনো তথ্য শেখা হয়নি"; hindi -> "अभी तक कोई व्यक्तिगत जानकारी नहीं सीखी गई"; else -> "No personal information learned yet" })
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                knowledge.forEach { (friend, categories) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(friend.name, style = MaterialTheme.typography.titleLarge)
                            categories.forEach { memory ->
                                Text("• ${memory.fact}", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("OpenRouter API key") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("The key is stored only on this device and is sent directly to OpenRouter."); OutlinedTextField(key, { key = it }, label = { Text("API key") }, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ChatScreen(friend: Friend, store: DostStore, onBack: () -> Unit, language: AppLanguage, onFloatingChat: () -> Unit, onRelationshipUpdate: (Int, Int) -> Unit) {
    val messageMap = ConversationManager.messages.collectAsState().value
    val messages = messageMap[friend.id] ?: store.loadMessages(friend.id)
    var draft by remember { mutableStateOf("") }
    val sendingIds = ConversationManager.sending.collectAsState().value
    val sending = friend.id in sendingIds
    val scheduledWaitingIds = ConversationManager.waitingForSchedule.collectAsState().value
    val waitingForSchedule = friend.id in scheduledWaitingIds
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    var currentTime by remember { mutableStateOf(ZonedDateTime.now()) }
    var showFriendInfo by remember { mutableStateOf(false) }
    var showUserInfo by remember { mutableStateOf(false) }
    var showSessionSummaries by remember { mutableStateOf(false) }
    var sessionSummaries by remember(friend.id) { mutableStateOf(store.loadSessionSummaries(friend.id).takeLast(10).reversed()) }
    val busyUntil = friend.busyUntil(currentTime)
    val dailyBusyStart = LocalTime.of(friend.busyStartHour.coerceIn(0, 23), 0)
    val dailyBusyEnd = dailyBusyStart.plusHours(friend.busyDurationHours.coerceIn(4, 8).toLong())
    LaunchedEffect(friend.id) {
        while (true) {
            delay(60_000)
            currentTime = ZonedDateTime.now()
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
    Scaffold(topBar = { TopAppBar(title = { Row(modifier = Modifier.clickable { showFriendInfo = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { FriendPhoto(friend.photoUri, friend.name, Modifier.size(40.dp).clip(CircleShape)); Column { Text(friend.name); Text("${friendshipLevel(friend.friendshipScore)} · ${friend.friendshipScore}/100", style = MaterialTheme.typography.labelSmall); Text(if (busyUntil != null) "Busy until ${busyUntil.format(DateTimeFormatter.ofPattern("HH:mm"))}" else "Daily busy ${"%02d:%02d".format(dailyBusyStart.hour, dailyBusyStart.minute)}–${"%02d:%02d".format(dailyBusyEnd.hour, dailyBusyEnd.minute)}", style = MaterialTheme.typography.labelSmall) } } }, navigationIcon = { TextButton(onClick = onBack) { Text(when { bangla -> "ফিরুন"; hindi -> "वापस"; else -> "Back" }) } }, actions = { TextButton(onClick = { showUserInfo = true }) { Text(when { bangla -> "আমি"; hindi -> "मैं"; else -> "Me" }) }; TextButton(onClick = { sessionSummaries = store.loadSessionSummaries(friend.id).takeLast(10).reversed(); showSessionSummaries = true }) { Text(when { bangla -> "সারাংশ"; hindi -> "सारांश"; else -> "Sessions" }) }; TextButton(onClick = onFloatingChat) { Text(when { bangla -> "মিনিমাইজ"; hindi -> "छोटा करें"; else -> "Minimize" }) } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            RelationshipMeters(friend = friend, language = language)
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
                if (messages.isEmpty()) item { Text(when { bangla -> "${friend.name}-এর সাথে কথা বলা শুরু করুন।"; hindi -> "${friend.name} के साथ बातचीत शुरू करें।"; else -> "Start a conversation with ${friend.name}." }, modifier = Modifier.padding(12.dp)) }
                items(messages) { message ->
                    MessageBubble(message = message, friendName = friend.name)
                }
                if (sending && waitingForSchedule) item { Text("${friend.name} will reply after the busy schedule.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                else if (sending) item { ThinkingBubble(friendName = friend.name, bangla = bangla, hindi = hindi) }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp)) } }
            }
            Surface(shadowElevation = 4.dp, tonalElevation = 2.dp, modifier = Modifier.imePadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text(when { bangla -> "একটি মেসেজ লিখুন..."; hindi -> "संदेश लिखें..."; else -> "Write a message..." }) }, maxLines = 4)
                    Button(enabled = draft.isNotBlank() && (!sending || waitingForSchedule), onClick = {
                    val text = draft.trim(); draft = ""; error = null
                    ConversationManager.send(friend, text, language)
                    }) { Text(when { bangla -> "পাঠান"; hindi -> "भेजें"; else -> "Send" }) }
                }
            }
        }
    }
    if (showFriendInfo) FriendInfoDialog(friend, onDismiss = { showFriendInfo = false })
    if (showUserInfo) UserInfoDialog(store, onDismiss = { showUserInfo = false })
    if (showSessionSummaries) SessionSummariesDialog(sessionSummaries, language, onDismiss = { showSessionSummaries = false })
}

@Composable
private fun FriendInfoDialog(friend: Friend, onDismiss: () -> Unit) {
    val start = LocalTime.of(friend.busyStartHour.coerceIn(0, 23), 0)
    val end = start.plusHours(friend.busyDurationHours.coerceIn(4, 8).toLong())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${friend.name}'s profile") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Age" to friend.age, "Gender" to friend.gender, "Personality" to friend.personality,
                    "Interests" to friend.interests, "Memories" to friend.memories, "Conversation style" to friend.conversationStyle,
                    "Family" to friend.family, "Family members" to friend.familyMembers, "Family activities" to friend.familyActivities,
                    "Financial condition" to friend.financialCondition, "Address" to friend.address, "Height" to friend.height,
                    "Weight" to friend.weight, "Facial features" to friend.facialFeatures, "Body features" to friend.bodyFeatures,
                    "Daily busy hours" to "%02d:%02d–%02d:%02d".format(start.hour, start.minute, end.hour, end.minute),
                    "Friendship" to "${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)",
                    "Love" to if (friend.friendshipScore > 70) "${loveLevel(friend.loveScore)} (${friend.loveScore}/100)" else "Locked"
                ).forEach { (label, value) ->
                    if (value.isNotBlank()) Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun UserInfoDialog(store: DostStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Name" to store.userName(), "Email" to store.userEmail(), "Age" to store.userAge(), "Gender" to store.userGender())
                    .forEach { (label, value) -> Text("$label: ${value.ifBlank { "Not provided" }}") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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
private fun MessageBubble(message: ChatMessage, friendName: String) {
    val isUser = message.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.84f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isUser) "You" else friendName, style = MaterialTheme.typography.labelSmall)
                Text(message.content, style = MaterialTheme.typography.bodyLarge)
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
private fun RelationshipMeters(friend: Friend, language: AppLanguage) {
    val loveUnlocked = friend.friendshipScore > 70
    val hindi = language == AppLanguage.HINDI
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when { language == AppLanguage.BANGLA -> "বন্ধুত্ব: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)"; hindi -> "दोस्ती: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)"; else -> "Friendship: ${friendshipLevel(friend.friendshipScore)} (${friend.friendshipScore}/100)" }, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { friend.friendshipScore / 100f }, modifier = Modifier.fillMaxWidth())
        Text(if (loveUnlocked) "${if (hindi) "प्यार" else "Love"}: ${loveLevel(friend.loveScore)} (${friend.loveScore}/100)" else when { language == AppLanguage.BANGLA -> "ভালোবাসা: বন্ধ (বন্ধুত্ব ৭১-এর বেশি হলে খুলবে)"; hindi -> "प्यार: बंद (दोस्ती 70 से ऊपर होने पर खुलेगा)"; else -> "Love: locked (unlocks above 70 friendship)" }, style = MaterialTheme.typography.labelMedium)
        if (loveUnlocked) LinearProgressIndicator(progress = { friend.loveScore / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DostTheme { DostApp() }
}