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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val openFriendRequests = MutableStateFlow<OpenFriendRequest?>(null)
    private var appViewModel: DostAppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reloadProfile = intent.getBooleanExtra(EXTRA_RELOAD_PROFILE, false)
        intent.removeExtra(EXTRA_RELOAD_PROFILE)
        if (savedInstanceState == null) publishOpenFriend(intent)
        enableEdgeToEdge()
        setContent {
            DostTheme {
                val ageGateLanguage = remember(this@MainActivity) {
                    languageForActiveProfile(this@MainActivity)
                }
                PlayAgeSignalsGate(
                    activity = this@MainActivity,
                    language = ageGateLanguage
                ) {
                    val viewModel = remember(this@MainActivity) {
                        ViewModelProvider(this@MainActivity)[DostAppViewModel::class.java].also {
                            savedInstanceState?.takeUnless { reloadProfile }?.let { state ->
                                it.restoreNavigationState(
                                    state.getString(KEY_SCREEN),
                                    state.getString(KEY_SELECTED_FRIEND_ID)
                                )
                            }
                            appViewModel = it
                        }
                    }
                    DostApp(openFriendRequests, viewModel)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        appViewModel?.let {
            outState.putString(KEY_SCREEN, it.screen.value.name)
            outState.putString(KEY_SELECTED_FRIEND_ID, it.selectedFriend.value?.id)
        }
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
        val requestedProfile = intent?.getStringExtra(ChatLaunch.EXTRA_PROFILE_NAMESPACE)
            ?: LEGACY_PROFILE_NAMESPACE
        if (requestedProfile != activeProfileNamespace(this)) return
        val friendId = intent?.getStringExtra(ChatLaunch.EXTRA_FRIEND_ID)?.takeIf { it.isNotBlank() } ?: return
        openFriendRequests.value = OpenFriendRequest(friendId, System.nanoTime())
    }
}

private const val KEY_SCREEN = "screen"
private const val KEY_SELECTED_FRIEND_ID = "selected_friend_id"
private const val EXTRA_RELOAD_PROFILE = "reload_profile"

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

private fun restartForActiveProfile(context: Context): Boolean {
    val activity = context.activityOrNull() as? MainActivity ?: return false
    WorkManager.getInstance(context).cancelAllWork()
    NotificationManagerCompat.from(context).cancelAll()
    activity.viewModelStore.clear()
    activity.intent.putExtra(EXTRA_RELOAD_PROFILE, true)
    activity.recreate()
    return true
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
    var chatLanguage by appViewModel.chatLanguage
    var screen by appViewModel.screen
    var showFriendCount by remember { mutableStateOf(store.showFriendCount()) }
    var name by appViewModel.name
    var email by appViewModel.email
    var age by appViewModel.age
    var gender by appViewModel.gender
    var userTimeZoneId by appViewModel.userTimeZoneId
    var openRouterApiKey by appViewModel.openRouterApiKey
    var aiModel by appViewModel.aiModel
    var anonymousTelemetryEnabled by remember { mutableStateOf(store.anonymousTelemetryEnabled()) }
    var aiNotificationPreviewsEnabled by remember { mutableStateOf(store.aiNotificationPreviewsEnabled()) }
    var proactiveEnabled by remember { mutableStateOf(store.proactiveEnabled()) }
    var proactiveCadenceMinutes by remember { mutableIntStateOf(store.proactiveCadenceMinutes()) }
    var automaticFollowUpMinutes by remember { mutableIntStateOf(store.automaticFollowUpMinutes()) }
    var quietHoursStartMinute by remember { mutableIntStateOf(store.quietHoursStartMinute()) }
    var quietHoursEndMinute by remember { mutableIntStateOf(store.quietHoursEndMinute()) }
    val friends = appViewModel.friends
    var selectedFriend by appViewModel.selectedFriend
    LaunchedEffect(screen) {
        SuperSecureSession.active = screen == AppScreen.SUPER_SECURE
        if (screen == AppScreen.SUPER_SECURE) {
            context.stopService(Intent(context, FloatingChatService::class.java))
            friends.forEach { ReplyNotifier.cancelReply(context, it.id) }
        }
    }
    var selectedMainTab by rememberSaveable { mutableStateOf(MainTab.CHATS) }
    var homeInboxFilter by remember { mutableStateOf(HomeInboxFilter.ALL) }
    var homeInboxSearchQuery by remember { mutableStateOf("") }
    var pinnedConversationIds by remember { mutableStateOf(store.pinnedConversationIds()) }
    var mutedConversationIds by remember { mutableStateOf(store.mutedConversationIds()) }
    var archivedConversationIds by remember { mutableStateOf(store.archivedConversationIds()) }
    var closeAfterOverlayPermission by appViewModel.closeAfterOverlayPermission
    var bubbleFriendId by appViewModel.bubbleFriendId
    val scope = rememberCoroutineScope()
    var authMode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var authRequestState by remember { mutableStateOf<AuthRequestState>(AuthRequestState.Idle) }
    var settingsNotice by remember { mutableStateOf("") }
    var checkingPeopleEligibility by remember { mutableStateOf(false) }
    var peopleEligibilityError by remember { mutableStateOf<String?>(null) }
    var backupPasswordMode by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassword by rememberSaveable { mutableStateOf("") }
    var backupPasswordConfirmation by rememberSaveable { mutableStateOf("") }
    var pendingEncryptedBackup by remember { mutableStateOf<String?>(null) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var backupPasswordError by remember { mutableStateOf("") }
    var settingsReturnScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var friendProfileReturnScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var knowledgeReturnScreen by rememberSaveable { mutableStateOf(AppScreen.SETTINGS) }
    var knowledgeFriendFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var chatReturnScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var peopleChatReturnScreen by rememberSaveable { mutableStateOf(AppScreen.PEOPLE) }
    var gameReturnScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    val homeListState = rememberLazyListState()
    val knowledgeListState = rememberLazyListState()
    var systemFriendCatalog by remember { mutableStateOf(emptyList<SystemFriendListing>()) }
    var systemFriendCatalogLoading by remember { mutableStateOf(false) }
    var systemFriendCatalogError by remember { mutableStateOf<String?>(null) }
    var systemFriendAddError by remember { mutableStateOf<String?>(null) }
    var addingSystemFriendId by remember { mutableStateOf<String?>(null) }
    var recentlyAddedSystemFriend by remember { mutableStateOf<Friend?>(null) }
    var selectedPeopleConversation by remember { mutableStateOf<SocialConversation?>(null) }
    var peopleInboxConversations by remember { mutableStateOf(emptyList<SocialConversation>()) }
    var peopleInboxLoading by remember { mutableStateOf(false) }
    var peopleInboxError by remember { mutableStateOf<String?>(null) }
    var peopleInboxEligibleUid by remember { mutableStateOf("") }
    var peopleInboxRefreshKey by remember { mutableIntStateOf(0) }
    val activeFirebaseUid = authRepository.currentAccount?.uid.orEmpty()
    var isPremium by remember(activeFirebaseUid) { mutableStateOf(false) }

    LaunchedEffect(activeFirebaseUid, store.birthMonth(), store.birthYear()) {
        if (activeFirebaseUid.isBlank()) return@LaunchedEffect
        try {
            authRepository.ensureAgeCohort(store.birthMonth(), store.birthYear())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Unit
        }
    }

    LaunchedEffect(activeFirebaseUid, isPremium) {
        if (activeFirebaseUid.isBlank()) {
            isPremium = false
            return@LaunchedEffect
        }
        try {
            val entitlement = authRepository.premiumEntitlement()
            isPremium = entitlement.isActive
            if (entitlement.isActive && !entitlement.isLifetime) {
                val expiry = entitlement.expiresAtEpochSeconds ?: return@LaunchedEffect
                val remainingMillis = expiry * 1000 - System.currentTimeMillis()
                if (remainingMillis > 0) delay(remainingMillis)
                isPremium = authRepository.hasPremiumEntitlement(forceRefresh = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            isPremium = false
            settingsNotice = "Premium status could not be refreshed: ${error.message?.take(120) ?: "try again"}"
        }
    }

    fun toggleConversationPinned(conversationKey: String) {
        store.setConversationPinned(conversationKey, conversationKey !in pinnedConversationIds)
        pinnedConversationIds = store.pinnedConversationIds()
    }

    fun toggleConversationMuted(conversationKey: String) {
        val muted = conversationKey !in mutedConversationIds
        store.setConversationMuted(conversationKey, muted)
        mutedConversationIds = store.mutedConversationIds()
        if (muted) {
            when {
                conversationKey.startsWith("ai:") ->
                    ReplyNotifier.cancelReply(context, conversationKey.removePrefix("ai:"))
                conversationKey.startsWith("people:") ->
                    PeopleMessageNotifications.cancel(context, conversationKey.removePrefix("people:"))
            }
        }
    }

    fun toggleConversationArchived(conversationKey: String) {
        store.setConversationArchived(conversationKey, conversationKey !in archivedConversationIds)
        archivedConversationIds = store.archivedConversationIds()
    }

    fun withAdultEligibility(action: () -> Unit) {
        if (checkingPeopleEligibility) return
        scope.launch {
            checkingPeopleEligibility = true
            peopleEligibilityError = null
            try {
                authRepository.ensureAgeCohort(store.birthMonth(), store.birthYear())
                authRepository.ensureAdultEligibility()
                peopleInboxEligibleUid = authRepository.currentAccount?.uid.orEmpty()
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                peopleEligibilityError = error.message?.take(160)
                    ?: "The age-eligibility check failed."
            } finally {
                checkingPeopleEligibility = false
            }
        }
    }

    fun openSuperSecure() {
        if (!BuildConfig.DOST_SUPER_SECURE_ENABLED) return
        withAdultEligibility {
            SuperSecureSession.active = true
            screen = AppScreen.SUPER_SECURE
        }
    }

    fun closeSuperSecure() {
        SuperSecureSession.active = false
        ConversationManager.refreshProactiveWork()
        screen = AppScreen.HOME
    }

    LaunchedEffect(activeFirebaseUid) {
        showFriendCount = store.showFriendCount()
        aiNotificationPreviewsEnabled = store.aiNotificationPreviewsEnabled()
        homeInboxFilter = HomeInboxFilter.ALL
        homeInboxSearchQuery = ""
        recentlyAddedSystemFriend = null
        pinnedConversationIds = store.pinnedConversationIds()
        mutedConversationIds = store.mutedConversationIds()
        archivedConversationIds = store.archivedConversationIds()
        peopleInboxConversations = emptyList()
        peopleInboxEligibleUid = ""
        peopleInboxError = null
        peopleInboxLoading = false
    }

    LaunchedEffect(recentlyAddedSystemFriend?.id) {
        val friendId = recentlyAddedSystemFriend?.id ?: return@LaunchedEffect
        delay(15_000)
        if (recentlyAddedSystemFriend?.id == friendId) recentlyAddedSystemFriend = null
    }

    LaunchedEffect(screen, selectedMainTab, activeFirebaseUid, peopleInboxRefreshKey) {
        if (screen != AppScreen.HOME || selectedMainTab != MainTab.CHATS || !BuildConfig.DOST_PEOPLE_ENABLED) {
            return@LaunchedEffect
        }
        if (activeFirebaseUid.isBlank()) {
            peopleInboxConversations = emptyList()
            peopleInboxError = when (language) {
                AppLanguage.BANGLA -> "মানুষের চ্যাট দেখতে Firebase-এ সাইন ইন করুন।"
                AppLanguage.HINDI -> "लोगों की चैट देखने के लिए Firebase में साइन इन करें।"
                AppLanguage.ENGLISH -> "Sign in to Firebase to show People chats."
            }
            return@LaunchedEffect
        }
        if (peopleInboxEligibleUid == activeFirebaseUid) return@LaunchedEffect

        peopleInboxLoading = true
        peopleInboxError = null
        try {
            authRepository.ensureAgeCohort(store.birthMonth(), store.birthYear())
            authRepository.ensureAdultEligibility()
            peopleInboxEligibleUid = activeFirebaseUid
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            peopleInboxError = error.message?.take(160)
                ?: "The age-eligibility check failed."
        } finally {
            peopleInboxLoading = false
        }
    }

    DisposableEffect(
        screen,
        selectedMainTab,
        activeFirebaseUid,
        peopleInboxEligibleUid,
        peopleInboxRefreshKey
    ) {
        if (screen != AppScreen.HOME || selectedMainTab != MainTab.CHATS
            || !BuildConfig.DOST_PEOPLE_ENABLED
            || activeFirebaseUid.isBlank()
            || peopleInboxEligibleUid != activeFirebaseUid
        ) {
            onDispose { }
        } else {
            peopleInboxLoading = true
            val registration = try {
                PeopleRepository(context).listenConversations(
                    activeFirebaseUid,
                    onChange = {
                        peopleInboxConversations = it
                        peopleInboxError = null
                        peopleInboxLoading = false
                    },
                    onError = {
                        peopleInboxError = it.message ?: "Could not load People chats."
                        peopleInboxLoading = false
                    }
                )
            } catch (error: Exception) {
                peopleInboxError = error.message ?: "Could not load People chats."
                peopleInboxLoading = false
                null
            }
            onDispose { registration?.remove() }
        }
    }

    suspend fun importBackupText(backupText: String) {
        withContext(Dispatchers.IO) {
            ConversationManager.importLocalData(JSONObject(backupText))
        }
        language = store.language()
        chatLanguage = store.chatLanguage()
        showFriendCount = store.showFriendCount()
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
        recentlyAddedSystemFriend = null
        knowledgeFriendFilter = null
        screen = AppScreen.HOME
        settingsNotice = "Local data imported."
    }

    BackHandler(
        enabled = screen in setOf(
            AppScreen.CHAT,
            AppScreen.PEOPLE,
            AppScreen.PEOPLE_CHAT,
            AppScreen.SUPER_SECURE,
            AppScreen.FRIEND_PROFILE,
            AppScreen.SYSTEM_FRIEND_PICKER,
            AppScreen.KNOWLEDGE,
            AppScreen.GAMES,
            AppScreen.SETTINGS,
            AppScreen.PREMIUM,
            AppScreen.REGISTER
        ) || screen == AppScreen.AUTH && authMode != AuthMode.SIGN_IN
    ) {
        when (screen) {
            AppScreen.CHAT -> screen = chatReturnScreen
            AppScreen.PEOPLE_CHAT -> screen = peopleChatReturnScreen
            AppScreen.SUPER_SECURE -> {
                closeSuperSecure()
            }
            AppScreen.PEOPLE -> screen = AppScreen.HOME
            AppScreen.FRIEND_PROFILE -> screen = friendProfileReturnScreen
            AppScreen.SYSTEM_FRIEND_PICKER -> screen = AppScreen.HOME
            AppScreen.GAMES -> screen = gameReturnScreen
            AppScreen.KNOWLEDGE -> screen = knowledgeReturnScreen
            AppScreen.SETTINGS -> screen = settingsReturnScreen
            AppScreen.PREMIUM -> screen = AppScreen.SETTINGS
            AppScreen.REGISTER -> screen = AppScreen.AUTH
            AppScreen.AUTH -> authMode = AuthMode.SIGN_IN
            else -> Unit
        }
    }
    val systemFriendClient = remember { SystemFriendClient() }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            settingsNotice = try {
                val exportPassword = pendingExportPassword ?: error("The backup passphrase was lost.")
                val password = exportPassword.toCharArray()
                val backup = withContext(Dispatchers.IO) {
                    try {
                        LocalBackupCrypto.encrypt(store.exportLocalData().toString(2), password)
                    } finally {
                        password.fill('\u0000')
                    }
                }
                require(backup.length <= MAX_LOCAL_BACKUP_CHARS) { "The backup is too large to export." }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(backup) }
                        ?: throw java.io.IOException("Could not open the selected file.")
                }
                "Encrypted backup exported. Keep the passphrase separate; it cannot be recovered."
            } catch (error: Exception) {
                "Export failed: ${error.message?.take(160) ?: "could not write the file"}"
            } finally {
                pendingExportPassword = null
                backupPassword = ""
                backupPasswordConfirmation = ""
            }
        }
        else {
            pendingExportPassword = null
            backupPassword = ""
            backupPasswordConfirmation = ""
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            settingsNotice = "Importing local data…"
            try {
                val backupText = withContext(Dispatchers.IO) { readLocalBackup(context, uri) }
                if (LocalBackupCrypto.isEncryptedBackup(backupText)) {
                    pendingEncryptedBackup = backupText
                    backupPassword = ""
                    backupPasswordConfirmation = ""
                    backupPasswordError = ""
                    backupPasswordMode = "import"
                    settingsNotice = "Enter the passphrase used to encrypt this backup."
                } else {
                    importBackupText(backupText)
                }
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
        if (store.activateAccount(account.uid)) {
            if (!restartForActiveProfile(context)) {
                authRepository.signOut()
                authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
            }
            return
        }
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
        if (screen in setOf(AppScreen.SPLASH, AppScreen.LANGUAGE_SETUP)) return@LaunchedEffect
        openRequestedFriend(openFriendRequest?.friendId)
    }
    LaunchedEffect(screen, authRepository.currentAccount?.uid, authRepository.currentAccount?.emailVerified) {
        val currentAccount = authRepository.currentAccount
        if (currentAccount?.needsEmailVerification == true && screen !in setOf(AppScreen.SPLASH, AppScreen.LANGUAGE_SETUP)) {
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
        if (screen !in setOf(AppScreen.HOME, AppScreen.SYSTEM_FRIEND_PICKER, AppScreen.CHAT, AppScreen.PEOPLE, AppScreen.PEOPLE_CHAT, AppScreen.GAMES, AppScreen.KNOWLEDGE) || hasLocalAccountAccess()) return@LaunchedEffect
        val currentAccountId = currentAccount?.uid
        if (!currentAccountId.isNullOrBlank() && storedAccountIds().any { it != currentAccountId }) {
            if (store.activateAccount(currentAccountId)) {
                if (!restartForActiveProfile(context)) {
                    authRepository.signOut()
                    authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
                }
            } else {
                authRepository.signOut()
                authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
            }
            return@LaunchedEffect
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            settingsNotice = when (language) {
                AppLanguage.BANGLA -> "Dost-এর নোটিফিকেশন বন্ধ। আপনি চ্যাট চালিয়ে যেতে পারেন; সতর্কবার্তা চাইলে Android নোটিফিকেশন সেটিংস থেকে চালু করুন।"
                AppLanguage.HINDI -> "Dost की सूचनाएँ बंद हैं। आप चैट जारी रख सकते हैं; अलर्ट चाहिए तो Android सूचना सेटिंग में चालू करें।"
                AppLanguage.ENGLISH -> "Dost notifications are off. You can still chat; turn them on in Android notification settings whenever you want alerts."
            }
        }
    }
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
        if (screen == AppScreen.HOME && proactiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                val firebaseIdToken = try {
                    authRepository.currentIdToken()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    ""
                }
                val profile = OpenRouterClient().generateFriendProfile(
                    store.openRouterApiKey(), store.aiModel(), friend.name, friend.age, friend.gender,
                    friend.personality, requestLanguage, firebaseIdToken
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
        if (!isPremium && friends.count { it.catalogId.isBlank() } >= MAX_CUSTOM_FRIENDS) return
        val pending = safeFriend.copy(profilePending = true)
        friends.add(pending)
        store.saveFriends(friends)
        startProfileGeneration(pending.id)
    }

    LaunchedEffect(Unit) {
        friends.filter { it.profilePending }.forEach { startProfileGeneration(it.id) }
    }

    fun removeFriendPermanently(friend: Friend) {
        if (!friends.remove(friend)) return
        store.saveFriends(friends)
        ConversationWorkScheduler.cancelAllForFriend(context, friend.id)
        ReplyNotifier.cancelReply(context, friend.id)
        ConversationManager.removeFriend(friend.id)
        store.deleteFriendData(friend)
        pinnedConversationIds = store.pinnedConversationIds()
        mutedConversationIds = store.mutedConversationIds()
        archivedConversationIds = store.archivedConversationIds()
        generatingProfileIds = generatingProfileIds - friend.id
        profileErrors = profileErrors - friend.id
        if (recentlyAddedSystemFriend?.id == friend.id) recentlyAddedSystemFriend = null
    }

    fun addSystemFriend(friend: Friend): Friend? {
        if (friend.catalogId.isBlank()
            || (!isPremium && friends.count { it.catalogId.isNotBlank() } >= MAX_SYSTEM_FRIENDS)
            || friends.any { it.catalogId == friend.catalogId }
        ) return null
        val addedFriend = friend.copy(id = java.util.UUID.randomUUID().toString()).withLoveMeterGated(store.userAge())
        friends.add(addedFriend)
        store.saveFriends(friends)
        ConversationWorkScheduler.scheduleMorningGreeting(context, addedFriend, language)
        return addedFriend
    }

    fun loadSystemFriendCatalog() {
        systemFriendCatalogLoading = true
        systemFriendCatalogError = null
        systemFriendAddError = null
        scope.launch {
            try {
                systemFriendCatalog = systemFriendClient.getCatalog(chatLanguage)
            } catch (error: java.io.IOException) {
                systemFriendCatalogError = LocalizedText.LOAD_FRIENDS_FAILED.resolve(language)
            } catch (error: org.json.JSONException) {
                systemFriendCatalogError = LocalizedText.INVALID_FRIEND_CATALOG.resolve(language)
            } finally {
                systemFriendCatalogLoading = false
            }
        }
    }

    fun addCatalogFriend(listing: SystemFriendListing) {
        val addedCatalogIds = friends.mapNotNull { it.catalogId.takeIf(String::isNotBlank) }.toSet()
        if (addingSystemFriendId != null || listing.id in addedCatalogIds
            || (!isPremium && friends.count { it.catalogId.isNotBlank() } >= MAX_SYSTEM_FRIENDS)
        ) return
        addingSystemFriendId = listing.id
        systemFriendAddError = null
        scope.launch {
            try {
                val profile = systemFriendClient.getProfile(listing.id, chatLanguage)
                val friendWithCachedPhoto = systemFriendClient.savePhotoForOfflineUse(context, profile)
                val addedFriend = addSystemFriend(friendWithCachedPhoto)
                if (addedFriend != null) {
                    recentlyAddedSystemFriend = addedFriend
                    selectedMainTab = MainTab.CHATS
                    homeInboxFilter = HomeInboxFilter.ALL
                    homeInboxSearchQuery = ""
                    screen = AppScreen.HOME
                }
            } catch (error: java.io.IOException) {
                systemFriendAddError = LocalizedText.ADD_SYSTEM_FRIEND_FAILED.resolve(language)
            } catch (error: org.json.JSONException) {
                systemFriendAddError = LocalizedText.INVALID_SYSTEM_FRIEND.resolve(language)
            } finally {
                addingSystemFriendId = null
            }
        }
    }

    fun openFriendProfile(friend: Friend, returnTo: AppScreen) {
        selectedFriend = friend
        friendProfileReturnScreen = returnTo
        screen = AppScreen.FRIEND_PROFILE
    }

    fun updateFriendTimeZone(friend: Friend, timeZoneId: String) {
        val updated = store.updateFriend(friend.id) { current ->
            current.copy(timeZoneId = validZoneId(timeZoneId).id)
        } ?: return
        val index = friends.indexOfFirst { it.id == friend.id }
        if (index >= 0) friends[index] = updated
        selectedFriend = updated
        ConversationManager.refreshBusyReply(updated)
        ConversationWorkScheduler.scheduleMorningGreeting(context, updated, language)
    }

    fun updateFriendSchedule(friend: Friend, blocks: List<RoutineBlock>, behavior: BusyReplyBehavior, delayMinutes: Int) {
        val primaryBlock = blocks.firstOrNull() ?: return
        val updated = store.updateFriend(friend.id) { current ->
            current.copy(
                routineBlocks = blocks,
                busyReplyBehavior = behavior,
                busyReplyDelayMinutes = delayMinutes.coerceIn(0, 120),
                busyStartHour = primaryBlock.startMinute / 60,
                busyDurationHours = (primaryBlock.durationMinutes / 60).coerceIn(4, 8),
                busyReason = primaryBlock.reason
            )
        } ?: return
        val index = friends.indexOfFirst { it.id == friend.id }
        if (index >= 0) friends[index] = updated
        selectedFriend = updated
        ConversationManager.refreshBusyReply(updated)
        ConversationWorkScheduler.scheduleMorningGreeting(context, updated, language)
    }

    androidx.compose.animation.AnimatedContent(
        targetState = screen,
        label = "pageTransition",
        transitionSpec = {
            val order = listOf(AppScreen.LANGUAGE_SETUP, AppScreen.SPLASH, AppScreen.AUTH, AppScreen.REGISTER, AppScreen.HOME, AppScreen.FRIEND_PROFILE, AppScreen.SETTINGS, AppScreen.PREMIUM, AppScreen.SYSTEM_FRIEND_PICKER, AppScreen.CHAT, AppScreen.GAMES, AppScreen.KNOWLEDGE)
            val forward = order.indexOf(targetState) >= order.indexOf(initialState)
            val slideIn = slideInHorizontally(animationSpec = tween(300)) { if (forward) it else -it }
            val slideOut = slideOutHorizontally(animationSpec = tween(300)) { if (forward) -it else it }
            slideIn.togetherWith(slideOut)
        }
    ) { currentScreen ->
    when (currentScreen) {
        AppScreen.LANGUAGE_SETUP -> LanguageSetupScreen(
            appLanguage = language,
            chatLanguage = chatLanguage,
            onAppLanguageChange = { language = it },
            onChatLanguageChange = { chatLanguage = it },
            onContinue = {
                store.saveLanguage(language)
                store.saveChatLanguage(chatLanguage)
                store.completeLanguageSetup()
                screen = AppScreen.SPLASH
            }
        )
        AppScreen.SPLASH -> DostSplashScreen(language, chatLanguage, onFinished = finishSplash@{
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
                        if (store.activateAccount(currentAccount.uid)) {
                            if (!restartForActiveProfile(context)) {
                                store.markAuthenticationRequired()
                                authRepository.signOut()
                                currentAccount = null
                                authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
                            } else {
                                return@finishSplash
                            }
                        } else {
                            store.markAuthenticationRequired()
                            authRepository.signOut()
                            currentAccount = null
                            authRequestState = AuthRequestState.Error(authenticationAccountMismatchText(language))
                        }
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
                    chatReturnScreen = AppScreen.HOME
                    screen = AppScreen.CHAT
                } else {
                    screen = destination
                }
            }
        })
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
            name = name,
            email = email,
            gender = gender,
            onNameChange = { name = it },
            onEmailChange = { email = it },
            onGenderChange = { gender = it },
            onFinish = finish@{
                if (name.isBlank() || email.isBlank()
                    || ageCohortForAge(age.toIntOrNull() ?: 0) == null
                    || gender.isBlank()
                    || authRequestState == AuthRequestState.Loading
                ) return@finish
                scope.launch {
                    authRequestState = AuthRequestState.Loading
                    try {
                        authRepository.currentAccount?.let {
                            authRepository.ensureAgeCohort(store.birthMonth(), store.birthYear())
                        }
                        store.saveUserProfile(name, email, age.trim(), gender, authRepository.currentAccount?.uid)
                        authRequestState = AuthRequestState.Idle
                        screen = AppScreen.HOME
                    } catch (error: CancellationException) {
                        authRequestState = AuthRequestState.Idle
                        throw error
                    } catch (error: Exception) {
                        authRequestState = AuthRequestState.Error(
                            error.message?.take(160) ?: "Could not save your age cohort."
                        )
                    }
                }
            },
            onBack = { screen = AppScreen.AUTH },
            emailEditable = false,
            requestMessage = (authRequestState as? AuthRequestState.Error)?.message,
            loading = authRequestState == AuthRequestState.Loading
        )
        AppScreen.HOME -> if (hasLocalAccountAccess()) MainNavigationScaffold(
            language = language,
            selectedTab = selectedMainTab,
            onSelectTab = { selectedMainTab = it }
        ) {
            when (selectedMainTab) {
                MainTab.CHATS -> HomeScreen(
                    language = language,
                    showFriendCount = showFriendCount,
                    isPremium = isPremium,
                    userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
                    friends = friends,
                    peopleConversations = peopleInboxConversations,
                    pinnedConversationIds = pinnedConversationIds,
                    mutedConversationIds = mutedConversationIds,
                    archivedConversationIds = archivedConversationIds,
                    peopleInboxLoading = peopleInboxLoading,
                    peopleInboxError = peopleInboxError,
                    searchQuery = homeInboxSearchQuery,
                    onSearchQueryChange = { homeInboxSearchQuery = it },
                    selectedFilter = homeInboxFilter,
                    onSelectedFilterChange = { homeInboxFilter = it },
                    recentlyAddedSystemFriend = recentlyAddedSystemFriend,
                    onDismissRecentlyAddedSystemFriend = { recentlyAddedSystemFriend = null },
                    onUndoSystemFriend = ::removeFriendPermanently,
                    listState = homeListState,
                    onBrowseSystemFriends = {
                        selectedMainTab = MainTab.DISCOVER
                    },
                    onOpenPeople = {
                        selectedMainTab = MainTab.DISCOVER
                        withAdultEligibility { screen = AppScreen.PEOPLE }
                    },
                    onRetryPeopleInbox = { peopleInboxRefreshKey++ },
                    onOpenPeopleConversation = {
                        withAdultEligibility {
                            selectedPeopleConversation = it
                            peopleChatReturnScreen = AppScreen.HOME
                            screen = AppScreen.PEOPLE_CHAT
                        }
                    },
                    onOpenGames = {
                        gameReturnScreen = AppScreen.HOME
                        screen = AppScreen.GAMES
                    },
                    onOpenDiscover = { selectedMainTab = MainTab.DISCOVER },
                    onOpenFriendProfile = { openFriendProfile(it, AppScreen.HOME) },
                    onDeleteFriend = ::removeFriendPermanently,
                    onToggleFavorite = { friend ->
                        val updated = store.updateFriend(friend.id) { it.copy(favorite = !it.favorite) }
                        val index = friends.indexOfFirst { it.id == friend.id }
                        if (updated != null && index >= 0) friends[index] = updated
                    },
                    onToggleConversationPinned = ::toggleConversationPinned,
                    onToggleConversationMuted = ::toggleConversationMuted,
                    onToggleConversationArchived = ::toggleConversationArchived,
                    onOpenChat = {
                        recentlyAddedSystemFriend = null
                        chatReturnScreen = AppScreen.HOME
                        selectedFriend = it
                        screen = AppScreen.CHAT
                    },
                    generatingProfileIds = generatingProfileIds,
                    profileFailedIds = profileErrors.keys,
                    peopleEnabled = BuildConfig.DOST_PEOPLE_ENABLED,
                    onOpenPremium = { screen = AppScreen.PREMIUM }
                )
                MainTab.DISCOVER -> DiscoverHomeScreen(
                    language = language,
                    peopleEnabled = BuildConfig.DOST_PEOPLE_ENABLED,
                    onOpenAiFriends = {
                        screen = AppScreen.SYSTEM_FRIEND_PICKER
                        loadSystemFriendCatalog()
                    },
                    onOpenPeople = { withAdultEligibility { screen = AppScreen.PEOPLE } }
                )
                MainTab.PROFILE -> ProfileHomeScreen(
                    language = language,
                    userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
                    email = authRepository.currentAccount?.email.orEmpty(),
                    superSecureEnabled = BuildConfig.DOST_SUPER_SECURE_ENABLED,
                    onOpenSuperSecure = ::openSuperSecure,
                    onOpenSettings = {
                        settingsReturnScreen = AppScreen.HOME
                        screen = AppScreen.SETTINGS
                    }
                )
            }
        }
        AppScreen.SETTINGS -> if (hasLocalAccountAccess()) SettingsScreen(
            language = language,
            chatLanguage = chatLanguage,
            showFriendCount = showFriendCount,
            friends = friends,
            userTimeZoneId = userTimeZoneId,
            signedInEmail = authRepository.currentAccount?.email,
            installationId = store.installationId(),
            openRouterApiKey = openRouterApiKey,
            aiModel = aiModel,
            anonymousTelemetryEnabled = anonymousTelemetryEnabled,
            aiNotificationPreviewsEnabled = aiNotificationPreviewsEnabled,
            proactiveEnabled = proactiveEnabled,
            proactiveCadenceMinutes = proactiveCadenceMinutes,
            automaticFollowUpMinutes = automaticFollowUpMinutes,
            quietHoursStartMinute = quietHoursStartMinute,
            quietHoursEndMinute = quietHoursEndMinute,
            settingsNotice = settingsNotice,
            onBack = { screen = settingsReturnScreen },
            onOpenPremium = { screen = AppScreen.PREMIUM },
            onDeleteFriend = ::removeFriendPermanently,
            onLanguageChange = {
                language = it
                store.saveLanguage(it)
                if (!store.hasCustomChatLanguage()) chatLanguage = it
                ConversationManager.refreshProactiveWork()
            },
            onChatLanguageChange = {
                chatLanguage = it
                store.saveChatLanguage(it)
            },
            onShowFriendCountChange = {
                showFriendCount = it
                store.setShowFriendCount(it)
            },
            onUserTimeZoneChange = {
                userTimeZoneId = it
                store.saveUserTimeZoneId(it)
                ConversationManager.refreshProactiveWork()
            },
            onAnonymousTelemetryChange = {
                store.setAnonymousTelemetryEnabled(it)
                anonymousTelemetryEnabled = it
            },
            onAiNotificationPreviewsChange = {
                store.setAiNotificationPreviewsEnabled(it)
                aiNotificationPreviewsEnabled = it
                if (!it) friends.forEach { friend -> ReplyNotifier.cancelReply(context, friend.id) }
            },
            onProactiveEnabledChange = {
                store.setProactiveEnabled(it)
                proactiveEnabled = it
                ConversationManager.refreshProactiveWork()
                if (!it) {
                    scope.launch {
                        AnonymousTelemetry.record(
                            store.anonymousTelemetryEnabled(),
                            AnonymousTelemetryEvent.PROACTIVE_MESSAGES_DISABLED
                        )
                    }
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    ReplyNotifier.ensureChannel(context)
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenNotificationSettings = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                }
                context.startActivity(intent)
            },
            onProactiveCadenceChange = {
                store.setProactiveCadenceMinutes(it)
                proactiveCadenceMinutes = it
                ConversationManager.refreshProactiveWork()
            },
            onAutomaticFollowUpChange = {
                store.setAutomaticFollowUpMinutes(it)
                automaticFollowUpMinutes = it
            },
            onQuietHoursChange = { start, end ->
                store.setQuietHours(start, end)
                quietHoursStartMinute = start
                quietHoursEndMinute = end
                ConversationManager.refreshProactiveWork()
            },
            onAiSettingsSave = { apiKey, model ->
                openRouterApiKey = apiKey
                aiModel = model
                store.saveOpenRouterApiKey(apiKey)
                store.saveAiModel(model)
            },
            onKnowledge = { knowledgeReturnScreen = AppScreen.SETTINGS; knowledgeFriendFilter = null; screen = AppScreen.KNOWLEDGE },
            onSignOut = {
                authRepository.signOut()
                store.markAuthenticationRequired()
                selectedFriend = null
                authMode = AuthMode.SIGN_IN
                authRequestState = AuthRequestState.Idle
                screen = AppScreen.AUTH
            },
            onCopyInstallationId = {
                val clipboard = checkNotNull(context.getSystemService(android.content.ClipboardManager::class.java)) {
                    "Clipboard is unavailable."
                }
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Dost installation ID", store.installationId())
                )
                settingsNotice = "Installation ID copied."
            },
            onExportLocalData = {
                backupPassword = ""
                backupPasswordConfirmation = ""
                backupPasswordError = ""
                backupPasswordMode = "export"
            },
            onImportLocalData = { backupImportLauncher.launch(arrayOf("application/json")) },
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
        AppScreen.PREMIUM -> if (hasLocalAccountAccess()) PremiumScreen(
            language = language,
            uid = activeFirebaseUid,
            isPremium = isPremium,
            authRepository = authRepository,
            onBack = { screen = AppScreen.SETTINGS },
            onEntitlementChanged = { isPremium = it }
        )
        AppScreen.SYSTEM_FRIEND_PICKER -> if (hasLocalAccountAccess()) SystemFriendPickerScreen(
            language = language,
            catalog = systemFriendCatalog,
            isLoading = systemFriendCatalogLoading,
            catalogError = systemFriendCatalogError,
            addError = systemFriendAddError,
            addedCatalogIds = friends.mapNotNull { it.catalogId.takeIf(String::isNotBlank) }.toSet(),
            passedCatalogIds = store.passedSystemFriendIds(),
            addingCatalogId = addingSystemFriendId,
            systemLimitReached = !isPremium && friends.count { it.catalogId.isNotBlank() } >= MAX_SYSTEM_FRIENDS,
            customLimitReached = !isPremium && friends.count { it.catalogId.isBlank() } >= MAX_CUSTOM_FRIENDS,
            onRefresh = ::loadSystemFriendCatalog,
            onSelect = ::addCatalogFriend,
            onPassCatalogFriend = {
                systemFriendAddError = null
                store.markSystemFriendPassed(it)
            },
            onStartOver = {
                systemFriendAddError = null
                store.clearPassedSystemFriends()
            },
            onCreateCustomFriend = {
                addFriendWithProfile(it)
                screen = AppScreen.HOME
            },
            onOpenPremium = { screen = AppScreen.PREMIUM },
            onManageFriends = {
                settingsReturnScreen = AppScreen.SYSTEM_FRIEND_PICKER
                screen = AppScreen.SETTINGS
            },
            onBack = { screen = AppScreen.HOME }
        )
        AppScreen.PEOPLE -> if (BuildConfig.DOST_PEOPLE_ENABLED && hasLocalAccountAccess()
            && (store.userAge().toIntOrNull() ?: 0) >= ROMANCE_MINIMUM_AGE
        ) PeopleScreen(
            language = language,
            uid = authRepository.currentAccount?.uid.orEmpty(),
            userName = name.ifBlank { store.userName().ifBlank { "Friend" } },
            userAge = age.ifBlank { store.userAge() },
            userGender = gender.ifBlank { store.userGender() },
            store = store,
            onBack = { screen = AppScreen.HOME; selectedMainTab = MainTab.DISCOVER },
            onOpenConversation = {
                withAdultEligibility {
                    selectedPeopleConversation = it
                    peopleChatReturnScreen = AppScreen.PEOPLE
                    screen = AppScreen.PEOPLE_CHAT
                }
            }
        ) else {
            selectedPeopleConversation = null
            screen = AppScreen.HOME
        }
        AppScreen.PEOPLE_CHAT -> if (BuildConfig.DOST_PEOPLE_ENABLED && hasLocalAccountAccess()
            && (store.userAge().toIntOrNull() ?: 0) >= ROMANCE_MINIMUM_AGE
        ) selectedPeopleConversation?.let { conversation ->
            PeopleChatScreen(
                language = language,
                uid = authRepository.currentAccount?.uid.orEmpty(),
                conversation = conversation,
                onBack = { screen = peopleChatReturnScreen }
            )
        } ?: run { screen = AppScreen.PEOPLE } else {
            selectedPeopleConversation = null
            screen = AppScreen.HOME
        }
        AppScreen.SUPER_SECURE -> if (BuildConfig.DOST_SUPER_SECURE_ENABLED && hasLocalAccountAccess()) {
            SuperSecureScreen(
                context = context,
                language = language,
                uid = activeFirebaseUid,
                displayName = name.ifBlank { store.userName() },
                onBack = {
                    closeSuperSecure()
                }
            )
        } else {
            SuperSecureSession.active = false
            screen = AppScreen.HOME
        }
        AppScreen.FRIEND_PROFILE -> if (hasLocalAccountAccess()) selectedFriend?.let { friend ->
            FriendProfileScreen(
                friend = friend,
                language = language,
                userAge = store.userAge(),
                userTimeZoneId = store.userTimeZoneId(),
                memoryCount = store.loadUserMemories(friend.id).size,
                plannedEvents = store.loadPlannedEvents(friend.id),
                sharedMoments = store.loadSharedMoments(friend.id),
                anonymousTelemetryEnabled = anonymousTelemetryEnabled,
                proactivePausedUntil = store.proactivePausedUntil(friend.id),
                onBack = { screen = friendProfileReturnScreen },
                onOpenMemories = {
                    knowledgeReturnScreen = AppScreen.FRIEND_PROFILE
                    knowledgeFriendFilter = friend.id
                    screen = AppScreen.KNOWLEDGE
                },
                onTimeZoneChange = { updateFriendTimeZone(friend, it) },
                onSaveSchedule = { blocks, behavior, delayMinutes ->
                    updateFriendSchedule(friend, blocks, behavior, delayMinutes)
                },
                onSaveReminder = { event ->
                    if (event.state == PlannedEventState.COMPLETED) {
                        store.completePlannedEvent(friend.id, event.id)
                    } else {
                        store.savePlannedEvent(friend.id, event)
                    }
                    if (event.state == PlannedEventState.UPCOMING && event.followUpRequested) {
                        ConversationWorkScheduler.scheduleUserReminder(context, friend, event, language)
                    } else {
                        ConversationWorkScheduler.cancelUserReminder(context, event.id)
                    }
                },
                onSaveMoment = { store.saveSharedMoment(friend.id, it) },
                onDeleteMoment = { store.deleteSharedMoment(friend.id, it) },
                onPauseProactive = {
                    ConversationManager.pauseFriendProactive(friend.id, it)
                    scope.launch {
                        AnonymousTelemetry.record(
                            store.anonymousTelemetryEnabled(),
                            AnonymousTelemetryEvent.PROACTIVE_MESSAGES_PAUSED
                        )
                    }
                },
                onResumeProactive = { ConversationManager.resumeFriendProactive(friend.id) }
            )
        }
        AppScreen.CHAT -> if (hasLocalAccountAccess()) selectedFriend?.let { friend ->
            ChatScreen(
                friend = friend,
                store = store,
                onBack = { screen = chatReturnScreen },
                language = language,
                chatLanguage = chatLanguage,
                initialDraft = appViewModel.chatDrafts[friend.id].orEmpty(),
                initialImagePath = appViewModel.chatImageDrafts[friend.id].orEmpty(),
                onDraftChange = { appViewModel.chatDrafts[friend.id] = it },
                onImageDraftChange = { path ->
                    if (path.isBlank()) appViewModel.chatImageDrafts.remove(friend.id)
                    else appViewModel.chatImageDrafts[friend.id] = path
                },
                onFriendProfile = { openFriendProfile(friend, AppScreen.CHAT) },
                onOpenMemories = {
                    knowledgeReturnScreen = AppScreen.CHAT
                    knowledgeFriendFilter = friend.id
                    screen = AppScreen.KNOWLEDGE
                },
                onChatLanguageChange = {
                    chatLanguage = it
                    store.saveChatLanguage(it)
                },
                onOpenGames = {
                    gameReturnScreen = AppScreen.CHAT
                    screen = AppScreen.GAMES
                },
                profileGenerating = friend.id in generatingProfileIds,
                profileError = profileErrors[friend.id],
                onRetryProfile = { startProfileGeneration(friend.id) },
                onUseBasicProfile = { continueWithBasicProfile(friend.id) },
                onFloatingChat = { requestFloatingChat(friend.id, closeApp = true) },
                onFriendTimeZoneChange = { updateFriendTimeZone(friend, it) }
            )
        }
        AppScreen.GAMES -> if (hasLocalAccountAccess()) MiniGamesScreen(
            language = language,
            onBack = { screen = gameReturnScreen }
        )
        AppScreen.KNOWLEDGE -> if (hasLocalAccountAccess()) KnowledgeScreen(
            language = language,
            friends = friends,
            store = store,
            listState = knowledgeListState,
            friendIdFilter = knowledgeFriendFilter,
            onBack = { screen = knowledgeReturnScreen },
            onOpenChat = { chatReturnScreen = AppScreen.KNOWLEDGE; selectedFriend = it; screen = AppScreen.CHAT }
        )
    }
    }
    if (backupPasswordMode != null) {
        val exporting = backupPasswordMode == "export"
        BackupPassphraseDialog(
            exporting = exporting,
            password = backupPassword,
            confirmation = backupPasswordConfirmation,
            error = backupPasswordError,
            onPasswordChange = { backupPassword = it },
            onConfirmationChange = { backupPasswordConfirmation = it },
            onDismiss = {
                backupPasswordMode = null
                backupPassword = ""
                backupPasswordConfirmation = ""
                backupPasswordError = ""
                pendingEncryptedBackup = null
            },
            onConfirm = {
                backupPasswordError = ""
                if (exporting) {
                    pendingExportPassword = backupPassword
                    backupPasswordMode = null
                    backupExportLauncher.launch("dost-encrypted-backup.json")
                } else {
                    val backup = pendingEncryptedBackup
                    if (backup != null) {
                        val password = backupPassword.toCharArray()
                        scope.launch {
                            try {
                                val decrypted = withContext(Dispatchers.IO) {
                                    LocalBackupCrypto.decrypt(backup, password)
                                }
                                require(decrypted.length <= MAX_LOCAL_BACKUP_CHARS) { "The backup is too large to import." }
                                importBackupText(decrypted)
                                backupPasswordMode = null
                                pendingEncryptedBackup = null
                                backupPassword = ""
                                backupPasswordConfirmation = ""
                            } catch (error: Exception) {
                                backupPasswordError = error.message?.take(160) ?: "The encrypted backup could not be opened."
                            } finally {
                                password.fill('\u0000')
                            }
                        }
                    }
                }
            }
        )
    }
    if (checkingPeopleEligibility || peopleEligibilityError != null) {
        val error = peopleEligibilityError
        val title = when (language) {
            AppLanguage.BANGLA -> if (checkingPeopleEligibility) "অ্যাক্সেস যাচাই" else "যাচাই করা যায়নি"
            AppLanguage.HINDI -> if (checkingPeopleEligibility) "पहुंच की जांच" else "जांच पूरी नहीं हुई"
            AppLanguage.ENGLISH -> if (checkingPeopleEligibility) "Checking access" else "Access check failed"
        }
        val message = when {
            checkingPeopleEligibility -> when (language) {
                AppLanguage.BANGLA -> "নিরাপদে Friends ব্যবহার করার জন্য Google Play-এর সাথে সংযোগ করা হচ্ছে।"
                AppLanguage.HINDI -> "People को सुरक्षित रूप से उपयोग करने के लिए Google Play से जुड़ा जा रहा है।"
                AppLanguage.ENGLISH -> "Connecting to Google Play to verify access to People."
            }
            else -> when (language) {
                AppLanguage.BANGLA -> "ইন্টারনেট সংযোগ এবং Play Store থেকে ইনস্টল করা অ্যাপটি পরীক্ষা করে আবার চেষ্টা করুন।"
                AppLanguage.HINDI -> "इंटरनेट कनेक्शन और Play Store से इंस्टॉल किए गए ऐप की जांच करके फिर कोशिश करें।"
                AppLanguage.ENGLISH -> "Check your connection and make sure this app was installed from Google Play, then try again."
            } + (error?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
        }
        AlertDialog(
            onDismissRequest = {
                if (!checkingPeopleEligibility) peopleEligibilityError = null
            },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                if (!checkingPeopleEligibility) {
                    TextButton(onClick = { peopleEligibilityError = null }) {
                        Text(
                            when (language) {
                                AppLanguage.BANGLA -> "ঠিক আছে"
                                AppLanguage.HINDI -> "ठीक है"
                                AppLanguage.ENGLISH -> "OK"
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun BackupPassphraseDialog(
    exporting: Boolean,
    password: String,
    confirmation: String,
    error: String,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val validPassword = password.length in 12..256
    val validConfirmation = !exporting || password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exporting) "Protect your backup" else "Unlock your backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (exporting) {
                        "Choose a passphrase with at least 12 characters. You will need it to restore this file; Dost cannot recover it."
                    } else {
                        "Enter the passphrase used when this backup was created. Importing replaces the current local data."
                    }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it.take(256)) },
                    label = { Text(if (exporting) "Passphrase" else "Backup passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (exporting) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { onConfirmationChange(it.take(256)) },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                when {
                    error.isNotBlank() -> Text(error)
                    !validPassword -> Text("Use 12 to 256 characters.")
                    !validConfirmation -> Text("The passphrases do not match.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = validPassword && validConfirmation) {
                Text(if (exporting) "Create encrypted backup" else "Unlock and import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

private const val MAX_LOCAL_BACKUP_CHARS = 35_000_000

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DostTheme { DostApp() }
}
