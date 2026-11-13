@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.snigtus.dost

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

private fun Context.activityOrNull(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        val wrapper = current as? ContextWrapper ?: return null
        val base = wrapper.baseContext
        if (base === current) return null
        current = base
    }
    return null
}

@Composable
fun DostApp(
    openFriendRequests: StateFlow<OpenFriendRequest?> = MutableStateFlow(null),
    viewModel: DostAppViewModel? = null
) {
    val context = LocalContext.current
    val authRepository = remember(context) { FirebaseAuthRepository(context) }
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
    var authMode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var authRequestState by remember { mutableStateOf<AuthRequestState>(AuthRequestState.Idle) }
    var settingsNotice by remember { mutableStateOf("") }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            settingsNotice = try {
                val backup = withContext(Dispatchers.IO) { store.exportLocalData().toString(2) }
                require(backup.length <= MAX_LOCAL_BACKUP_CHARS) { "The backup is too large to export." }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(backup) }
                        ?: throw java.io.IOException("Could not open the selected file.")
                }
                "Local data exported."
            } catch (error: Exception) {
                "Export failed: ${error.message?.take(160) ?: "could not write the file"}"
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            settingsNotice = "Importing local data…"
            try {
                withContext(Dispatchers.IO) {
                    ConversationManager.importLocalData(JSONObject(readLocalBackup(context, uri)))
                }
                language = store.language()
                name = store.userName()
                email = store.userEmail()
                age = store.userAge()
                gender = store.userGender()
                userTimeZoneId = store.userTimeZoneId()
                openRouterApiKey = store.openRouterApiKey()
                aiModel = store.aiModel()
                friends.clear()
                friends.addAll(store.loadFriends())
                selectedFriend = null
                screen = AppScreen.HOME
                settingsNotice = "Local data imported."
            } catch (error: Exception) {
                settingsNotice = "Import failed: ${error.message?.take(160) ?: "the file is invalid"}"
            }
        }
    }
    var generatingProfileIds by appViewModel.generatingProfileIds
    var profileErrors by appViewModel.profileErrors
    val openFriendRequest by openFriendRequests.collectAsState()

    fun storedAccountIds(): List<String> = listOf(
        store.authenticationAccountId(),
        store.firebaseAccountId()
    ).filter(String::isNotBlank).distinct()

    fun canBindLocalProfile(accountId: String): Boolean {
        val storedIds = storedAccountIds()
        return if (storedIds.isEmpty()) {
            !store.hasUserProfile() || !store.authenticationRequired()
        } else {
            storedIds.all { it == accountId }
        }
    }

    fun hasLocalAccountAccess(): Boolean {
        if (!store.hasEligibleAccount()) return false
        val accountId = authRepository.currentAccount?.uid
        val storedIds = storedAccountIds()
        return if (accountId.isNullOrBlank()) {
            storedIds.isEmpty() && !store.authenticationRequired()
        } else {
            storedIds.isNotEmpty() && storedIds.all { it == accountId }
        }
    }

    fun openRequestedFriend(friendId: String?) {
        val resolved = ChatLaunch.friendToOpen(hasLocalAccountAccess(), friendId, friends.map { it.id }.toSet()) ?: return
        selectedFriend = friends.first { it.id == resolved }
        screen = AppScreen.CHAT
    }

    fun completeAuthentication(account: AuthAccount) {
        if (!canBindLocalProfile(account.uid)) {
            authRepository.signOut()
            authMode = AuthMode.SIGN_IN
            authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
            return
        }
        store.bindAuthenticationAccount(account.uid)
        store.linkFirebaseAccount(account.uid)
        store.markAuthenticationRequired()
        email = account.email.ifBlank { email }
        if (!store.hasEligibleAccount()) name = account.displayName.ifBlank { name }
        authMode = AuthMode.SIGN_IN
        authRequestState = AuthRequestState.Idle
        screen = authenticationStartScreen(
            hasSignedInUser = true,
            hasEligibleLocalProfile = store.hasEligibleAccount(),
            authenticationRequired = true,
            signedInUserId = account.uid,
            localAccountId = store.authenticationAccountId().ifBlank { store.firebaseAccountId() }
        )
    }

    fun authenticate(action: suspend () -> AuthAccount, sendVerificationEmail: Boolean = false) {
        if (authRequestState is AuthRequestState.Loading) return
        scope.launch {
            authRequestState = AuthRequestState.Loading
            try {
                val account = action()
                if (account.needsEmailVerification) {
                    email = account.email
                    authMode = AuthMode.VERIFY_EMAIL
                    if (sendVerificationEmail) authRepository.sendEmailVerification()
                    authRequestState = AuthRequestState.Success(
                        when {
                            sendVerificationEmail && language == AppLanguage.BANGLA -> "যাচাইকরণ ইমেইল পাঠানো হয়েছে। লিংক খুলে এখানে ফিরে আসুন।"
                            sendVerificationEmail && language == AppLanguage.HINDI -> "सत्यापन ईमेल भेजा गया। लिंक खोलें, फिर यहाँ लौटें।"
                            sendVerificationEmail -> "Verification email sent. Open the link, then return here."
                            language == AppLanguage.BANGLA -> "সাইন ইন শেষ করতে ইমেইল যাচাই করুন।"
                            language == AppLanguage.HINDI -> "साइन इन पूरा करने के लिए अपना ईमेल सत्यापित करें।"
                            else -> "Verify your email to finish signing in."
                        }
                    )
                    return@launch
                }
                completeAuthentication(account)
            } catch (error: CancellationException) {
                authRequestState = AuthRequestState.Idle
                throw error
            } catch (error: Exception) {
                authRequestState = AuthRequestState.Error(authenticationErrorText(language, error))
            }
        }
    }

    fun resendVerificationEmail() {
        if (authRequestState is AuthRequestState.Loading) return
        scope.launch {
            authRequestState = AuthRequestState.Loading
            try {
                authRepository.sendEmailVerification()
                authRequestState = AuthRequestState.Success(
                    when (language) {
                        AppLanguage.BANGLA -> "যাচাইকরণ ইমেইল আবার পাঠানো হয়েছে।"
                        AppLanguage.HINDI -> "सत्यापन ईमेल फिर भेजा गया है।"
                        AppLanguage.ENGLISH -> "Verification email sent again."
                    }
                )
            } catch (error: CancellationException) {
                authRequestState = AuthRequestState.Idle
                throw error
            } catch (error: Exception) {
                authRequestState = AuthRequestState.Error(authenticationErrorText(language, error))
            }
        }
    }

    fun checkEmailVerification() {
        if (authRequestState is AuthRequestState.Loading) return
        scope.launch {
            authRequestState = AuthRequestState.Loading
            try {
                val account = authRepository.reloadCurrentAccount()
                if (account.needsEmailVerification) {
                    authRequestState = AuthRequestState.Error(
                        when (language) {
                            AppLanguage.BANGLA -> "ইমেইল যাচাই করা হয়নি। ইমেইলের লিংক খুলে আবার চেষ্টা করুন।"
                            AppLanguage.HINDI -> "ईमेल सत्यापित नहीं हुआ। ईमेल का लिंक खोलकर फिर कोशिश करें।"
                            AppLanguage.ENGLISH -> "Your email is not verified yet. Open the link from your email and try again."
                        }
                    )
                } else {
                    completeAuthentication(account)
                }
            } catch (error: CancellationException) {
                authRequestState = AuthRequestState.Idle
                throw error
            } catch (error: Exception) {
                authRequestState = AuthRequestState.Error(authenticationErrorText(language, error))
            }
        }
    }

    fun sendPasswordReset(resetEmail: String) {
        if (authRequestState is AuthRequestState.Loading) return
        scope.launch {
            authRequestState = AuthRequestState.Loading
            try {
                authRepository.sendPasswordReset(resetEmail)
                authRequestState = AuthRequestState.Success(
                    when (language) {
                        AppLanguage.BANGLA -> "অ্যাকাউন্ট থাকলে ইমেইলে রিসেট লিংক পাঠানো হয়েছে।"
                        AppLanguage.HINDI -> "यदि खाता मौजूद है, तो ईमेल पर रीसेट लिंक भेज दिया गया है।"
                        AppLanguage.ENGLISH -> "If an account exists, a reset link has been sent to that email."
                    }
                )
            } catch (error: CancellationException) {
                authRequestState = AuthRequestState.Idle
                throw error
            } catch (error: Exception) {
                authRequestState = AuthRequestState.Error(authenticationErrorText(language, error))
            }
        }
    }

    LaunchedEffect(openFriendRequest?.nonce) {
        if (screen == AppScreen.SPLASH) return@LaunchedEffect
        openRequestedFriend(openFriendRequest?.friendId)
    }
    LaunchedEffect(screen, authRepository.currentAccount?.uid, authRepository.currentAccount?.emailVerified) {
        val currentAccount = authRepository.currentAccount
        if (currentAccount?.needsEmailVerification == true && screen != AppScreen.SPLASH) {
            if (authMode != AuthMode.VERIFY_EMAIL) {
                email = currentAccount.email
                authMode = AuthMode.VERIFY_EMAIL
                authRequestState = AuthRequestState.Success(
                    when (language) {
                        AppLanguage.BANGLA -> "সাইন ইন শেষ করতে ইমেইল যাচাই করুন।"
                        AppLanguage.HINDI -> "साइन इन पूरा करने के लिए अपना ईमेल सत्यापित करें।"
                        AppLanguage.ENGLISH -> "Verify your email to finish signing in."
                    }
                )
            }
            if (screen != AppScreen.AUTH) {
                selectedFriend = null
                screen = AppScreen.AUTH
            }
            return@LaunchedEffect
        }
        if (screen !in setOf(AppScreen.HOME, AppScreen.CHAT, AppScreen.KNOWLEDGE) || hasLocalAccountAccess()) return@LaunchedEffect
        val currentAccountId = currentAccount?.uid
        if (!currentAccountId.isNullOrBlank() && storedAccountIds().any { it != currentAccountId }) {
            authRepository.signOut()
            authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
        }
        selectedFriend = null
        screen = AppScreen.AUTH
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
            val order = listOf(AppScreen.SPLASH, AppScreen.AUTH, AppScreen.REGISTER, AppScreen.HOME, AppScreen.CHAT, AppScreen.KNOWLEDGE)
            val forward = order.indexOf(targetState) >= order.indexOf(initialState)
            val slideIn = slideInHorizontally(animationSpec = tween(300)) { if (forward) it else -it }
            val slideOut = slideOutHorizontally(animationSpec = tween(300)) { if (forward) -it else it }
            slideIn.togetherWith(slideOut)
        }
    ) { currentScreen ->
    when (currentScreen) {
        AppScreen.SPLASH -> DostSplashScreen(language) {
            var currentAccount = authRepository.currentAccount
            if (currentAccount?.needsEmailVerification == true) {
                email = currentAccount.email
                authMode = AuthMode.VERIFY_EMAIL
                authRequestState = AuthRequestState.Success(
                    when (language) {
                        AppLanguage.BANGLA -> "সাইন ইন শেষ করতে ইমেইল যাচাই করুন।"
                        AppLanguage.HINDI -> "साइन इन पूरा करने के लिए अपना ईमेल सत्यापित करें।"
                        AppLanguage.ENGLISH -> "Verify your email to finish signing in."
                    }
                )
                screen = AppScreen.AUTH
            } else {
                if (currentAccount != null) {
                    if (!canBindLocalProfile(currentAccount.uid)) {
                        store.markAuthenticationRequired()
                        authRepository.signOut()
                        currentAccount = null
                        authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
                    } else {
                        store.bindAuthenticationAccount(currentAccount.uid)
                        store.linkFirebaseAccount(currentAccount.uid)
                        store.markAuthenticationRequired()
                        email = currentAccount.email.ifBlank { email }
                        if (!store.hasEligibleAccount()) name = currentAccount.displayName.ifBlank { name }
                    }
                }
                val destination = authenticationStartScreen(
                    hasSignedInUser = currentAccount != null,
                    hasEligibleLocalProfile = store.hasEligibleAccount(),
                    authenticationRequired = store.authenticationRequired(),
                    signedInUserId = currentAccount?.uid,
                    localAccountId = store.authenticationAccountId().ifBlank { store.firebaseAccountId() }
                )
                val requestedId = openFriendRequests.value?.friendId
                val resolved = ChatLaunch.friendToOpen(destination == AppScreen.HOME, requestedId, friends.map { it.id }.toSet())
                if (resolved != null) {
                    selectedFriend = friends.first { it.id == resolved }
                    screen = AppScreen.CHAT
                } else {
                    screen = destination
                }
            }
        }
        AppScreen.AUTH -> AuthScreen(
            language = language,
            initialEmail = email,
            mode = authMode,
            onModeChange = { authMode = it },
            requestState = authRequestState,
            emailSignInEnabled = authRepository.emailSignInEnabled,
            googleSignInEnabled = authRepository.googleSignInEnabled,
            onSignIn = { authEmail, password ->
                authenticate({ authRepository.signIn(authEmail, password) })
            },
            onCreateAccount = { authEmail, password ->
                authenticate({ authRepository.createAccount(authEmail, password) }, sendVerificationEmail = true)
            },
            onPasswordReset = ::sendPasswordReset,
            onResendVerification = ::resendVerificationEmail,
            onCheckVerification = ::checkEmailVerification,
            onGoogleSignIn = {
                val activity = context.activityOrNull()
                if (activity == null) {
                    authRequestState = AuthRequestState.Error(
                        when (language) {
                            AppLanguage.BANGLA -> "এই স্ক্রিনে Google সাইন-ইন চালু করা যায়নি।"
                            AppLanguage.HINDI -> "इस स्क्रीन पर Google साइन-इन उपलब्ध नहीं है।"
                            AppLanguage.ENGLISH -> "Google sign-in isn't available from this screen."
                        }
                    )
                } else {
                    authenticate({ authRepository.signInWithGoogle(activity) })
                }
            }
        )
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
                store.saveUserProfile(name, email, age.trim(), gender, authRepository.currentAccount?.uid)
                friend?.let { addFriendWithProfile(it.copy(age = it.age.trim())) }
                screen = AppScreen.HOME
            },
            onBack = { if (registerStep == 2) registerStep = 1 else screen = AppScreen.AUTH },
            emailEditable = false
        )
        AppScreen.HOME -> if (hasLocalAccountAccess()) HomeScreen(
            language = language,
            userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
            signedInEmail = authRepository.currentAccount?.email,
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
            onSignOut = {
                authRepository.signOut()
                store.markAuthenticationRequired()
                selectedFriend = null
                authMode = AuthMode.SIGN_IN
                authRequestState = AuthRequestState.Idle
                screen = AppScreen.AUTH
            },
            generatingProfileIds = generatingProfileIds,
            profileFailedIds = profileErrors.keys,
            onExportLocalData = { backupExportLauncher.launch("dost-local-backup.json") },
            onImportLocalData = { backupImportLauncher.launch(arrayOf("application/json")) },
            settingsNotice = settingsNotice,
            onDeleteServerArchive = {
                scope.launch {
                    settingsNotice = "Deleting server archive…"
                    settingsNotice = try {
                        val deleted = OpenRouterClient().deleteServerArchive(store.installationId())
                        "Server archive deleted ($deleted sessions)."
                    } catch (error: Exception) {
                        "Archive deletion failed: ${error.message?.take(160) ?: "please try again"}"
                    }
                }
            }
        )
        AppScreen.CHAT -> if (hasLocalAccountAccess()) selectedFriend?.let { friend ->
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
        AppScreen.KNOWLEDGE -> if (hasLocalAccountAccess()) KnowledgeScreen(
            language = language,
            friends = friends,
            store = store,
            onBack = { screen = AppScreen.HOME }
        )
    }
    }
}

private fun readLocalBackup(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Could not open the selected file.")
    return input.reader(Charsets.UTF_8).use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(text.length + count <= MAX_LOCAL_BACKUP_CHARS) { "The backup file is too large to import." }
            text.append(buffer, 0, count)
        }
        text.toString()
    }
}

private const val MAX_LOCAL_BACKUP_CHARS = 25_000_000

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DostTheme { DostApp() }
}