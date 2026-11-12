@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.snigtus.dost

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.io.File
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.snigtus.dost.ui.theme.DostTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val openFriendRequests = MutableStateFlow<OpenFriendRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) publishOpenFriend(intent)
        enableEdgeToEdge()
        setContent { DostTheme { DostApp(openFriendRequests) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishOpenFriend(intent)
    }

    override fun onStart() {
        super.onStart()
        stopService(Intent(this, FloatingChatService::class.java))
    }

    private fun publishOpenFriend(intent: Intent?) {
        val friendId = intent?.getStringExtra(ChatLaunch.EXTRA_FRIEND_ID)?.takeIf { it.isNotBlank() } ?: return
        openFriendRequests.value = OpenFriendRequest(friendId, System.nanoTime())
    }
}

private const val MAX_CUSTOM_FRIENDS = 3
private const val MAX_SYSTEM_FRIENDS = 7
private const val MAX_TOTAL_FRIENDS = MAX_CUSTOM_FRIENDS + MAX_SYSTEM_FRIENDS


@Composable
fun DostApp(openFriendRequests: StateFlow<OpenFriendRequest?> = MutableStateFlow(null)) {
    val context = LocalContext.current
    val store = remember(context) { DostStore(context) }
    ConversationManager.initialize(context)
    var language by remember { mutableStateOf(store.language()) }
    var screen by rememberSaveable(
        stateSaver = Saver<AppScreen, String>(
            save = { it.name },
            restore = { runCatching { AppScreen.valueOf(it) }.getOrDefault(AppScreen.SPLASH) }
        )
    ) { mutableStateOf(AppScreen.SPLASH) }
    var registerStep by remember { mutableStateOf(1) }
    var name by remember { mutableStateOf(store.userName()) }
    var email by remember { mutableStateOf(store.userEmail()) }
    var age by remember { mutableStateOf(store.userAge()) }
    var gender by remember { mutableStateOf(store.userGender()) }
    var userTimeZoneId by remember { mutableStateOf(store.userTimeZoneId()) }
    var openRouterApiKey by remember { mutableStateOf(store.openRouterApiKey()) }
    var aiModel by remember { mutableStateOf(store.aiModel()) }
    val friends = remember {
        store.clearLockedLoveScores()
        mutableStateListOf<Friend>().apply { addAll(store.loadFriends()) }
    }
    val selectedFriendSaver = remember(store) {
        Saver<Friend?, String>(
            save = { friend -> friend?.id ?: "" },
            restore = { id -> if (id.isBlank()) null else store.loadFriends().firstOrNull { it.id == id } }
        )
    }
    var selectedFriend by rememberSaveable(stateSaver = selectedFriendSaver) { mutableStateOf<Friend?>(null) }
    var closeAfterOverlayPermission by remember { mutableStateOf(false) }
    var bubbleFriendId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var generatingProfileIds by remember { mutableStateOf(setOf<String>()) }
    var profileErrors by remember { mutableStateOf(mapOf<String, ProfileFailure>()) }
    val openFriendRequest by openFriendRequests.collectAsState()

    fun openRequestedFriend(friendId: String?) {
        val resolved = ChatLaunch.friendToOpen(store.hasEligibleAccount(), friendId, friends.map { it.id }.toSet()) ?: return
        selectedFriend = friends.first { it.id == resolved }
        screen = AppScreen.CHAT
    }

    LaunchedEffect(openFriendRequest?.nonce) {
        if (screen == AppScreen.SPLASH) return@LaunchedEffect
        openRequestedFriend(openFriendRequest?.friendId)
    }

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val relationshipUpdates = ConversationManager.relationshipUpdates.collectAsState().value
    LaunchedEffect(relationshipUpdates) {
        if (relationshipUpdates.isEmpty()) return@LaunchedEffect
        val storedFriends = store.loadFriends()
        relationshipUpdates.keys.forEach { friendId ->
            val stored = storedFriends.firstOrNull { it.id == friendId } ?: return@forEach
            val index = friends.indexOfFirst { it.id == friendId }
            if (index >= 0) friends[index] = stored
            if (selectedFriend?.id == friendId) selectedFriend = stored
        }
    }
    LaunchedEffect(screen) {
        if (screen == AppScreen.HOME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ReplyNotifier.ensureChannel(context)
            if (!ReplyNotifier.canNotify(context)) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    

    fun startProfileGeneration(friendId: String) {
        val friend = friends.firstOrNull { it.id == friendId } ?: return
        if (!friend.profilePending || friendId in generatingProfileIds) return
        val requestLanguage = language
        profileErrors = profileErrors - friendId
        generatingProfileIds = generatingProfileIds + friendId
        scope.launch {
            try {
                val profile = OpenRouterClient().generateFriendProfile(
                    store.openRouterApiKey(), store.aiModel(), friend.name, friend.age, friend.gender, friend.personality, requestLanguage
                )
                val index = friends.indexOfFirst { it.id == friendId }
                if (index >= 0 && friends[index].profilePending) {
                    val updated = store.updateFriend(friendId) { current ->
                        current.withGeneratedProfile(profile)
                    } ?: return@launch
                    friends[index] = updated
                    if (selectedFriend?.id == friendId) selectedFriend = updated
                    ConversationWorkScheduler.scheduleMorningGreeting(context, updated, requestLanguage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                profileErrors = profileErrors + (friendId to profileFailureText(requestLanguage, error.message))
            } finally {
                generatingProfileIds = generatingProfileIds - friendId
            }
        }
    }

    fun continueWithBasicProfile(friendId: String) {
        val index = friends.indexOfFirst { it.id == friendId }
        if (index < 0 || !friends[index].profilePending || friendId in generatingProfileIds) return
        val updated = store.updateFriend(friendId) { current ->
            current.copy(profilePending = false)
        } ?: return
        friends[index] = updated
        if (selectedFriend?.id == friendId) selectedFriend = updated
        profileErrors = profileErrors - friendId
        ConversationWorkScheduler.scheduleMorningGreeting(context, updated, language)
    }

    fun addFriendWithProfile(friend: Friend) {
        val safeFriend = friend.withLoveMeterGated(store.userAge())
        if (friends.count { it.catalogId.isBlank() } >= MAX_CUSTOM_FRIENDS) return
        val pending = safeFriend.copy(profilePending = true)
        friends.add(pending)
        store.saveFriends(friends)
        startProfileGeneration(pending.id)
    }

    LaunchedEffect(Unit) {
        friends.filter { it.profilePending }.forEach { startProfileGeneration(it.id) }
    }

    fun addSystemFriend(friend: Friend) {
        if (friend.catalogId.isBlank()
            || friends.count { it.catalogId.isNotBlank() } >= MAX_SYSTEM_FRIENDS
            || friends.any { it.catalogId == friend.catalogId }
        ) return
        val addedFriend = friend.copy(id = java.util.UUID.randomUUID().toString()).withLoveMeterGated(store.userAge())
        friends.add(addedFriend)
        store.saveFriends(friends)
        ConversationWorkScheduler.scheduleMorningGreeting(context, addedFriend, language)
    }

    androidx.compose.animation.AnimatedContent(
        targetState = screen,
        label = "pageTransition",
        transitionSpec = {
            val order = listOf(AppScreen.SPLASH, AppScreen.REGISTER, AppScreen.HOME, AppScreen.CHAT, AppScreen.KNOWLEDGE)
            val forward = order.indexOf(targetState) >= order.indexOf(initialState)
            val slideIn = slideInHorizontally(animationSpec = tween(300)) { if (forward) it else -it }
            val slideOut = slideOutHorizontally(animationSpec = tween(300)) { if (forward) -it else it }
            slideIn.togetherWith(slideOut)
        }
    ) { currentScreen ->
    when (currentScreen) {
        AppScreen.SPLASH -> DostSplashScreen(language) {
            val requestedId = openFriendRequests.value?.friendId
            val resolved = ChatLaunch.friendToOpen(store.hasEligibleAccount(), requestedId, friends.map { it.id }.toSet())
            if (resolved != null) {
                selectedFriend = friends.first { it.id == resolved }
                screen = AppScreen.CHAT
            } else {
                screen = if (store.hasEligibleAccount()) AppScreen.HOME else AppScreen.REGISTER
            }
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
            onNext = {
                if (name.isNotBlank() && email.isNotBlank() && accountAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank()) {
                    registerStep = 2
                }
            },
            onFinish = finish@{ friend ->
                if (accountAgeResult(age) != AccountAgeResult.VALID) return@finish
                if (friend != null && friendAgeResult(friend.age) != AccountAgeResult.VALID) return@finish
                store.saveUserProfile(name, email, age.trim(), gender)
                friend?.let { addFriendWithProfile(it.copy(age = it.age.trim())) }
                screen = AppScreen.HOME
            },
            onBack = { if (registerStep == 2) registerStep = 1 else screen = AppScreen.REGISTER }
        )
        AppScreen.HOME -> HomeScreen(
            language = language,
            userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
            friends = friends,
            onAddFriend = { addFriendWithProfile(it) },
            onAddSystemFriend = { addSystemFriend(it) },
            onDeleteFriend = { friend ->
                friends.remove(friend)
                store.saveFriends(friends)
                store.deleteFriendData(friend.id)
                ConversationManager.removeFriend(friend.id)
                generatingProfileIds = generatingProfileIds - friend.id
                profileErrors = profileErrors - friend.id
            },
            onOpenChat = { selectedFriend = it; screen = AppScreen.CHAT },
            userTimeZoneId = userTimeZoneId,
            onUserTimeZoneChange = { userTimeZoneId = it; store.saveUserTimeZoneId(it) },
            openRouterApiKey = openRouterApiKey,
            aiModel = aiModel,
            onAiSettingsSave = { apiKey, model ->
                openRouterApiKey = apiKey
                aiModel = model
                store.saveOpenRouterApiKey(apiKey)
                store.saveAiModel(model)
            },
            onLanguageChange = { language = it; store.saveLanguage(it) },
            onKnowledge = { screen = AppScreen.KNOWLEDGE },
            generatingProfileIds = generatingProfileIds,
            profileFailedIds = profileErrors.keys
        )
        AppScreen.CHAT -> selectedFriend?.let { friend ->
            ChatScreen(
                friend = friend,
                store = store,
                onBack = { screen = AppScreen.HOME },
                language = language,
                profileGenerating = friend.id in generatingProfileIds,
                profileError = profileErrors[friend.id],
                onRetryProfile = { startProfileGeneration(friend.id) },
                onUseBasicProfile = { continueWithBasicProfile(friend.id) },
                onFloatingChat = { requestFloatingChat(friend.id, closeApp = true) },
                onFriendTimeZoneChange = { timeZoneId ->
                    val updated = store.updateFriend(friend.id) { current ->
                        current.copy(timeZoneId = validZoneId(timeZoneId).id)
                    }
                    if (updated != null) {
                        val index = friends.indexOfFirst { it.id == friend.id }
                        if (index >= 0) friends[index] = updated
                        selectedFriend = updated
                        ConversationManager.refreshBusyReply(updated)
                    }
                }
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
private fun HomeScreen(language: AppLanguage, userName: String, friends: List<Friend>, onAddFriend: (Friend) -> Unit, onAddSystemFriend: (Friend) -> Unit, onDeleteFriend: (Friend) -> Unit, onOpenChat: (Friend) -> Unit, userTimeZoneId: String, onUserTimeZoneChange: (String) -> Unit, openRouterApiKey: String, aiModel: String, onAiSettingsSave: (String, String) -> Unit, onLanguageChange: (AppLanguage) -> Unit, onKnowledge: () -> Unit, generatingProfileIds: Set<String>, profileFailedIds: Set<String>) {
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
                catalogError = error.message ?: "Could not load system friends."
            } catch (error: org.json.JSONException) {
                catalogError = "The system friend catalog returned invalid data."
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
                catalogError = error.message ?: "Could not add this system friend."
            } catch (error: org.json.JSONException) {
                catalogError = "This system friend profile is invalid."
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
                    TextButton(onClick = { if (canAddAnyFriend) showAddOptions = true else showSettings = true }) {
                        Text(when {
                            !canAddAnyFriend && bangla -> "বন্ধু পরিচালনা"
                            !canAddAnyFriend && hindi -> "दोस्त प्रबंधित करें"
                            !canAddAnyFriend -> "Manage friends"
                            bangla -> "নতুন চ্যাট"
                            hindi -> "नई चैट"
                            else -> "New chat"
                        })
                    }
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
        onManageFriends = { showSystemFriendPicker = false; showSettings = true },
        onDismiss = { showSystemFriendPicker = false }
    )
    if (showSettings) SettingsDialog(language, friends, userTimeZoneId, onDismiss = { showSettings = false }, onDeleteFriend = { onDeleteFriend(it) }, onLanguageChange = { onLanguageChange(it) }, onUserTimeZoneChange = onUserTimeZoneChange, onAiSettings = { showSettings = false; showAiSettings = true }, onKnowledge = { showSettings = false; onKnowledge() })
    if (showAiSettings) AiSettingsDialog(openRouterApiKey, aiModel, onDismiss = { showAiSettings = false }, onSave = { apiKey, model -> onAiSettingsSave(apiKey, model); showAiSettings = false })
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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
        else -> lastMessage?.content?.take(48) ?: "${friendshipLevel(friend.friendshipScore)} · ${friend.friendshipScore}/100"
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a friend") }, text = { Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") })
        OutlinedTextField(age, { age = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        friendAgeError(age, language)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        friendUnder18RomanceNote(age, language)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        GenderField(gender, onValueChange = { gender = it })
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
        TimeZonePicker("Friend timezone", friendTimeZoneId, onTimeZoneChange = { friendTimeZoneId = it })
        Text("Daily busy schedule", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isWorkSchedule) Button(onClick = { workSchedule = false; customStartHour = null; customDurationHours = null }) { Text("School") }
            else OutlinedButton(onClick = { workSchedule = false; customStartHour = null; customDurationHours = null }) { Text("School") }
            if (isWorkSchedule) Button(onClick = { workSchedule = true; customStartHour = null; customDurationHours = null }) { Text("Work") }
            else OutlinedButton(onClick = { workSchedule = true; customStartHour = null; customDurationHours = null }) { Text("Work") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                android.app.TimePickerDialog(context, { _, hour, _ -> customStartHour = hour }, busyStartHour, 0, false).show()
            }) { Text("Starts ${LocalTime.of(busyStartHour, 0).format(DateTimeFormatter.ofPattern("h:mm a"))}") }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { durationMenuExpanded = true }) { Text("${busyDurationHours}h") }
                DropdownMenu(expanded = durationMenuExpanded, onDismissRequest = { durationMenuExpanded = false }) {
                    (4..8).forEach { hours ->
                        DropdownMenuItem(text = { Text("$hours hours") }, onClick = { customDurationHours = hours; durationMenuExpanded = false })
                    }
                }
            }
            Text("until ${busyEndTime.format(DateTimeFormatter.ofPattern("h:mm a"))}")
        }
        Text("Busy times use ${friendTimeZoneId} local time. Replies resume 30 minutes after the busy window.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendPhoto(photoUri, name.ifBlank { "?" }, Modifier.size(56.dp).clip(CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (photoUri.isBlank()) "No photo selected" else "Photo selected")
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (photoUri.isBlank()) "Choose photo" else "Change photo") }
            }
            if (photoUri.isNotBlank()) TextButton(onClick = { photoUri = "" }) { Text("Remove") }
        }
    } }, confirmButton = { Button(onClick = { onAdd(Friend(id = java.util.UUID.randomUUID().toString(), name = name, age = age.trim(), gender = gender, personality = personality.trim(), photoUri = photoUri, busyStartHour = busyStartHour, busyDurationHours = busyDurationHours, busyReason = if (isWorkSchedule) "Work" else "School", timeZoneId = friendTimeZoneId, greeting = greeting.trim())) }, enabled = name.isNotBlank() && friendAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank() && personality.isNotBlank() && greeting.isNotBlank()) { Text("Add") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZonePicker(label: String, timeZoneId: String, onTimeZoneChange: (String) -> Unit) {
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
                TimeZonePicker("Your timezone", userTimeZoneId, onTimeZoneChange = onUserTimeZoneChange)
                HorizontalDivider()
                Button(onClick = onAiSettings, modifier = Modifier.fillMaxWidth()) { Text("AI provider and model") }
                Text("Chat messages and AI replies are saved in a private server archive.", style = MaterialTheme.typography.bodySmall)
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
private fun AiSettingsDialog(apiKey: String, initialModel: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var editedApiKey by remember { mutableStateOf(apiKey) }
    var model by remember { mutableStateOf(initialModel) }
    var modelSearch by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val matchingModels = OpenRouterClient.modelCatalog.filter { modelSearch.isBlank() || it.contains(modelSearch, ignoreCase = true) }
    val modelIsValid = model.matches(Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)?"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI provider and model") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editedApiKey,
                    onValueChange = { editedApiKey = it },
                    label = { Text("Your OpenRouter API key") },
                    placeholder = { Text("Leave blank to use the server key") },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) "Hide" else "Show") } },
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
                        label = { Text("Preferred OpenRouter model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        singleLine = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = modelMenuExpanded && matchingModels.isNotEmpty(), onDismissRequest = { modelMenuExpanded = false }) {
                        matchingModels.forEach { option ->
                            DropdownMenuItem(text = { Text(if (OpenRouterClient.isUnstableFreeModel(option)) "$option (may be slow)" else option) }, onClick = { model = option; modelSearch = ""; modelMenuExpanded = false })
                        }
                    }
                }
                Text("Choose a model from the list or type any OpenRouter model ID. Your choice is saved as the preferred model. Photos require a vision-capable model. Paid or custom models require your own key; the key is encrypted on this device and never written to logs.", style = MaterialTheme.typography.bodySmall)
                if (!modelIsValid) Text("Enter a model ID in provider/model format.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(editedApiKey.trim(), model.trim()) }, enabled = modelIsValid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun KnowledgeScreen(language: AppLanguage, friends: List<Friend>, store: DostStore, onBack: () -> Unit) {
    var revision by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Pair<String, UserMemory>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, UserMemory>?>(null) }
    val knowledge = remember(friends, revision) {
        friends.mapNotNull { friend ->
            val memories = store.loadUserMemories(friend.id).filter { it.fact.isNotBlank() }
            if (memories.isEmpty()) null else friend to memories
        }
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
                knowledge.forEach { (friend, memories) ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(friend.name, style = MaterialTheme.typography.titleLarge)
                            memories.forEach { memory ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(memoryCategoryLabel(memory.category, language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(memory.fact, style = MaterialTheme.typography.bodyLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { editing = friend.id to memory }) {
                                            Text(when { bangla -> "ঠিক করুন"; hindi -> "ठीक करें"; else -> "Correct" })
                                        }
                                        TextButton(onClick = { deleting = friend.id to memory }) {
                                            Text(when { bangla -> "মুছুন"; hindi -> "हटाएँ"; else -> "Delete" }, color = MaterialTheme.colorScheme.error)
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
    editing?.let { (friendId, memory) ->
        CorrectMemoryDialog(
            language = language,
            memory = memory,
            onDismiss = { editing = null },
            onSave = { updated ->
                store.updateUserMemory(friendId, memory, updated)
                editing = null
                revision += 1
            }
        )
    }
    deleting?.let { (friendId, memory) ->
        val friendName = friends.firstOrNull { it.id == friendId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(when { bangla -> "এই তথ্য মুছবেন?"; hindi -> "यह जानकारी हटाएँ?"; else -> "Delete this fact?" }) },
            text = { Text(when { bangla -> "$friendName আর এটা মনে রাখবে না।"; hindi -> "$friendName अब इसे याद नहीं रखेगा।"; else -> "$friendName will no longer remember this." }) },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteUserMemory(friendId, memory)
                    deleting = null
                    revision += 1
                }) { Text(when { bangla -> "মুছুন"; hindi -> "हटाएँ"; else -> "Delete" }, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" }) } }
        )
    }
}

@Composable
private fun CorrectMemoryDialog(language: AppLanguage, memory: UserMemory, onDismiss: () -> Unit, onSave: (UserMemory) -> Unit) {
    var category by remember(memory) { mutableStateOf(memory.category) }
    var fact by remember(memory) { mutableStateOf(memory.fact) }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { bangla -> "এই তথ্য ঠিক করুন"; hindi -> "इस जानकारी को ठीक करें"; else -> "Correct this fact" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    category,
                    { category = it },
                    label = { Text(when { bangla -> "বিভাগ"; hindi -> "श्रेणी"; else -> "Category" }) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    fact,
                    { fact = it },
                    label = { Text(when { bangla -> "তথ্য"; hindi -> "जानकारी"; else -> "Fact" }) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(UserMemory(category, fact)) }, enabled = fact.isNotBlank()) {
                Text(when { bangla -> "সংরক্ষণ"; hindi -> "सहेजें"; else -> "Save" })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" }) } }
    )
}

@Composable
private fun ChatScreen(friend: Friend, store: DostStore, onBack: () -> Unit, language: AppLanguage, onFloatingChat: () -> Unit, onFriendTimeZoneChange: (String) -> Unit, profileGenerating: Boolean, profileError: ProfileFailure?, onRetryProfile: () -> Unit, onUseBasicProfile: () -> Unit) {
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
        ConversationManager.seedInitialGreeting(friend)
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
                    "How you met" to friend.relationship, "First message" to friend.greeting,
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DostTheme { DostApp() }
}