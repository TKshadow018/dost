@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.snigtus.dost

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openFriendRequests = MutableStateFlow<OpenFriendRequest?>(null)
    private lateinit var appViewModel: DostAppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) publishOpenFriend(intent)
        enableEdgeToEdge()
        appViewModel = ViewModelProvider(this)[DostAppViewModel::class.java]
        savedInstanceState?.let {
            appViewModel.restoreNavigationState(it.getString(KEY_SCREEN), it.getString(KEY_SELECTED_FRIEND_ID))
        }
        setContent { DostTheme { DostApp(openFriendRequests, appViewModel) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SCREEN, appViewModel.screen.value.name)
        outState.putString(KEY_SELECTED_FRIEND_ID, appViewModel.selectedFriend.value?.id)
        super.onSaveInstanceState(outState)
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

private const val KEY_SCREEN = "screen"
private const val KEY_SELECTED_FRIEND_ID = "selected_friend_id"


@Composable
fun DostApp(
    openFriendRequests: StateFlow<OpenFriendRequest?> = MutableStateFlow(null),
    viewModel: DostAppViewModel? = null
) {
    val context = LocalContext.current
    val appViewModel = viewModel ?: remember(context) {
        DostAppViewModel(context.applicationContext as Application)
    }
    val store = appViewModel.store
    var language by appViewModel.language
    var screen by appViewModel.screen
    var registerStep by appViewModel.registerStep
    var name by appViewModel.name
    var email by appViewModel.email
    var age by appViewModel.age
    var gender by appViewModel.gender
    var userTimeZoneId by appViewModel.userTimeZoneId
    var openRouterApiKey by appViewModel.openRouterApiKey
    var aiModel by appViewModel.aiModel
    val friends = appViewModel.friends
    var selectedFriend by appViewModel.selectedFriend
    var closeAfterOverlayPermission by appViewModel.closeAfterOverlayPermission
    var bubbleFriendId by appViewModel.bubbleFriendId
    val scope = rememberCoroutineScope()
    var generatingProfileIds by appViewModel.generatingProfileIds
    var profileErrors by appViewModel.profileErrors
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
                    store.openRouterApiKey(), store.aiModel(), friend.name, friend.age, friend.gender, requestLanguage
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
                ConversationManager.removeFriend(friend.id)
                store.deleteFriendData(friend)
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

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DostTheme { DostApp() }
}