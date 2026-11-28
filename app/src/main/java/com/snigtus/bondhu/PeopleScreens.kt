@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.snigtus.dost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class PeopleTab { DISCOVER, CHATS, GROUPS, REQUESTS, PROFILE }

@Composable
internal fun PeopleScreen(
    language: AppLanguage,
    uid: String,
    userName: String,
    userAge: String,
    userGender: String,
    store: DostStore,
    circleMode: Boolean = false,
    onBack: () -> Unit,
    onOpenConversation: (SocialConversation) -> Unit,
    onOpenGroup: (CircleGroup) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    val cohort = socialAgeCohort(userAge)
    val teenCircle = circleMode && userAge.toIntOrNull()?.let { it in MINIMUM_ACCOUNT_AGE until ROMANCE_MINIMUM_AGE } == true
    var tab by rememberSaveable(uid, circleMode, userAge) {
        mutableStateOf(if (teenCircle) PeopleTab.GROUPS else if (circleMode) PeopleTab.CHATS else PeopleTab.DISCOVER)
    }
    var ownProfile by remember(uid) { mutableStateOf<PublicPersonProfile?>(null) }
    val savedDraft = remember(uid) { store.peopleProfileDraft() }
    var country by rememberSaveable(uid) { mutableStateOf(savedDraft.country) }
    var bio by rememberSaveable(uid) { mutableStateOf(savedDraft.bio) }
    var interests by rememberSaveable(uid) { mutableStateOf(savedDraft.interests) }
    var peopleInterestFilter by rememberSaveable(uid) { mutableStateOf("") }
    var contactPolicy by rememberSaveable(uid) { mutableStateOf(PeopleContactPolicy.EVERYONE) }
    var discovered by remember(uid) { mutableStateOf(emptyList<PublicPersonProfile>()) }
    var conversations by remember(uid) { mutableStateOf(emptyList<SocialConversation>()) }
    var contactRequests by remember(uid) { mutableStateOf(emptyList<SocialContactRequest>()) }
    var outgoingRequests by remember(uid) { mutableStateOf(emptyList<SocialOutgoingContactRequest>()) }
    var circleGroups by remember(uid) { mutableStateOf(emptyList<CircleGroup>()) }
    var circleGroupInvites by remember(uid) { mutableStateOf(emptyList<Pair<String, String>>()) }
    var blockedIds by remember(uid) { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var loadingProfile by remember { mutableStateOf(true) }
    var loadingPeople by remember { mutableStateOf(false) }
    var loadingRequests by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf("") }
    var fatalError by remember { mutableStateOf("") }
    var deleteProfileConfirmation by remember { mutableStateOf(false) }
    var blockRequestConfirmation by remember { mutableStateOf<SocialContactRequest?>(null) }
    var reportRequest by remember { mutableStateOf<SocialContactRequest?>(null) }
    var requestReportReason by rememberSaveable { mutableStateOf("") }
    var reportSubmitError by remember { mutableStateOf("") }
    var blockProfileConfirmation by remember { mutableStateOf<PublicPersonProfile?>(null) }
    var reportProfile by remember { mutableStateOf<PublicPersonProfile?>(null) }
    var profileReportReason by rememberSaveable(uid) { mutableStateOf("") }
    var callAlertsEnabled by remember(uid) {
        mutableStateOf(
            store.peopleCallAlertsEnabled(ReplyNotifier.canNotify(context))
                && ReplyNotifier.canNotify(context)
        )
    }
    var messageNotificationsEnabled by remember(uid) {
        mutableStateOf(store.peopleMessageNotificationsEnabled())
    }
    var notificationPermissionRequested by rememberSaveable(uid) { mutableStateOf(false) }
    var pendingCallAlertEnable by rememberSaveable(uid) { mutableStateOf(false) }
    var pendingMessageNotificationEnable by rememberSaveable(uid) { mutableStateOf(false) }

    fun registerCallTokenIfAllowed() {
        if (uid.isBlank() || !ReplyNotifier.canNotify(context)) return
        callAlertsEnabled = true
        store.setPeopleCallAlertsEnabled(true)
        scope.launch {
            try {
                repository.messagingToken(uid)
                notice = ""
            } catch (error: Exception) {
                notice = peopleFailureMessage(error, language, socialLabel(
                    language,
                    "Could not enable incoming call alerts on this device.",
                    "এই ডিভাইসে ইনকামিং কলের সতর্কতা চালু করা যায়নি।",
                    "इस डिवाइस पर इनकमिंग कॉल अलर्ट चालू नहीं हो सके।"
                ))
            }
        }
    }

    fun registerMessageTokenIfAllowed() {
        if (uid.isBlank() || !messageNotificationsEnabled || !ReplyNotifier.canNotify(context)) return
        scope.launch {
            try {
                repository.peopleMessageMessagingToken(uid)
                notice = ""
            } catch (error: Exception) {
                notice = peopleFailureMessage(error, language, socialLabel(
                    language,
                    "Could not enable People message notifications on this device.",
                    "এই ডিভাইসে People বার্তার নোটিফিকেশন চালু করা যায়নি।",
                    "इस डिवाइस पर People संदेश सूचनाएँ चालू नहीं हो सकीं।"
                ))
            }
        }
    }

    fun removeMessageToken() {
        if (uid.isBlank()) return
        scope.launch {
            try {
                repository.removePeopleMessageToken(uid)
            } catch (error: Exception) {
                notice = peopleFailureMessage(error, language, socialLabel(
                    language,
                    "Could not disable People message notifications on this device.",
                    "এই ডিভাইসে People বার্তার নোটিফিকেশন বন্ধ করা যায়নি।",
                    "इस डिवाइस पर People संदेश सूचनाएँ बंद नहीं हो सकीं।"
                ))
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionRequested = true
        val notificationsAllowed = granted && ReplyNotifier.canNotify(context)
        if (pendingCallAlertEnable) {
            pendingCallAlertEnable = false
            callAlertsEnabled = notificationsAllowed
            if (notificationsAllowed) registerCallTokenIfAllowed()
        }
        if (pendingMessageNotificationEnable) {
            pendingMessageNotificationEnable = false
            if (notificationsAllowed) {
                if (!store.hasPeopleCallAlertsChoice()) {
                    store.setPeopleCallAlertsEnabled(callAlertsEnabled)
                }
                messageNotificationsEnabled = true
                store.setPeopleMessageNotificationsEnabled(true)
                registerMessageTokenIfAllowed()
            } else {
                messageNotificationsEnabled = false
                notice = socialLabel(
                    language,
                    "Notification permission is off, so message alerts remain disabled.",
                    "নোটিফিকেশনের অনুমতি বন্ধ, তাই বার্তার সতর্কতা চালু হয়নি।",
                    "सूचना की अनुमति बंद है, इसलिए संदेश अलर्ट बंद ही रहेंगे।"
                )
            }
        }
    }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val notificationsAllowed = ReplyNotifier.canNotify(context)
        if (pendingCallAlertEnable) {
            pendingCallAlertEnable = false
            callAlertsEnabled = notificationsAllowed
            if (notificationsAllowed) registerCallTokenIfAllowed()
        } else {
            callAlertsEnabled = store.peopleCallAlertsEnabled(callAlertsEnabled) && notificationsAllowed
        }
        if (pendingMessageNotificationEnable) {
            pendingMessageNotificationEnable = false
            if (notificationsAllowed) {
                if (!store.hasPeopleCallAlertsChoice()) {
                    store.setPeopleCallAlertsEnabled(callAlertsEnabled)
                }
                messageNotificationsEnabled = true
                store.setPeopleMessageNotificationsEnabled(true)
                registerMessageTokenIfAllowed()
            } else {
                messageNotificationsEnabled = false
                notice = socialLabel(
                    language,
                    "Notification permission is off, so message alerts remain disabled.",
                    "নোটিফিকেশনের অনুমতি বন্ধ, তাই বার্তার সতর্কতা চালু হয়নি।",
                    "सूचना की अनुमति बंद है, इसलिए संदेश अलर्ट बंद ही रहेंगे।"
                )
            }
        } else if (messageNotificationsEnabled) {
            if (notificationsAllowed) registerMessageTokenIfAllowed() else removeMessageToken()
        }
        if (callAlertsEnabled) registerCallTokenIfAllowed()
    }

    fun requestCallAlerts() {
        pendingCallAlertEnable = true
        if (ReplyNotifier.canNotify(context)) {
            pendingCallAlertEnable = false
            registerCallTokenIfAllowed()
            return
        }
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission && !notificationPermissionRequested) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            }
            notificationSettingsLauncher.launch(settingsIntent)
        }
    }

    fun setMessageNotifications(enabled: Boolean) {
        if (enabled == messageNotificationsEnabled) return
        if (!enabled) {
            messageNotificationsEnabled = false
            store.setPeopleMessageNotificationsEnabled(false)
            removeMessageToken()
            return
        }
        if (!store.hasPeopleCallAlertsChoice()) {
            store.setPeopleCallAlertsEnabled(callAlertsEnabled)
        }
        if (ReplyNotifier.canNotify(context)) {
            messageNotificationsEnabled = true
            store.setPeopleMessageNotificationsEnabled(true)
            registerMessageTokenIfAllowed()
            return
        }
        pendingMessageNotificationEnable = true
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission && !notificationPermissionRequested) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            }
            notificationSettingsLauncher.launch(settingsIntent)
        }
    }
    val isPublic = ownProfile?.isPublic == true
    val currentProfile = ownProfile ?: newPublicProfile(
        uid = uid,
        userName = userName,
        userAge = userAge,
        userGender = userGender,
        country = country,
        bio = bio,
        interests = interests,
        isPublic = false,
        contactPolicy = contactPolicy
    )

    LaunchedEffect(uid, userName, userAge, userGender) {
        if (uid.isBlank()) {
            fatalError = socialLabel(language, "Sign in to a Firebase account to use People.", "People ব্যবহার করতে Firebase অ্যাকাউন্টে সাইন ইন করুন।", "People इस्तेमाल करने के लिए Firebase खाते में साइन इन करें।")
            loadingProfile = false
            return@LaunchedEffect
        }
        if (teenCircle) {
            ownProfile = null
            blockedIds = emptySet()
            loadingProfile = false
            fatalError = ""
            return@LaunchedEffect
        }
        try {
            ownProfile = repository.getOwnProfile(uid)
            ownProfile?.let {
                country = it.country
                bio = it.bio
                interests = it.interests
                contactPolicy = it.contactPolicy
            }
            blockedIds = repository.blockedIds(uid)
            fatalError = ""
        } catch (error: Exception) {
            fatalError = peopleFailureMessage(
                error,
                language,
                socialLabel(language, "Could not load your profile.", "আপনার প্রোফাইল লোড করা যায়নি।", "आपकी प्रोफ़ाइल लोड नहीं हो सकी।")
            )
        } finally {
            loadingProfile = false
        }
    }

    LaunchedEffect(uid) {
        if (uid.isBlank() || teenCircle) return@LaunchedEffect
        val notificationsAllowed = ReplyNotifier.canNotify(context)
        callAlertsEnabled = store.peopleCallAlertsEnabled(notificationsAllowed) && notificationsAllowed
        if (callAlertsEnabled) {
            try {
                repository.messagingToken(uid)
            } catch (error: Exception) {
                notice = peopleFailureMessage(error, language, socialLabel(
                    language,
                    "Could not enable incoming call alerts on this device.",
                    "এই ডিভাইসে ইনকামিং কলের সতর্কতা চালু করা যায়নি।",
                    "इस डिवाइस पर इनकमिंग कॉल अलर्ट चालू नहीं हो सके।"
                ))
            }
        }
        if (messageNotificationsEnabled) {
            if (notificationsAllowed) {
                try {
                    repository.peopleMessageMessagingToken(uid)
                } catch (error: Exception) {
                    notice = peopleFailureMessage(error, language, socialLabel(
                        language,
                        "Could not enable People message notifications on this device.",
                        "এই ডিভাইসে People বার্তার নোটিফিকেশন চালু করা যায়নি।",
                        "इस डिवाइस पर People संदेश सूचनाएँ चालू नहीं हो सकीं।"
                    ))
                }
            } else {
                removeMessageToken()
            }
        }
    }

    LaunchedEffect(country, bio, interests) {
        store.savePeopleProfileDraft(PeopleProfileDraft(country, bio, interests))
    }

    LaunchedEffect(tab, refreshKey, isPublic, uid, cohort) {
        if (tab != PeopleTab.DISCOVER || !isPublic || uid.isBlank() || cohort == null) return@LaunchedEffect
        loadingPeople = true
        try {
            discovered = repository.discover(uid, cohort)
            fatalError = ""
        } catch (error: Exception) {
            fatalError = peopleFailureMessage(
                error,
                language,
                socialLabel(language, "Could not load people.", "মানুষের তালিকা লোড করা যায়নি।", "लोगों की सूची लोड नहीं हो सकी।")
            )
        } finally {
            loadingPeople = false
        }
    }

    DisposableEffect(uid, tab) {
        if ((tab != PeopleTab.CHATS && tab != PeopleTab.DISCOVER) || uid.isBlank()) {
            onDispose { }
        } else {
            val registration = try {
                repository.listenConversations(
                    uid,
                    onChange = { conversations = it },
                    onError = {
                        fatalError = peopleFailureMessage(
                            it,
                            language,
                            socialLabel(language, "Could not load conversations.", "কথোপকথন লোড করা যায়নি।", "बातचीत लोड नहीं हो सकी।")
                        )
                    }
                )
            } catch (error: Exception) {
                fatalError = peopleFailureMessage(
                    error,
                    language,
                    socialLabel(language, "Could not load conversations.", "কথোপকথন লোড করা যায়নি।", "बातचीत लोड नहीं हो सकी।")
                )
                null
            }
            onDispose { registration?.remove() }
        }
    }

    DisposableEffect(uid, tab) {
        if ((tab != PeopleTab.REQUESTS && tab != PeopleTab.DISCOVER) || uid.isBlank()) {
            onDispose { }
        } else {
            if (tab == PeopleTab.REQUESTS) loadingRequests = true
            val registration = try {
                repository.listenIncomingRequests(
                    uid,
                    onChange = {
                        contactRequests = it
                        if (tab == PeopleTab.REQUESTS) loadingRequests = false
                    },
                    onError = {
                        fatalError = peopleFailureMessage(
                            it,
                            language,
                            socialLabel(language, "Could not load contact requests.", "যোগাযোগের অনুরোধ লোড করা যায়নি।", "संपर्क अनुरोध लोड नहीं हो सके।")
                        )
                        if (tab == PeopleTab.REQUESTS) loadingRequests = false
                    }
                )
            } catch (error: Exception) {
                fatalError = peopleFailureMessage(
                    error,
                    language,
                    socialLabel(language, "Could not load contact requests.", "যোগাযোগের অনুরোধ লোড করা যায়নি।", "संपर्क अनुरोध लोड नहीं हो सके।")
                )
                if (tab == PeopleTab.REQUESTS) loadingRequests = false
                null
            }
            onDispose { registration?.remove() }
        }
    }

    DisposableEffect(uid, tab) {
        if (tab != PeopleTab.GROUPS || uid.isBlank()) {
            onDispose { }
        } else {
            val groupsRegistration = try {
                repository.listenCircleGroups(
                    uid,
                    onChange = { circleGroups = it },
                    onError = {
                        fatalError = peopleFailureMessage(
                            it,
                            language,
                            socialLabel(language, "Could not load groups.", "গ্রুপ লোড করা যায়নি।", "समूह लोड नहीं हो सके।")
                        )
                    }
                )
            } catch (error: Exception) {
                fatalError = peopleFailureMessage(
                    error,
                    language,
                    socialLabel(language, "Could not load groups.", "গ্রুপ লোড করা যায়নি।", "समूह लोड नहीं हो सके।")
                )
                null
            }
            val invitesRegistration = try {
                repository.listenCircleGroupInvites(
                    uid,
                    onChange = { circleGroupInvites = it },
                    onError = {
                        fatalError = peopleFailureMessage(
                            it,
                            language,
                            socialLabel(language, "Could not load group invitations.", "গ্রুপের আমন্ত্রণ লোড করা যায়নি।", "समूह आमंत्रण लोड नहीं हो सके।")
                        )
                    }
                )
            } catch (error: Exception) {
                fatalError = peopleFailureMessage(
                    error,
                    language,
                    socialLabel(language, "Could not load group invitations.", "গ্রুপের আমন্ত্রণ লোড করা যায়নি।", "समूह आमंत्रण लोड नहीं हो सके।")
                )
                null
            }
            onDispose {
                groupsRegistration?.remove()
                invitesRegistration?.remove()
            }
        }
    }

    DisposableEffect(uid, tab) {
        if (tab != PeopleTab.DISCOVER || uid.isBlank()) {
            onDispose { }
        } else {
            val registration = try {
                repository.listenOutgoingRequests(
                    uid,
                    onChange = { outgoingRequests = it },
                    onError = {
                        fatalError = peopleFailureMessage(it, language, socialLabel(
                            language,
                            "Could not load sent contact requests.",
                            "পাঠানো যোগাযোগের অনুরোধ লোড করা যায়নি।",
                            "भेजे गए संपर्क अनुरोध लोड नहीं हो सके।"
                        ))
                    }
                )
            } catch (error: Exception) {
                fatalError = peopleFailureMessage(error, language, socialLabel(
                    language,
                    "Could not load sent contact requests.",
                    "পাঠানো যোগাযোগের অনুরোধ লোড করা যায়নি।",
                    "भेजे गए संपर्क अनुरोध लोड नहीं हो सके।"
                ))
                null
            }
            onDispose { registration?.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (circleMode) "Circle" else socialLabel(language, "People", "মানুষ", "लोग")
                    )
                },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    if (tab == PeopleTab.DISCOVER && isPublic) {
                        IconButton(onClick = { refreshKey++ }, enabled = !loadingPeople) {
                            Icon(Icons.Filled.Refresh, contentDescription = socialLabel(language, "Refresh people", "মানুষের তালিকা রিফ্রেশ করুন", "लोगों की सूची रीफ़्रेश करें"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PeopleTab.values().filterNot {
                    (!circleMode && it == PeopleTab.GROUPS)
                        || (circleMode && (it == PeopleTab.DISCOVER || (teenCircle && it != PeopleTab.GROUPS)))
                }.forEach { item ->
                    FilterChip(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = {
                            Text(when (item) {
                                PeopleTab.DISCOVER -> socialLabel(language, "Discover", "খুঁজুন", "खोजें")
                                PeopleTab.CHATS -> socialLabel(language, "Chats", "চ্যাট", "चैट")
                                PeopleTab.GROUPS -> socialLabel(language, "Groups", "গ্রুপ", "समूह")
                                PeopleTab.REQUESTS -> socialLabel(language, "Requests", "অনুরোধ", "अनुरोध")
                                PeopleTab.PROFILE -> socialLabel(language, "My profile", "আমার প্রোফাইল", "मेरी प्रोफ़ाइल")
                            })
                        }
                    )
                }
            }
            if (fatalError.isNotBlank()) {
                Text(
                    fatalError,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            notice.takeIf(String::isNotBlank)?.let {
                Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
            }
            if (!callAlertsEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            socialLabel(
                                language,
                                "Incoming call alerts are optional. Enable them if you want notifications while Dost is closed.",
                                "ইনকামিং কলের সতর্কতা ঐচ্ছিক। Dost বন্ধ থাকলেও নোটিফিকেশন চাইলে চালু করুন।",
                                "इनकमिंग कॉल अलर्ट वैकल्पिक हैं। Dost बंद होने पर सूचना चाहिए तो इन्हें चालू करें।"
                            ),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = ::requestCallAlerts
                        ) {
                            Text(socialLabel(language, "Enable", "চালু করুন", "चालू करें"))
                        }
                    }
                }
            }
            when (tab) {
                PeopleTab.PROFILE -> PeopleProfileEditor(
                    language = language,
                    userName = userName,
                    userAge = userAge,
                    userGender = userGender,
                    country = country,
                    onCountryChange = { country = it.take(80) },
                    bio = bio,
                    onBioChange = { bio = it.take(500) },
                    interests = interests,
                    onInterestsChange = { interests = it.take(300) },
                    contactPolicy = contactPolicy,
                    onContactPolicyChange = { contactPolicy = it },
                    messageNotificationsEnabled = messageNotificationsEnabled,
                    onMessageNotificationsChange = ::setMessageNotifications,
                    isPublic = isPublic,
                    loading = loadingProfile,
                    busy = busy,
                    onPublish = {
                        if (cohort == null || uid.isBlank()) {
                            fatalError = socialLabel(language, "A valid account age and Firebase sign-in are required.", "সঠিক অ্যাকাউন্ট বয়স ও Firebase সাইন-ইন প্রয়োজন।", "सही खाता आयु और Firebase साइन-इन आवश्यक है।")
                        } else if (country.isBlank()) {
                            fatalError = socialLabel(language, "Enter your country before publishing.", "প্রকাশের আগে আপনার দেশ লিখুন।", "प्रकाशित करने से पहले अपना देश लिखें।")
                        } else {
                            busy = true
                            fatalError = ""
                            notice = ""
                            scope.launch {
                                try {
                                    val profile = newPublicProfile(
                                        uid, userName, userAge, userGender, country.trim(),
                                        bio.trim(), interests.trim(), true, contactPolicy
                                    )
                                    repository.saveProfile(profile)
                                    ownProfile = profile
                                    notice = socialLabel(language, "Your profile is public to eligible adults.", "আপনার প্রোফাইল যোগ্য প্রাপ্তবয়স্কদের কাছে প্রকাশিত হয়েছে।", "आपकी प्रोफ़ाइल पात्र वयस्कों को दिख रही है।")
                                } catch (error: Exception) {
                                    fatalError = peopleFailureMessage(
                                        error,
                                        language,
                                        socialLabel(language, "Could not publish your profile.", "প্রোফাইল প্রকাশ করা যায়নি।", "प्रोफ़ाइल प्रकाशित नहीं हो सकी।")
                                    )
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onHide = {
                        val existing = ownProfile ?: return@PeopleProfileEditor
                        busy = true
                        scope.launch {
                            try {
                                val hidden = existing.copy(isPublic = false)
                                repository.saveProfile(hidden)
                                ownProfile = hidden
                                notice = socialLabel(language, "Your profile is hidden from discovery. Existing chats remain.", "আপনার প্রোফাইল খোঁজার তালিকা থেকে লুকানো হয়েছে। আগের চ্যাট থাকবে।", "आपकी प्रोफ़ाइल खोज से छिपी है। मौजूदा चैट बनी रहेंगी।")
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not hide your profile.", "প্রোফাইল লুকানো যায়নি।", "प्रोफ़ाइल छिपाई नहीं जा सकी।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onDelete = { deleteProfileConfirmation = true }
                )
                PeopleTab.DISCOVER -> when {
                    loadingProfile || loadingPeople -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    cohort == null -> EmptyPeopleState(
                        language,
                        socialLabel(language, "An adult account is required.", "১৮ বছর বা তার বেশি বয়সের অ্যাকাউন্ট প্রয়োজন।", "18 वर्ष या उससे अधिक आयु का खाता आवश्यक है।")
                    )
                    !isPublic -> EmptyPeopleState(
                        language,
                        socialLabel(language, "Publish your profile before discovering people or starting a chat.", "মানুষ খোঁজা বা চ্যাট শুরু করার আগে আপনার প্রোফাইল প্রকাশ করুন।", "लोगों को खोजने या चैट शुरू करने से पहले अपनी प्रोफ़ाइल प्रकाशित करें।"),
                        onProfile = { tab = PeopleTab.PROFILE }
                    )
                    discovered.isEmpty() -> EmptyPeopleState(
                        language,
                        socialLabel(language, "No public adult profiles are available yet.", "এখনো কোনো প্রকাশিত প্রাপ্তবয়স্কদের প্রোফাইল নেই।", "अभी कोई सार्वजनिक वयस्क प्रोफ़ाइल उपलब्ध नहीं है।")
                    )
                    else -> PeopleSwipeDeck(
                        people = discovered,
                        conversations = conversations,
                        incomingRequests = contactRequests,
                        outgoingRequests = outgoingRequests,
                        language = language,
                        store = store,
                        uid = uid,
                        enabled = !busy,
                        interestQuery = peopleInterestFilter,
                        onInterestQueryChange = { peopleInterestFilter = it },
                        onOpenConversation = onOpenConversation,
                        onOpenRequests = { tab = PeopleTab.REQUESTS },
                        onOpenChats = { tab = PeopleTab.CHATS },
                        onBlockProfile = { blockProfileConfirmation = it },
                        onReportProfile = {
                            profileReportReason = ""
                            reportSubmitError = ""
                            reportProfile = it
                        },
                        onSelect = { person ->
                            busy = true
                            fatalError = ""
                            scope.launch {
                                try {
                                    when (val result = repository.startConversationOrRequest(uid, currentProfile, person)) {
                                        is PeopleContactStartResult.Conversation -> onOpenConversation(result.value)
                                        PeopleContactStartResult.RequestSent -> {
                                            outgoingRequests = (outgoingRequests.filterNot {
                                                it.recipientUid == person.uid
                                            } + SocialOutgoingContactRequest(
                                                person.uid,
                                                SocialContactRequestStatus.PENDING
                                            ))
                                            notice = socialLabel(
                                                language,
                                                if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                    "Interest sent. If they accept, you can match and start chatting."
                                                } else {
                                                    "Contact request sent."
                                                },
                                                if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                    "আগ্রহ জানানো হয়েছে। তারা গ্রহণ করলে ম্যাচ হয়ে চ্যাট শুরু করতে পারবেন।"
                                                } else {
                                                    "যোগাযোগের অনুরোধ পাঠানো হয়েছে।"
                                                },
                                                if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                    "रुचि भेजी गई। वे स्वीकार करें तो आप मैच होकर चैट शुरू कर सकते हैं।"
                                                } else {
                                                    "संपर्क अनुरोध भेज दिया गया।"
                                                }
                                            )
                                        }
                                        is PeopleContactStartResult.ExistingRequest -> {
                                            outgoingRequests = (outgoingRequests.filterNot {
                                                it.recipientUid == person.uid
                                            } + SocialOutgoingContactRequest(person.uid, result.status))
                                            notice = when (result.status) {
                                                SocialContactRequestStatus.PENDING -> socialLabel(
                                                    language,
                                                    if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                        "Your interest is still pending. A chat opens only after they accept."
                                                    } else {
                                                        "Your contact request is still pending."
                                                    },
                                                    if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                        "আপনার আগ্রহের অনুরোধ অপেক্ষায় আছে। তারা গ্রহণ করলেই চ্যাট শুরু হবে।"
                                                    } else {
                                                        "আপনার যোগাযোগের অনুরোধ এখনো অপেক্ষায় আছে।"
                                                    },
                                                    if (person.contactPolicy == PeopleContactPolicy.MATCHES) {
                                                        "आपकी रुचि लंबित है। वे स्वीकार करेंगे तभी चैट खुलेगी।"
                                                    } else {
                                                        "आपका संपर्क अनुरोध अभी लंबित है।"
                                                    }
                                                )
                                                SocialContactRequestStatus.ACCEPTED -> {
                                                    tab = PeopleTab.CHATS
                                                    socialLabel(
                                                        language,
                                                        "Your request was accepted. Open the chat from Chats.",
                                                        "আপনার অনুরোধ গ্রহণ করা হয়েছে। Chats থেকে চ্যাট খুলুন।",
                                                        "आपका अनुरोध स्वीकार किया गया। Chats से चैट खोलें।"
                                                    )
                                                }
                                                SocialContactRequestStatus.IGNORED -> socialLabel(
                                                    language,
                                                    "This request was already closed and cannot be sent again.",
                                                    "এই অনুরোধটি আগেই বন্ধ হয়েছে এবং আবার পাঠানো যাবে না।",
                                                    "यह अनुरोध पहले ही बंद हो चुका है और दोबारा नहीं भेजा जा सकता।"
                                                )
                                            }
                                        }
                                        PeopleContactStartResult.IncomingRequest -> {
                                            tab = PeopleTab.REQUESTS
                                            notice = socialLabel(
                                                language,
                                                "They already sent you a request. Review it in Requests.",
                                                "তারা আপনাকে ইতিমধ্যে অনুরোধ পাঠিয়েছেন। Requests-এ দেখুন।",
                                                "उन्होंने आपको पहले ही अनुरोध भेजा है। Requests में देखें।"
                                            )
                                        }
                                        PeopleContactStartResult.Unavailable -> {
                                            notice = socialLabel(
                                                language,
                                                "This profile is no longer available for new chats. The list is refreshing.",
                                                "এই প্রোফাইলে আর নতুন চ্যাট শুরু করা যাচ্ছে না। তালিকা রিফ্রেশ হচ্ছে।",
                                                "इस प्रोफ़ाइल से अब नई चैट शुरू नहीं की जा सकती। सूची रीफ़्रेश हो रही है।"
                                            )
                                            refreshKey++
                                        }
                                    }
                                } catch (error: Exception) {
                                    fatalError = peopleFailureMessage(
                                        error,
                                        language,
                                        socialLabel(language, "Could not start chat. Check your connection or try again.", "চ্যাট শুরু করা যায়নি। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।", "चैट शुरू नहीं हो सकी। कनेक्शन जाँचकर फिर कोशिश करें।")
                                    )
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
                PeopleTab.CHATS -> {
                    val visibleConversations = conversations.filterNot {
                        it.peer.uid in blockedIds || store.isConversationArchived("people:${it.id}")
                    }
                    when {
                        visibleConversations.isEmpty() && conversations.any {
                            store.isConversationArchived("people:${it.id}")
                        } -> EmptyPeopleState(
                            language,
                            socialLabel(
                                language,
                                "Archived chats are in Chats > Archived in the main inbox. Restore one there to show it here.",
                                "আর্কাইভ করা চ্যাট মূল ইনবক্সের Chats > Archived-এ আছে। সেখানে ফিরিয়ে আনলে এখানে দেখা যাবে।",
                                "आर्काइव चैट मुख्य इनबॉक्स के Chats > Archived में हैं। वहाँ वापस लाने पर वे यहाँ दिखेंगी।"
                            )
                        )
                        visibleConversations.isEmpty() -> EmptyPeopleState(
                            language,
                            socialLabel(language, "Chats with people you selected will appear here.", "আপনার নির্বাচিত মানুষের চ্যাট এখানে দেখা যাবে।", "आपके चुने हुए लोगों की चैट यहाँ दिखेंगी।")
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                        ) {
                            items(visibleConversations, key = { it.id }) { conversation ->
                                ConversationCard(conversation, language) { onOpenConversation(conversation) }
                            }
                        }
                    }
                }
                PeopleTab.GROUPS -> CircleGroupsContent(
                    language = language,
                    uid = uid,
                    groups = circleGroups,
                    invites = circleGroupInvites,
                    busy = busy,
                    onOpen = onOpenGroup,
                    onCreate = { name ->
                        busy = true
                        scope.launch {
                            try {
                                repository.createCircleGroup(uid, name)
                                notice = socialLabel(language, "Group created.", "গ্রুপ তৈরি হয়েছে।", "समूह बनाया गया।")
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not create the group.", "গ্রুপ তৈরি করা যায়নি।", "समूह नहीं बनाया जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onInvite = { groupId, memberUid ->
                        busy = true
                        scope.launch {
                            try {
                                repository.inviteCircleGroupMember(uid, groupId, memberUid)
                                notice = socialLabel(language, "Invitation sent. They must accept before joining.", "আমন্ত্রণ পাঠানো হয়েছে। যোগ দেওয়ার আগে তাদের গ্রহণ করতে হবে।", "आमंत्रण भेजा गया। जुड़ने से पहले उन्हें स्वीकार करना होगा।")
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not invite this account.", "এই অ্যাকাউন্টকে আমন্ত্রণ জানানো যায়নি।", "इस खाते को आमंत्रित नहीं किया जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onAcceptInvite = { groupId ->
                        busy = true
                        scope.launch {
                            try {
                                repository.acceptCircleGroupInvite(uid, groupId)
                                circleGroupInvites = circleGroupInvites.filterNot { it.first == groupId }
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not accept this invitation.", "এই আমন্ত্রণ গ্রহণ করা যায়নি।", "यह आमंत्रण स्वीकार नहीं किया जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onRename = { groupId, name ->
                        busy = true
                        scope.launch {
                            try {
                                repository.renameCircleGroup(uid, groupId, name)
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not rename this group.", "গ্রুপের নাম বদলানো যায়নি।", "समूह का नाम नहीं बदला जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onLeave = { groupId ->
                        busy = true
                        scope.launch {
                            try {
                                repository.leaveCircleGroup(uid, groupId)
                                circleGroups = circleGroups.filterNot { it.id == groupId }
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not leave this group.", "গ্রুপ ছেড়ে যাওয়া যায়নি।", "समूह छोड़ा नहीं जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onRemove = { groupId, memberUid ->
                        busy = true
                        scope.launch {
                            try {
                                repository.removeCircleGroupMember(uid, groupId, memberUid)
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not remove this member.", "সদস্যকে সরানো যায়নি।", "सदस्य को हटाया नहीं जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onPromote = { groupId, memberUid ->
                        busy = true
                        scope.launch {
                            try {
                                repository.promoteCircleGroupAdmin(uid, groupId, memberUid)
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not make this member an admin.", "সদস্যকে অ্যাডমিন করা যায়নি।", "सदस्य को एडमिन नहीं बनाया जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
                PeopleTab.REQUESTS -> when {
                    loadingRequests -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    contactRequests.isEmpty() -> EmptyPeopleState(
                        language,
                        socialLabel(language, "New contact requests will appear here.", "নতুন যোগাযোগের অনুরোধ এখানে দেখা যাবে।", "नए संपर्क अनुरोध यहाँ दिखेंगे।")
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                    ) {
                        items(contactRequests, key = { it.id }) { request ->
                            ContactRequestCard(
                                request = request,
                                language = language,
                                enabled = !busy,
                                canAccept = isPublic,
                                onAccept = {
                                    busy = true
                                    fatalError = ""
                                    scope.launch {
                                        try {
                                            val conversation = repository.acceptContactRequest(uid, request)
                                            onOpenConversation(conversation)
                                        } catch (error: Exception) {
                                            fatalError = peopleFailureMessage(
                                                error,
                                                language,
                                                socialLabel(language, "Could not accept this request. Try again.", "অনুরোধটি গ্রহণ করা যায়নি। আবার চেষ্টা করুন।", "यह अनुरोध स्वीकार नहीं हो सका। फिर कोशिश करें।")
                                            )
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                onIgnore = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            repository.ignoreContactRequest(uid, request)
                                        } catch (error: Exception) {
                                            fatalError = peopleFailureMessage(
                                                error,
                                                language,
                                                socialLabel(language, "Could not ignore this request. Try again.", "অনুরোধটি উপেক্ষা করা যায়নি। আবার চেষ্টা করুন।", "यह अनुरोध अनदेखा नहीं हो सका। फिर कोशिश करें।")
                                            )
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                onBlock = { blockRequestConfirmation = request },
                                onReport = {
                                    requestReportReason = ""
                                    reportSubmitError = ""
                                    reportRequest = request
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteProfileConfirmation) {
        AlertDialog(
            onDismissRequest = { deleteProfileConfirmation = false },
            title = { Text(socialLabel(language, "Delete public profile?", "প্রকাশিত প্রোফাইল মুছবেন?", "सार्वजनिक प्रोफ़ाइल हटाएँ?")) },
            text = { Text(socialLabel(language, "Your profile will be removed from discovery. Existing chat copies and messages remain for the other participant.", "আপনার প্রোফাইল খোঁজার তালিকা থেকে মুছে যাবে। আগের চ্যাট ও বার্তা অন্য অংশগ্রহণকারীর কাছে থাকবে।", "आपकी प्रोफ़ाइल खोज से हट जाएगी। मौजूदा चैट की कॉपी और संदेश दूसरे व्यक्ति के पास रहेंगे।")) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        deleteProfileConfirmation = false
                        busy = true
                        scope.launch {
                            try {
                                repository.deleteOwnProfile(uid)
                                ownProfile = null
                                notice = socialLabel(language, "Public profile deleted.", "প্রকাশিত প্রোফাইল মুছে ফেলা হয়েছে।", "सार्वजनिक प्रोफ़ाइल हटा दी गई।")
                            } catch (error: Exception) {
                                fatalError = peopleFailureMessage(
                                    error,
                                    language,
                                    socialLabel(language, "Could not delete the profile.", "প্রোফাইল মুছে ফেলা যায়নি।", "प्रोफ़ाइल हटाई नहीं जा सकी।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(socialLabel(language, "Delete", "মুছুন", "हटाएँ"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProfileConfirmation = false }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }

    blockRequestConfirmation?.let { request ->
        AlertDialog(
            onDismissRequest = { if (!busy) blockRequestConfirmation = null },
            title = { Text(socialLabel(language, "Block this person?", "এই ব্যক্তিকে ব্লক করবেন?", "इस व्यक्ति को ब्लॉक करें?")) },
            text = { Text(socialLabel(language, "They will not be able to start or continue a chat with you.", "তারা আপনার সঙ্গে চ্যাট শুরু বা চালিয়ে যেতে পারবে না।", "वे आपके साथ चैट शुरू या जारी नहीं कर पाएँगे।")) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    blockRequestConfirmation = null
                    busy = true
                    scope.launch {
                        try {
                            repository.blockContactRequest(uid, request)
                            blockedIds = blockedIds + request.id
                            notice = socialLabel(language, "Person blocked.", "ব্যক্তিকে ব্লক করা হয়েছে।", "व्यक्ति को ब्लॉक कर दिया गया।")
                        } catch (error: Exception) {
                            fatalError = peopleFailureMessage(
                                error,
                                language,
                                socialLabel(language, "Could not block this person. Try again.", "ব্যক্তিটিকে ব্লক করা যায়নি। আবার চেষ্টা করুন।", "इस व्यक्ति को ब्लॉक नहीं किया जा सका। फिर कोशिश करें।")
                            )
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(socialLabel(language, "Block", "ব্লক", "ब्लॉक")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { blockRequestConfirmation = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }

    reportRequest?.let { request ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    reportRequest = null
                    reportSubmitError = ""
                }
            },
            title = { Text(socialLabel(language, "Report this person", "এই ব্যক্তিকে রিপোর্ট করুন", "इस व्यक्ति की रिपोर्ट करें")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = requestReportReason,
                        onValueChange = { requestReportReason = it.take(500) },
                        label = { Text(socialLabel(language, "Reason (3–500 characters)", "কারণ (৩–৫০০ অক্ষর)", "कारण (3–500 अक्षर)")) },
                        minLines = 3,
                        maxLines = 5
                    )
                    if (reportSubmitError.isNotBlank()) {
                        Text(reportSubmitError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && requestReportReason.trim().length in 3..500, onClick = {
                    reportSubmitError = ""
                    busy = true
                    scope.launch {
                        try {
                            repository.reportContactRequest(uid, request.sender.uid, requestReportReason)
                            requestReportReason = ""
                            reportRequest = null
                            reportSubmitError = ""
                            notice = socialLabel(language, "Report submitted.", "রিপোর্ট পাঠানো হয়েছে।", "रिपोर्ट भेज दी गई।")
                        } catch (error: Exception) {
                            reportSubmitError = peopleFailureMessage(
                                error,
                                language,
                                socialLabel(language, "Could not submit the report. Try again.", "রিপোর্ট পাঠানো যায়নি। আবার চেষ্টা করুন।", "रिपोर्ट नहीं भेजी जा सकी। फिर कोशिश करें।")
                            )
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(socialLabel(language, "Send report", "রিপোর্ট পাঠান", "रिपोर्ट भेजें")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    reportRequest = null
                    reportSubmitError = ""
                }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }

    blockProfileConfirmation?.let { person ->
        AlertDialog(
            onDismissRequest = { if (!busy) blockProfileConfirmation = null },
            title = {
                Text(socialLabel(language, "Block this profile?", "এই প্রোফাইলটি ব্লক করবেন?", "इस प्रोफ़ाइल को ब्लॉक करें?"))
            },
            text = {
                Text(
                    socialLabel(
                        language,
                        "This profile will leave your Discover deck, and neither of you can send new messages in a chat with the other.",
                        "এই প্রোফাইলটি আপনার Discover তালিকা থেকে সরে যাবে, এবং আপনারা একে অপরকে নতুন বার্তা পাঠাতে পারবেন না।",
                        "यह प्रोफ़ाइल आपकी Discover सूची से हट जाएगी और आप दोनों एक-दूसरे को नए संदेश नहीं भेज पाएँगे।"
                    )
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    blockProfileConfirmation = null
                    busy = true
                    scope.launch {
                        try {
                            repository.blockUser(uid, person.uid)
                            blockedIds = blockedIds + person.uid
                            discovered = discovered.filterNot { it.uid == person.uid }
                            notice = socialLabel(
                                language,
                                "Profile blocked and removed from Discover.",
                                "প্রোফাইলটি ব্লক করে Discover থেকে সরানো হয়েছে।",
                                "प्रोफ़ाइल ब्लॉक करके Discover से हटा दी गई।"
                            )
                        } catch (error: Exception) {
                            fatalError = peopleFailureMessage(error, language, socialLabel(
                                language,
                                "Could not block this profile.",
                                "প্রোফাইলটি ব্লক করা যায়নি।",
                                "इस प्रोफ़ाइल को ब्लॉक नहीं किया जा सका।"
                            ))
                        } finally {
                            busy = false
                        }
                    }
                }) {
                    Text(socialLabel(language, "Block profile", "প্রোফাইল ব্লক করুন", "प्रोफ़ाइल ब्लॉक करें"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { blockProfileConfirmation = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }

    reportProfile?.let { person ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    reportProfile = null
                    reportSubmitError = ""
                }
            },
            title = {
                Text(socialLabel(language, "Report this profile", "এই প্রোফাইলটি রিপোর্ট করুন", "इस प्रोफ़ाइल की रिपोर्ट करें"))
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        socialLabel(
                            language,
                            "Your report includes your account, this profile, your reason, and the submission time. Only Dost administrators can review it. Reports are reviewed manually and do not automatically suspend an account. This is not an emergency service. For urgent danger, contact local emergency services; for follow-up, see Contact Us in the Privacy Policy.",
                            "রিপোর্টে আপনার অ্যাকাউন্ট, এই প্রোফাইল, আপনার দেওয়া কারণ এবং পাঠানোর সময় থাকবে। শুধু Dost-এর প্রশাসকেরা এটি দেখতে পারবেন। রিপোর্ট হাতে পর্যালোচনা করা হয়; এতে স্বয়ংক্রিয়ভাবে অ্যাকাউন্ট বন্ধ হয় না। এটি জরুরি সহায়তা নয়। তাৎক্ষণিক বিপদে স্থানীয় জরুরি সেবায় যোগাযোগ করুন; পরে সহায়তার জন্য গোপনীয়তা নীতির Contact Us দেখুন।",
                            "रिपोर्ट में आपका खाता, यह प्रोफ़ाइल, आपका कारण और भेजने का समय शामिल होगा। इसे केवल Dost के प्रशासक देख सकते हैं। रिपोर्ट की मैन्युअल समीक्षा होती है; इससे खाता अपने-आप निलंबित नहीं होता। यह आपातकालीन सेवा नहीं है। तत्काल खतरे में स्थानीय आपातकालीन सेवाओं से संपर्क करें; आगे की सहायता के लिए गोपनीयता नीति का Contact Us देखें।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = profileReportReason,
                        onValueChange = { profileReportReason = it.take(500) },
                        label = {
                            Text(socialLabel(language, "Reason (3–500 characters)", "কারণ (৩–৫০০ অক্ষর)", "कारण (3–500 अक्षर)"))
                        },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (reportSubmitError.isNotBlank()) {
                        Text(reportSubmitError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && profileReportReason.trim().length in 3..500,
                    onClick = {
                        reportSubmitError = ""
                        busy = true
                        scope.launch {
                            try {
                                repository.reportPublicProfile(uid, person.uid, profileReportReason)
                                profileReportReason = ""
                                reportProfile = null
                                reportSubmitError = ""
                                notice = socialLabel(
                                    language,
                                    "Report submitted for manual review.",
                                    "রিপোর্টটি হাতে পর্যালোচনার জন্য পাঠানো হয়েছে।",
                                    "रिपोर्ट मैन्युअल समीक्षा के लिए भेज दी गई।"
                                )
                            } catch (error: Exception) {
                                reportSubmitError = peopleFailureMessage(error, language, socialLabel(
                                    language,
                                    "Could not submit this report.",
                                    "রিপোর্টটি পাঠানো যায়নি।",
                                    "रिपोर्ट नहीं भेजी जा सकी।"
                                ))
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(socialLabel(language, "Send report", "রিপোর্ট পাঠান", "रिपोर्ट भेजें"))
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    reportProfile = null
                    reportSubmitError = ""
                }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
    }
}

private data class PeopleProfilePreview(
    val displayName: String,
    val ageBand: String,
    val gender: String,
    val country: String,
    val bio: String,
    val interests: String,
    val contactPolicy: PeopleContactPolicy
)

private fun PublicPersonProfile.toPeopleProfilePreview() = PeopleProfilePreview(
    displayName = displayName,
    ageBand = ageBand,
    gender = gender,
    country = country,
    bio = bio,
    interests = interests,
    contactPolicy = contactPolicy
)

@Composable
private fun PeopleProfileCardContent(
    preview: PeopleProfilePreview,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val demographics = listOf(preview.ageBand, preview.gender, preview.country)
        .filter(String::isNotBlank)
        .joinToString(" · ")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                socialLabel(language, "Real-person profile", "মানুষের প্রোফাইল", "व्यक्ति की प्रोफ़ाइल"),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            preview.displayName.ifBlank {
                socialLabel(language, "Display name", "দেখানোর নাম", "दिखाने का नाम")
            },
            style = MaterialTheme.typography.headlineSmall
        )
        if (demographics.isNotBlank()) {
            Text(
                demographics,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (preview.contactPolicy) {
            PeopleContactPolicy.EVERYONE -> Text(
                socialLabel(
                    language,
                    "Any eligible adult can start a chat directly.",
                    "যোগ্য যেকোনো প্রাপ্তবয়স্ক সরাসরি চ্যাট শুরু করতে পারেন।",
                    "कोई भी पात्र वयस्क सीधे चैट शुरू कर सकता है।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            PeopleContactPolicy.REQUESTS -> Text(
                socialLabel(
                    language,
                    "They require a contact request first.",
                    "আগে যোগাযোগের অনুরোধ পাঠাতে হবে।",
                    "पहले संपर्क अनुरोध भेजना होगा।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            PeopleContactPolicy.MATCHES -> Text(
                socialLabel(
                    language,
                    "They want to match before starting a chat.",
                    "চ্যাট শুরুর আগে তারা ম্যাচ করতে চান।",
                    "वे चैट शुरू करने से पहले मैच करना चाहते हैं।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            PeopleContactPolicy.NOBODY -> Text(
                socialLabel(
                    language,
                    "They are not accepting new chats.",
                    "তারা নতুন চ্যাট নিচ্ছেন না।",
                    "वे नई चैट स्वीकार नहीं कर रहे हैं।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (preview.bio.isNotBlank()) {
            Text(preview.bio, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        }
        if (preview.interests.isNotBlank()) {
            Text(
                socialLabel(
                    language,
                    "Interests: ${preview.interests}",
                    "আগ্রহ: ${preview.interests}",
                    "रुचियाँ: ${preview.interests}"
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PeopleProfileEditor(
    language: AppLanguage,
    userName: String,
    userAge: String,
    userGender: String,
    country: String,
    onCountryChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    interests: String,
    onInterestsChange: (String) -> Unit,
    contactPolicy: PeopleContactPolicy,
    onContactPolicyChange: (PeopleContactPolicy) -> Unit,
    messageNotificationsEnabled: Boolean,
    onMessageNotificationsChange: (Boolean) -> Unit,
    isPublic: Boolean,
    loading: Boolean,
    busy: Boolean,
    onPublish: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Text(socialLabel(language, "Your public profile", "আপনার প্রকাশিত প্রোফাইল", "आपकी सार्वजनिक प्रोफ़ाइल"), style = MaterialTheme.typography.titleLarge)
            Text(
                socialLabel(
                    language,
                    "This is a real-person profile, not an AI character. Your public profile shows your display name, age band, gender, country, bio, interests, and contact preference. Email, exact age, precise location, and Firebase UID are not displayed. Details are self-reported; the age band is based on signup age and is not independently verified.",
                    "এটি একজন মানুষের প্রোফাইল, AI চরিত্র নয়। প্রকাশিত প্রোফাইলে দেখানোর নাম, বয়সের পরিসর, লিঙ্গ, দেশ, পরিচিতি, আগ্রহ ও যোগাযোগের পছন্দ থাকবে। ইমেইল, সঠিক বয়স, সুনির্দিষ্ট অবস্থান ও Firebase UID দেখানো হয় না। তথ্য আপনার দেওয়া; বয়সের পরিসর সাইন-আপের বয়সের ভিত্তিতে, আলাদাভাবে যাচাই করা হয় না।",
                    "यह किसी व्यक्ति की प्रोफ़ाइल है, AI पात्र नहीं। सार्वजनिक प्रोफ़ाइल में दिखाने का नाम, आयु-वर्ग, लिंग, देश, परिचय, रुचियाँ और संपर्क प्राथमिकता शामिल हैं। ईमेल, सही उम्र, सटीक स्थान और Firebase UID नहीं दिखते। जानकारी स्वयं दी गई है; आयु-वर्ग साइन-अप की उम्र पर आधारित है और स्वतंत्र रूप से सत्यापित नहीं है।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(userName, {}, label = { Text(socialLabel(language, "Display name", "দেখানোর নাম", "दिखाने का नाम")) }, enabled = false, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                "${socialAgeBand(userAge).orEmpty()} · $userGender",
                {},
                label = { Text(socialLabel(language, "Age band · gender", "বয়সের পরিসর · লিঙ্গ", "आयु-वर्ग · लिंग")) },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(country, onCountryChange, label = { Text(socialLabel(language, "Country", "দেশ", "देश")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(bio, onBioChange, label = { Text(socialLabel(language, "About you", "আপনার সম্পর্কে", "आपके बारे में")) }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(interests, onInterestsChange, label = { Text(socialLabel(language, "Interests", "আগ্রহ", "रुचियाँ")) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    socialLabel(language, "Public profile preview", "প্রকাশিত প্রোফাইলের পূর্বরূপ", "सार्वजनिक प्रोफ़ाइल का पूर्वावलोकन"),
                    style = MaterialTheme.typography.titleMedium
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    PeopleProfileCardContent(
                        preview = PeopleProfilePreview(
                            displayName = userName,
                            ageBand = socialAgeBand(userAge).orEmpty(),
                            gender = userGender,
                            country = country.trim(),
                            bio = bio.trim(),
                            interests = interests.trim(),
                            contactPolicy = contactPolicy
                        ),
                        language = language,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        socialLabel(language, "People message notifications", "People বার্তার নোটিফিকেশন", "People संदेश सूचनाएँ"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        socialLabel(
                            language,
                            "Optional and for this device only. Alerts never include message text or the sender's name.",
                            "ঐচ্ছিক এবং শুধু এই ডিভাইসের জন্য। সতর্কতায় বার্তার লেখা বা প্রেরকের নাম থাকে না।",
                            "वैकल्पिक और केवल इस डिवाइस के लिए। अलर्ट में संदेश या भेजने वाले का नाम नहीं दिखेगा।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = messageNotificationsEnabled,
                    onCheckedChange = onMessageNotificationsChange,
                    enabled = !busy && !loading
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(socialLabel(language, "Who can start a chat?", "কে চ্যাট শুরু করতে পারবে?", "कौन चैट शुरू कर सकता है?"), style = MaterialTheme.typography.titleSmall)
                listOf(
                    PeopleContactPolicy.EVERYONE to socialLabel(language, "Any eligible adult", "যোগ্য যেকোনো প্রাপ্তবয়স্ক", "कोई भी पात्र वयस्क"),
                    PeopleContactPolicy.REQUESTS to socialLabel(language, "Requests first", "আগে অনুরোধ পাঠাতে হবে", "पहले अनुरोध भेजें"),
                    PeopleContactPolicy.MATCHES to socialLabel(language, "Mutual match first", "আগে দুজনের সম্মতি", "पहले आपसी मैच"),
                    PeopleContactPolicy.NOBODY to socialLabel(language, "No new chats", "নতুন চ্যাট নয়", "नई चैट नहीं")
                ).forEach { (policy, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = contactPolicy == policy,
                            onClick = { onContactPolicyChange(policy) },
                            enabled = !busy && !loading
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    socialLabel(
                        language,
                        "With mutual match first, a contact request must be accepted before a chat opens. Existing chats remain available unless you block someone.",
                        "আগে দুজনের সম্মতি বেছে নিলে চ্যাট খোলার আগে অনুরোধ গ্রহণ করতে হবে। কাউকে ব্লক না করলে আগের চ্যাট চালু থাকবে।",
                        "आपसी मैच चुनने पर चैट खुलने से पहले अनुरोध स्वीकार करना होगा। किसी को ब्लॉक न करने तक मौजूदा चैट उपलब्ध रहेंगी।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Text(
                socialLabel(
                    language,
                    "Discovery is limited to eligible adults; it does not match people by exact age band. Your profile is visible only after you publish it, and you can hide or delete it at any time.",
                    "ডিসকভারি শুধু যোগ্য প্রাপ্তবয়স্কদের জন্য; এখানে নির্দিষ্ট বয়সের ব্যান্ড অনুযায়ী ম্যাচ করা হয় না। প্রকাশ করলেই প্রোফাইল দেখা যাবে, এবং যেকোনো সময় লুকাতে বা মুছতে পারবেন।",
                    "डिस्कवरी केवल पात्र वयस्कों के लिए है; लोगों का मिलान सटीक आयु-वर्ग के आधार पर नहीं होता। प्रकाशित करने पर ही आपकी प्रोफ़ाइल दिखेगी, और आप इसे कभी भी छिपा या हटा सकते हैं।"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Button(onClick = onPublish, enabled = !busy && !loading && userName.isNotBlank() && country.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (isPublic) socialLabel(language, "Save profile changes", "প্রোফাইলের পরিবর্তন সংরক্ষণ", "प्रोफ़ाइल बदलाव सेव करें") else socialLabel(language, "Publish profile", "প্রোফাইল প্রকাশ করুন", "प्रोफ़ाइल प्रकाशित करें"))
            }
        }
        if (isPublic) item {
            OutlinedButton(onClick = onHide, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(socialLabel(language, "Hide from discovery", "খোঁজার তালিকা থেকে লুকান", "खोज से छिपाएँ"))
            }
        }
        if (isPublic || userAge.isNotBlank()) item {
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(socialLabel(language, "Delete profile", "প্রোফাইল মুছুন", "प्रोफ़ाइल हटाएँ"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PeopleSwipeDeck(
    people: List<PublicPersonProfile>,
    conversations: List<SocialConversation>,
    incomingRequests: List<SocialContactRequest>,
    outgoingRequests: List<SocialOutgoingContactRequest>,
    language: AppLanguage,
    store: DostStore,
    uid: String,
    enabled: Boolean,
    interestQuery: String,
    onInterestQueryChange: (String) -> Unit,
    onOpenConversation: (SocialConversation) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenChats: () -> Unit,
    onBlockProfile: (PublicPersonProfile) -> Unit,
    onReportProfile: (PublicPersonProfile) -> Unit,
    onSelect: (PublicPersonProfile) -> Unit
) {
    var passedIds by remember(uid, store) { mutableStateOf(store.passedPeopleProfileIds()) }
    var dragOffset by remember { mutableIntStateOf(0) }
    val filteredPeople = filterPeopleByInterest(people, interestQuery)
    val current = filteredPeople.firstOrNull { it.uid !in passedIds }
    LaunchedEffect(current?.uid) { dragOffset = 0 }

    if (current == null) {
        val noInterestMatches = interestQuery.isNotBlank() && filteredPeople.isEmpty()
        EmptyPeopleState(
            language,
            if (noInterestMatches) {
                socialLabel(
                    language,
                    "No profiles match this interest.",
                    "এই আগ্রহের সঙ্গে মেলে এমন প্রোফাইল নেই।",
                    "इस रुचि से मेल खाती प्रोफ़ाइल नहीं है।"
                )
            } else {
                socialLabel(language, "You’ve seen everyone in this list.", "এই তালিকার সবাইকে দেখা হয়েছে।", "इस सूची में सभी को देख लिया है।")
            },
            onProfile = {
                if (noInterestMatches) {
                    onInterestQueryChange("")
                } else {
                    store.clearPassedPeopleProfiles()
                    passedIds = emptySet()
                }
            },
            onProfileLabel = if (noInterestMatches) {
                socialLabel(language, "Clear interest filter", "আগ্রহের ফিল্টার মুছুন", "रुचि फ़िल्टर हटाएँ")
            } else {
                socialLabel(language, "Start over", "আবার শুরু করুন", "फिर शुरू करें")
            }
        )
        return
    }

    val currentConversation = conversations.firstOrNull { it.peer.uid == current.uid }
    val hasIncomingRequest = incomingRequests.any { it.id == current.uid }
    val outgoingRequest = outgoingRequests.firstOrNull { it.recipientUid == current.uid }
    fun passCurrentProfile() {
        store.markPeopleProfilePassed(current.uid)
        passedIds = passedIds + current.uid
    }
    var showProfileActions by remember(current.uid) { mutableStateOf(false) }
    val canUseContactAction = currentConversation != null
        || hasIncomingRequest
        || outgoingRequest?.status == SocialContactRequestStatus.ACCEPTED
        || (outgoingRequest == null && current.contactPolicy != PeopleContactPolicy.NOBODY)
    val onContactAction = {
        when {
            currentConversation != null -> onOpenConversation(currentConversation)
            hasIncomingRequest -> onOpenRequests()
            outgoingRequest?.status == SocialContactRequestStatus.ACCEPTED -> onOpenChats()
            outgoingRequest != null -> Unit
            current.contactPolicy == PeopleContactPolicy.NOBODY -> Unit
            else -> onSelect(current)
        }
    }
    val contactActionDescription = when {
        currentConversation != null -> socialLabel(language, "Open chat", "চ্যাট খুলুন", "चैट खोलें")
        hasIncomingRequest -> socialLabel(language, "Review contact request", "যোগাযোগের অনুরোধ দেখুন", "संपर्क अनुरोध देखें")
        outgoingRequest?.status == SocialContactRequestStatus.PENDING ->
            socialLabel(language, "Contact request pending", "যোগাযোগের অনুরোধ অপেক্ষায়", "संपर्क अनुरोध लंबित")
        outgoingRequest?.status == SocialContactRequestStatus.ACCEPTED ->
            socialLabel(language, "Open chats", "Chats খুলুন", "Chats खोलें")
        outgoingRequest?.status == SocialContactRequestStatus.IGNORED ->
            socialLabel(language, "Request closed", "অনুরোধ বন্ধ", "अनुरोध बंद")
        current.contactPolicy == PeopleContactPolicy.MATCHES ->
            socialLabel(language, "Show interest", "আগ্রহ জানান", "रुचि दिखाएँ")
        current.contactPolicy == PeopleContactPolicy.NOBODY ->
            socialLabel(language, "Not accepting new chats", "নতুন চ্যাট নিচ্ছেন না", "नई चैट स्वीकार नहीं")
        else -> socialLabel(language, "Contact", "যোগাযোগ করুন", "संपर्क करें")
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                currentConversation != null -> socialLabel(
                    language,
                    "You already have a chat. Swipe right to open it or left to pass.",
                    "আপনাদের ইতিমধ্যে চ্যাট আছে। খুলতে ডানে, এড়িয়ে যেতে বামে সরান।",
                    "आपकी पहले से चैट है। खोलने के लिए दाएँ, छोड़ने के लिए बाएँ स्वाइप करें।"
                )
                hasIncomingRequest -> socialLabel(
                    language,
                    "They sent you a contact request. Open Requests to respond.",
                    "তারা আপনাকে যোগাযোগের অনুরোধ পাঠিয়েছেন। উত্তর দিতে Requests খুলুন।",
                    "उन्होंने संपर्क अनुरोध भेजा है। जवाब देने के लिए Requests खोलें।"
                )
                outgoingRequest?.status == SocialContactRequestStatus.PENDING -> socialLabel(
                    language,
                    "Your contact request is pending. Wait for their response or pass.",
                    "আপনার যোগাযোগের অনুরোধ অপেক্ষায় আছে। উত্তরের জন্য অপেক্ষা করুন বা এড়িয়ে যান।",
                    "आपका संपर्क अनुरोध लंबित है। जवाब की प्रतीक्षा करें या छोड़ दें।"
                )
                outgoingRequest?.status == SocialContactRequestStatus.ACCEPTED -> socialLabel(
                    language,
                    "They accepted your request. Open Chats to continue.",
                    "তারা আপনার অনুরোধ গ্রহণ করেছেন। চালিয়ে যেতে Chats খুলুন।",
                    "उन्होंने आपका अनुरोध स्वीकार किया। आगे बढ़ने के लिए Chats खोलें।"
                )
                outgoingRequest?.status == SocialContactRequestStatus.IGNORED -> socialLabel(
                    language,
                    "This request was closed and cannot be sent again. You can pass.",
                    "এই অনুরোধ বন্ধ হয়েছে এবং আবার পাঠানো যাবে না। আপনি এড়িয়ে যেতে পারেন।",
                    "यह अनुरोध बंद हो चुका है और दोबारा नहीं भेजा जा सकता। आप छोड़ सकते हैं।"
                )
                current.contactPolicy == PeopleContactPolicy.EVERYONE -> socialLabel(
                    language,
                    "Swipe right to open a chat or left to pass. They allow direct contact.",
                    "চ্যাট খুলতে ডানে, এড়িয়ে যেতে বামে সরান। তারা সরাসরি যোগাযোগের অনুমতি দিয়েছেন।",
                    "चैट खोलने के लिए दाएँ, छोड़ने के लिए बाएँ स्वाइप करें। वे सीधे संपर्क की अनुमति देते हैं।"
                )
                current.contactPolicy == PeopleContactPolicy.REQUESTS -> socialLabel(
                    language,
                    "Swipe right to send a contact request or left to pass.",
                    "যোগাযোগের অনুরোধ পাঠাতে ডানে, এড়িয়ে যেতে বামে সরান।",
                    "संपर्क अनुरोध भेजने के लिए दाएँ, छोड़ने के लिए बाएँ स्वाइप करें।"
                )
                current.contactPolicy == PeopleContactPolicy.MATCHES -> socialLabel(
                    language,
                    "Swipe right to show interest. They must accept before a chat opens; swipe left to pass.",
                    "আগ্রহ জানাতে ডানে সরান। চ্যাট খোলার আগে তাদের অনুরোধ গ্রহণ করতে হবে; এড়িয়ে যেতে বামে সরান।",
                    "रुचि दिखाने के लिए दाएँ स्वाइप करें। चैट खुलने से पहले उन्हें स्वीकार करना होगा; छोड़ने के लिए बाएँ स्वाइप करें।"
                )
                else -> socialLabel(
                    language,
                    "Swipe left to pass. They are not accepting new chats.",
                    "এড়িয়ে যেতে বামে সরান। তারা নতুন চ্যাট নিচ্ছেন না।",
                    "छोड़ने के लिए बाएँ स्वाइপ করুন। वे नई चैट स्वीकार नहीं कर रहे हैं।"
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = interestQuery,
            onValueChange = { onInterestQueryChange(it.take(80)) },
            enabled = enabled,
            singleLine = true,
            label = {
                Text(
                    socialLabel(
                        language,
                        "Optional interest filter",
                        "ঐচ্ছিক আগ্রহের ফিল্টার",
                        "वैकल्पिक रुचि फ़िल्टर"
                    )
                )
            },
            trailingIcon = {
                if (interestQuery.isNotBlank()) {
                    IconButton(onClick = { onInterestQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = socialLabel(language, "Clear interest filter", "আগ্রহের ফিল্টার মুছুন", "रुचि फ़िल्टर हटाएँ")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .offset { IntOffset(dragOffset, 0) }
                .pointerInput(
                    current.uid,
                    current.contactPolicy,
                    currentConversation?.id,
                    hasIncomingRequest,
                    outgoingRequest?.status,
                    enabled
                ) {
                    detectDragGestures(
                        onDragEnd = {
                            when {
                                dragOffset > 100 && enabled && canUseContactAction -> onContactAction()
                                dragOffset < -100 -> passCurrentProfile()
                            }
                            dragOffset = 0
                        },
                        onDragCancel = { dragOffset = 0 },
                        onDrag = { change, amount ->
                            change.consume()
                            if (enabled) dragOffset = (dragOffset + amount.x.toInt()).coerceIn(-size.width, size.width)
                        }
                    )
                }
        ) {
            PeopleProfileCardContent(
                preview = current.toPeopleProfilePreview(),
                language = language,
                modifier = Modifier.fillMaxSize().padding(20.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { passCurrentProfile() },
                enabled = enabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = socialLabel(language, "Pass", "এড়িয়ে যান", "छोड़ें"), tint = MaterialTheme.colorScheme.error)
            }
            Box {
                IconButton(
                    onClick = { showProfileActions = true },
                    enabled = enabled,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = socialLabel(language, "Profile safety options", "প্রোফাইলের নিরাপত্তা বিকল্প", "प्रोफ़ाइल सुरक्षा विकल्प")
                    )
                }
                DropdownMenu(
                    expanded = showProfileActions,
                    onDismissRequest = { showProfileActions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(socialLabel(language, "Block profile", "প্রোফাইল ব্লক করুন", "प्रोफ़ाइल ब्लॉक करें")) },
                        leadingIcon = { Icon(Icons.Filled.Block, null) },
                        onClick = {
                            showProfileActions = false
                            onBlockProfile(current)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(socialLabel(language, "Report profile", "প্রোফাইল রিপোর্ট করুন", "प्रोफ़ाइल रिपोर्ट करें")) },
                        leadingIcon = { Icon(Icons.Filled.Flag, null) },
                        onClick = {
                            showProfileActions = false
                            onReportProfile(current)
                        }
                    )
                }
            }
            IconButton(
                onClick = onContactAction,
                enabled = enabled && canUseContactAction,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = contactActionDescription, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: SocialConversation,
    language: AppLanguage,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(conversation.peer.displayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conversation.peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    conversation.lastMessage.ifBlank { socialLabel(language, "Say hello", "হ্যালো বলুন", "नमस्ते कहें") },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (conversation.unreadCount > 0) {
                Badge {
                    Text(if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString())
                }
            }
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ContactRequestCard(
    request: SocialContactRequest,
    language: AppLanguage,
    enabled: Boolean,
    canAccept: Boolean,
    onAccept: () -> Unit,
    onIgnore: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(request.sender.displayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text(request.sender.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${request.sender.ageBand} · ${request.sender.country}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (request.sender.bio.isNotBlank()) {
                Text(request.sender.bio, style = MaterialTheme.typography.bodyMedium)
            }
            if (!canAccept) {
                Text(
                    socialLabel(language, "Republish your profile to accept a request.", "অনুরোধ গ্রহণ করতে প্রোফাইল আবার প্রকাশ করুন।", "अनुरोध स्वीकार करने के लिए अपनी प्रोफ़ाइल फिर से प्रकाशित करें।"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAccept, enabled = enabled && canAccept) {
                    Text(socialLabel(language, "Accept", "গ্রহণ করুন", "स्वीकारें"))
                }
                TextButton(onClick = onIgnore, enabled = enabled) {
                    Text(socialLabel(language, "Ignore", "উপেক্ষা করুন", "अनदेखा करें"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBlock, enabled = enabled) {
                    Text(socialLabel(language, "Block", "ব্লক", "ब्लॉक"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onReport, enabled = enabled) {
                    Text(socialLabel(language, "Report", "রিপোর্ট", "रिपोर्ट"))
                }
            }
        }
    }
}

@Composable
private fun EmptyPeopleState(
    language: AppLanguage,
    message: String,
    onProfile: (() -> Unit)? = null,
    onProfileLabel: String = socialLabel(language, "Set up profile", "প্রোফাইল সেট করুন", "प्रोफ़ाइल सेट करें")
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onProfile != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onProfile) { Text(onProfileLabel) }
        }
    }
}

@Composable
private fun CircleGroupsContent(
    language: AppLanguage,
    uid: String,
    groups: List<CircleGroup>,
    invites: List<Pair<String, String>>,
    busy: Boolean,
    onOpen: (CircleGroup) -> Unit,
    onCreate: (String) -> Unit,
    onInvite: (String, String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onLeave: (String) -> Unit,
    onRemove: (String, String) -> Unit,
    onPromote: (String, String) -> Unit
) {
    var newGroupName by rememberSaveable(uid) { mutableStateOf("") }
    var inviteTargets by remember(uid) { mutableStateOf(emptyMap<String, String>()) }
    var renameDrafts by remember(uid) { mutableStateOf(emptyMap<String, String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        item {
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        socialLabel(language, "Create a group", "গ্রুপ তৈরি করুন", "समूह बनाएँ"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(socialLabel(language, "Group name", "গ্রুপের নাম", "समूह का नाम")) },
                        singleLine = true,
                        enabled = !busy
                    )
                    Button(
                        onClick = {
                            onCreate(newGroupName)
                            newGroupName = ""
                        },
                        enabled = !busy && newGroupName.isNotBlank()
                    ) {
                        Text(socialLabel(language, "Create", "তৈরি করুন", "बनाएँ"))
                    }
                }
            }
        }
        items(invites, key = { "invite:${it.first}" }) { (groupId, groupName) ->
            Card {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        socialLabel(
                            language,
                            "Invitation to $groupName",
                            "$groupName-এ আমন্ত্রণ",
                            "$groupName का आमंत्रण"
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onAcceptInvite(groupId) }, enabled = !busy) {
                        Text(socialLabel(language, "Accept", "গ্রহণ করুন", "स्वीकारें"))
                    }
                }
            }
        }
        items(groups, key = { it.id }) { group ->
            val isAdmin = uid in group.admins
            Card {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        socialLabel(
                            language,
                            "${group.members.size}/32 members · ${group.lastMessage.ifBlank { "No messages yet" }}",
                            "${group.members.size}/32 সদস্য · ${group.lastMessage.ifBlank { "এখনো বার্তা নেই" }}",
                            "${group.members.size}/32 सदस्य · ${group.lastMessage.ifBlank { "अभी संदेश नहीं हैं" }}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { onOpen(group) }, enabled = !busy) {
                        Text(socialLabel(language, "Open group", "গ্রুপ খুলুন", "समूह खोलें"))
                    }
                    if (isAdmin) {
                        OutlinedTextField(
                            value = inviteTargets[group.id].orEmpty(),
                            onValueChange = { inviteTargets = inviteTargets + (group.id to it.take(128)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(socialLabel(language, "Invite account UID", "অ্যাকাউন্ট UID আমন্ত্রণ", "खाता UID आमंत्रित करें")) },
                            singleLine = true,
                            enabled = !busy
                        )
                        TextButton(
                            onClick = {
                                onInvite(group.id, inviteTargets[group.id].orEmpty().trim())
                                inviteTargets = inviteTargets - group.id
                            },
                            enabled = !busy && !inviteTargets[group.id].isNullOrBlank()
                        ) {
                            Text(socialLabel(language, "Invite (they must accept)", "আমন্ত্রণ (তাদের গ্রহণ করতে হবে)", "आमंत्रित करें (उन्हें स्वीकार करना होगा)"))
                        }
                        OutlinedTextField(
                            value = renameDrafts[group.id] ?: group.name,
                            onValueChange = { renameDrafts = renameDrafts + (group.id to it.take(80)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(socialLabel(language, "Rename group", "গ্রুপের নাম বদলান", "समूह का नाम बदलें")) },
                            singleLine = true,
                            enabled = !busy
                        )
                        TextButton(
                            onClick = {
                                onRename(group.id, renameDrafts[group.id] ?: group.name)
                                renameDrafts = renameDrafts - group.id
                            },
                            enabled = !busy && !renameDrafts[group.id].isNullOrBlank()
                        ) {
                            Text(socialLabel(language, "Save name", "নাম সংরক্ষণ", "नाम सेव करें"))
                        }
                        group.members.filterNot { it == uid }.forEach { memberUid ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(memberUid, modifier = Modifier.weight(1f))
                                if (memberUid !in group.admins) {
                                    TextButton(
                                        onClick = { onPromote(group.id, memberUid) },
                                        enabled = !busy
                                    ) {
                                        Text(socialLabel(language, "Make admin", "অ্যাডমিন করুন", "एडमिन बनाएँ"))
                                    }
                                    TextButton(
                                        onClick = { onRemove(group.id, memberUid) },
                                        enabled = !busy
                                    ) {
                                        Text(
                                            socialLabel(language, "Remove", "সরান", "हटाएँ"),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = { onLeave(group.id) }, enabled = !busy) {
                        Text(
                            socialLabel(language, "Leave group", "গ্রুপ ছাড়ুন", "समूह छोड़ें"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CircleGroupChatScreen(
    language: AppLanguage,
    uid: String,
    group: CircleGroup,
    store: DostStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    var messages by remember(group.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var draft by rememberSaveable(group.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var muted by remember(group.id) {
        mutableStateOf(store.isConversationMuted("circle:${group.id}"))
    }
    var readReceiptsEnabled by remember(uid) {
        mutableStateOf(store.circleReadReceiptsEnabled())
    }
    var typingIndicatorsEnabled by remember(uid) {
        mutableStateOf(store.circleTypingIndicatorsEnabled())
    }
    var showChatSettings by remember { mutableStateOf(false) }
    var typingMembers by remember(group.id) { mutableStateOf(emptyList<Pair<String, Long>>()) }

    DisposableEffect(uid, group.id) {
        val registration = try {
            repository.listenCircleGroupMessages(
                uid,
                group.id,
                onChange = { messages = it },
                onError = {
                    error = peopleFailureMessage(
                        it,
                        language,
                        socialLabel(language, "Could not load group messages.", "গ্রুপের বার্তা লোড করা যায়নি।", "समूह संदेश लोड नहीं हो सके।")
                    )
                }
            )
        } catch (exception: Exception) {
            error = peopleFailureMessage(
                exception,
                language,
                socialLabel(language, "Could not load group messages.", "গ্রুপের বার্তা লোড করা যায়নি।", "समूह संदेश लोड नहीं हो सके।")
            )
            null
        }
        onDispose { registration?.remove() }
    }

    DisposableEffect(uid, group.id) {
        val registration = repository.listenCircleGroupTyping(
            uid,
            group.id,
            onChange = { typingMembers = it },
            onError = { typingMembers = emptyList() }
        )
        onDispose { registration.remove() }
    }

    LaunchedEffect(typingMembers) {
        while (typingMembers.isNotEmpty()) {
            delay(1_000)
            val now = System.currentTimeMillis()
            typingMembers = typingMembers.filter { it.second > now }
        }
    }

    LaunchedEffect(uid, group.id, messages, readReceiptsEnabled) {
        if (readReceiptsEnabled) {
            try {
                repository.markCircleGroupMessagesRead(uid, group.id, messages)
            } catch (exception: Exception) {
                error = peopleFailureMessage(
                    exception,
                    language,
                    socialLabel(language, "Could not update read receipts.", "পড়ার তথ্য আপডেট করা যায়নি।", "रीड रसीद अपडेट नहीं हो सकी।")
                )
            }
        }
    }

    LaunchedEffect(uid, group.id, draft, typingIndicatorsEnabled) {
        if (!typingIndicatorsEnabled || draft.isBlank()) {
            try {
                repository.setCircleGroupTyping(uid, group.id, false)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
            }
        } else {
            delay(700)
            try {
                repository.setCircleGroupTyping(uid, group.id, true)
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            socialLabel(
                                language,
                                "${group.members.size} members",
                                "${group.members.size} জন সদস্য",
                                "${group.members.size} सदस्य"
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    IconButton(onClick = { showChatSettings = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = socialLabel(language, "Chat settings", "চ্যাট সেটিংস", "चैट सेटिंग्स")
                        )
                    }
                    DropdownMenu(
                        expanded = showChatSettings,
                        onDismissRequest = { showChatSettings = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    socialLabel(
                                        language,
                                        if (readReceiptsEnabled) "Turn off read receipts" else "Turn on read receipts",
                                        if (readReceiptsEnabled) "পড়ার তথ্য বন্ধ করুন" else "পড়ার তথ্য চালু করুন",
                                        if (readReceiptsEnabled) "रीड रसीद बंद करें" else "रीड रसीद चालू करें"
                                    )
                                )
                            },
                            onClick = {
                                readReceiptsEnabled = !readReceiptsEnabled
                                store.setCircleReadReceiptsEnabled(readReceiptsEnabled)
                                showChatSettings = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    socialLabel(
                                        language,
                                        if (typingIndicatorsEnabled) "Turn off typing indicators" else "Turn on typing indicators",
                                        if (typingIndicatorsEnabled) "টাইপিং সূচক বন্ধ করুন" else "টাইপিং সূচক চালু করুন",
                                        if (typingIndicatorsEnabled) "टाइपिंग संकेत बंद करें" else "टाइपिंग संकेत चालू करें"
                                    )
                                )
                            },
                            onClick = {
                                typingIndicatorsEnabled = !typingIndicatorsEnabled
                                store.setCircleTypingIndicatorsEnabled(typingIndicatorsEnabled)
                                showChatSettings = false
                            }
                        )
                    }
                    TextButton(onClick = {
                        muted = !muted
                        store.setConversationMuted("circle:${group.id}", muted)
                    }) {
                        Text(socialLabel(language, if (muted) "Unmute" else "Mute", if (muted) "চালু" else "নীরব", if (muted) "अनम्यूट" else "म्यूट"))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (error.isNotBlank()) {
                Text(
                    error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (typingMembers.isNotEmpty() && typingIndicatorsEnabled) {
                Text(
                    socialLabel(language, "Someone is typing…", "কেউ লিখছেন…", "कोई लिख रहा है…"),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    Surface(
                        color = if (message.senderUid == uid) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                if (message.senderUid == uid) {
                                    socialLabel(language, "You", "আপনি", "आप")
                                } else {
                                    message.senderUid.take(12)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(message.text)
                            val seenCount = message.readBy.count { it != uid }
                            if (message.senderUid == uid && readReceiptsEnabled && seenCount > 0) {
                                Text(
                                    socialLabel(
                                        language,
                                        if (seenCount == 1) "Seen" else "Seen by $seenCount",
                                        if (seenCount == 1) "দেখা হয়েছে" else "$seenCount জন দেখেছেন",
                                        if (seenCount == 1) "देखा गया" else "$seenCount ने देखा"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(2_000) },
                    modifier = Modifier.weight(1f),
                    label = { Text(socialLabel(language, "Message", "বার্তা", "संदेश")) },
                    enabled = !busy
                )
                IconButton(
                    onClick = {
                        val text = draft.trim()
                        if (text.isBlank() || busy) return@IconButton
                        busy = true
                        error = ""
                        val messageId = UUID.randomUUID().toString()
                        scope.launch {
                            try {
                                repository.sendCircleGroupMessage(uid, group.id, text, messageId)
                                draft = ""
                            } catch (exception: Exception) {
                                error = peopleFailureMessage(
                                    exception,
                                    language,
                                    socialLabel(language, "Could not send this message.", "বার্তা পাঠানো যায়নি।", "संदेश नहीं भेजा जा सका।")
                                )
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = draft.isNotBlank() && !busy
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = socialLabel(language, "Send message", "বার্তা পাঠান", "संदेश भेजें")
                    )
                }
            }
        }
    }
}

@Composable
internal fun PeopleChatScreen(
    language: AppLanguage,
    uid: String,
    conversation: SocialConversation,
    onBack: () -> Unit,
    onOpenCipher: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val store = remember(context, uid) { DostStore(context) }
    val scope = rememberCoroutineScope()
    var messages by remember(conversation.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var olderMessages by remember(conversation.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var pendingMessages by remember(conversation.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var loadingMessages by remember(conversation.id) { mutableStateOf(true) }
    var hasOlderMessages by remember(conversation.id) { mutableStateOf(false) }
    var loadingOlderMessages by remember(conversation.id) { mutableStateOf(false) }
    var olderMessagesError by remember(conversation.id) { mutableStateOf("") }
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMessageSearch by rememberSaveable(conversation.id) { mutableStateOf(false) }
    var messageSearchQuery by rememberSaveable(conversation.id) { mutableStateOf("") }
    var notificationsMuted by remember(conversation.id) {
        mutableStateOf(store.isPeopleChatMuted(conversation.id))
    }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by rememberSaveable(conversation.id) { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(uid, conversation.id) {
        var initialSnapshotReceived = false
        val registration = try {
            repository.listenMessages(
                uid,
                conversation.id,
                onChange = { confirmedMessages ->
                    val confirmedIds = confirmedMessages.mapTo(mutableSetOf()) { it.id }
                    val completedPendingIds = pendingMessages
                        .asSequence()
                        .map { it.id }
                        .filter { it in confirmedIds }
                        .toList()
                    olderMessages = mergeSocialMessages(
                        olderMessages,
                        messages.filterNot { it.id in confirmedIds }
                    )
                    messages = confirmedMessages
                    pendingMessages = pendingMessages.filterNot { it.id in confirmedIds }
                    if (completedPendingIds.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                completedPendingIds.forEach {
                                    store.removePendingPeopleMessage(conversation.id, it)
                                }
                            } catch (exception: Exception) {
                                if (exception is kotlinx.coroutines.CancellationException) throw exception
                                error = peopleFailureMessage(
                                    exception,
                                    language,
                                    socialLabel(language, "Could not clear the sent message from the retry queue.", "পাঠানো বার্তাটি পুনরায় পাঠানোর তালিকা থেকে সরানো যায়নি।", "भेजे गए संदेश को रीट्राई सूची से हटाया नहीं जा सका।")
                                )
                            }
                        }
                    }
                    if (!initialSnapshotReceived) {
                        hasOlderMessages = confirmedMessages.size >= MAX_SOCIAL_CHAT_MESSAGES
                        initialSnapshotReceived = true
                    }
                    loadingMessages = false
                },
                onError = {
                    loadingMessages = false
                    error = peopleFailureMessage(
                        it,
                        language,
                        socialLabel(language, "Could not load chat messages.", "চ্যাটের বার্তা লোড করা যায়নি।", "चैट संदेश लोड नहीं हो सके।")
                    )
                }
            )
        } catch (exception: Exception) {
            loadingMessages = false
            error = peopleFailureMessage(
                exception,
                language,
                socialLabel(language, "Could not load chat messages.", "চ্যাটের বার্তা লোড করা যায়নি।", "चैट संदेश लोड नहीं हो सके।")
            )
            null
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(uid, conversation.id) {
        try {
            val savedMessages = withContext(Dispatchers.IO) {
                store.loadPendingPeopleMessages(conversation.id)
            }
            val confirmedIds = messages.mapTo(mutableSetOf()) { it.id }
            val confirmedPendingIds = savedMessages.filter {
                it.uid == uid && it.messageId in confirmedIds
            }
            val restoredMessages = savedMessages.filter {
                it.uid == uid && it.messageId !in confirmedIds
            }
            pendingMessages = mergeSocialMessages(
                pendingMessages,
                restoredMessages.map { it.toSocialChatMessage() }
            )
            if (confirmedPendingIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    confirmedPendingIds.forEach {
                        store.removePendingPeopleMessage(conversation.id, it.messageId)
                    }
                }
            }
            restoredMessages
                .filter { it.status == PendingPeopleMessageStatus.WAITING }
                .forEach { PeopleMessageOutbox.enqueue(context, store.profileNamespace, it) }
        } catch (exception: Exception) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            error = peopleFailureMessage(
                exception,
                language,
                socialLabel(language, "Could not restore pending messages.", "অপেক্ষমাণ বার্তা পুনরুদ্ধার করা যায়নি।", "लंबित संदेश वापस नहीं लाए जा सके।")
            )
        }
    }

    LaunchedEffect(uid, conversation.id) {
        PeopleMessageOutbox.updates.collect { update ->
            if (update.uid == uid && update.conversationId == conversation.id) {
                pendingMessages = pendingMessages.map {
                    if (it.id == update.messageId) it.copy(deliveryState = update.deliveryState) else it
                }
            }
        }
    }

    val historyMessages = mergeSocialMessages(messages, olderMessages)
    val displayedMessages = mergeSocialMessages(historyMessages, pendingMessages)
    val visibleMessages = searchSocialMessages(
        displayedMessages,
        messageSearchQuery.takeIf { showMessageSearch }.orEmpty()
    )
    val latestOutgoingMessageId = displayedMessages.lastOrNull { it.senderUid == uid }?.id

    LaunchedEffect(showMessageSearch, visibleMessages.lastOrNull()?.id, displayedMessages.lastOrNull()?.id) {
        val messagesToShow = if (showMessageSearch) visibleMessages else displayedMessages
        if (messagesToShow.isNotEmpty()) {
            val olderMessagesItemCount = if (
                !loadingMessages && historyMessages.isNotEmpty() && hasOlderMessages
            ) 1 else 0
            listState.animateScrollToItem(messagesToShow.lastIndex + olderMessagesItemCount)
        }
    }

    LaunchedEffect(uid, conversation.id, messages.lastOrNull()?.id) {
        val latestMessage = messages.lastOrNull()
        if (latestMessage == null || latestMessage.senderUid != uid) {
            try {
                repository.markConversationRead(uid, conversation.id)
            } catch (exception: Exception) {
                error = peopleFailureMessage(
                    exception,
                    language,
                    socialLabel(language, "Could not mark messages as read.", "বার্তা পড়া হিসেবে চিহ্নিত করা যায়নি।", "संदेशों को पढ़ा हुआ चिह्नित नहीं किया जा सका।")
                )
            }
        }
    }

    fun deliverMessage(message: SocialChatMessage) {
        if (busy) return
        val sendingMessage = message.copy(deliveryState = SocialMessageDeliveryState.SENDING)
        pendingMessages = if (pendingMessages.any { it.id == message.id }) {
            pendingMessages.map { if (it.id == message.id) sendingMessage else it }
        } else {
            pendingMessages + sendingMessage
        }
        error = ""
        busy = true
        val savedMessage = PendingPeopleMessage(
            uid = uid,
            conversationId = conversation.id,
            messageId = message.id,
            text = message.text,
            createdAtMillis = message.createdAtMillis,
            status = PendingPeopleMessageStatus.WAITING
        )
        scope.launch {
            var persisted = false
            var serverConfirmed = false
            try {
                withContext(Dispatchers.IO) {
                    store.savePendingPeopleMessage(savedMessage)
                }
                persisted = true
                repository.sendMessage(uid, conversation.id, message.text, message.id)
                serverConfirmed = true
                pendingMessages = pendingMessages.map {
                    if (it.id == message.id) it.copy(deliveryState = SocialMessageDeliveryState.SENT) else it
                }
                withContext(Dispatchers.IO) {
                    store.removePendingPeopleMessage(conversation.id, message.id)
                }
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                if (!persisted) {
                    pendingMessages = pendingMessages.map {
                        if (it.id == message.id) it.copy(deliveryState = SocialMessageDeliveryState.FAILED) else it
                    }
                    error = peopleFailureMessage(
                        exception,
                        language,
                        socialLabel(language, "Could not save this message for retry.", "বার্তাটি পুনরায় পাঠানোর জন্য সংরক্ষণ করা যায়নি।", "यह संदेश फिर से भेजने के लिए सेव नहीं हो सका।")
                    )
                    return@launch
                }
                if (serverConfirmed) {
                    error = peopleFailureMessage(
                        exception,
                        language,
                        socialLabel(language, "Message sent, but retry cleanup is still pending.", "বার্তা পাঠানো হয়েছে, তবে পুনরায় পাঠানোর তথ্য পরিষ্কার করা বাকি।", "संदेश भेजा गया, लेकिन रीट्राई की सफाई बाकी है।")
                    )
                    try {
                        PeopleMessageOutbox.enqueue(context, store.profileNamespace, savedMessage)
                    } catch (scheduleException: Exception) {
                        error = scheduleException.message ?: error
                    }
                    return@launch
                }
                val retryable = isRetryablePeopleMessageFailure(exception)
                val updatedMessage = savedMessage.copy(
                    status = if (retryable) PendingPeopleMessageStatus.WAITING else PendingPeopleMessageStatus.FAILED
                )
                try {
                    withContext(Dispatchers.IO) {
                        store.savePendingPeopleMessage(updatedMessage)
                    }
                } catch (storageException: Exception) {
                    pendingMessages = pendingMessages.map {
                        if (it.id == message.id) it.copy(deliveryState = SocialMessageDeliveryState.FAILED) else it
                    }
                    error = storageException.message
                        ?: socialLabel(language, "Could not save this message for retry.", "বার্তাটি পুনরায় পাঠানোর জন্য সংরক্ষণ করা যায়নি।", "यह संदेश फिर से भेजने के लिए सेव नहीं हो सका।")
                    return@launch
                }
                if (messages.any { it.id == message.id }) {
                    pendingMessages = pendingMessages.filterNot { it.id == message.id }
                } else if (retryable) {
                    pendingMessages = pendingMessages.map {
                        if (it.id == message.id) it.copy(deliveryState = SocialMessageDeliveryState.WAITING) else it
                    }
                    try {
                        PeopleMessageOutbox.enqueue(context, store.profileNamespace, updatedMessage)
                    } catch (scheduleException: Exception) {
                        error = scheduleException.message
                            ?: socialLabel(language, "Could not schedule message retry.", "বার্তা পুনরায় পাঠানোর সময় নির্ধারণ করা যায়নি।", "संदेश फिर से भेजने का समय तय नहीं हो सका।")
                    }
                } else {
                    pendingMessages = pendingMessages.map {
                        if (it.id == message.id) it.copy(deliveryState = SocialMessageDeliveryState.FAILED) else it
                    }
                    error = peopleFailureMessage(
                        exception,
                        language,
                        socialLabel(language, "Could not send message.", "বার্তা পাঠানো যায়নি।", "संदेश नहीं भेजा जा सका।")
                    )
                }
            } finally {
                busy = false
            }
        }
    }

    fun loadOlderMessages() {
        if (loadingOlderMessages || !hasOlderMessages) return
        val beforeMessageId = historyMessages.firstOrNull()?.id
        if (beforeMessageId == null) {
            hasOlderMessages = false
            return
        }
        loadingOlderMessages = true
        olderMessagesError = ""
        scope.launch {
            try {
                val page = repository.loadOlderMessages(uid, conversation.id, beforeMessageId)
                olderMessages = mergeSocialMessages(page.messages, olderMessages)
                hasOlderMessages = page.hasMore
            } catch (exception: Exception) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                olderMessagesError = peopleFailureMessage(
                    exception,
                    language,
                    socialLabel(language, "Could not load older messages. Try again.", "আগের বার্তা লোড করা যায়নি। আবার চেষ্টা করুন।", "पुराने संदेश लोड नहीं हो सके। फिर कोशिश करें।")
                )
            } finally {
                loadingOlderMessages = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                conversation.peer.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(conversation.peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${conversation.peer.ageBand} · ${conversation.peer.country}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    if (BuildConfig.DOST_CIPHER_ENABLED) {
                        TextButton(onClick = onOpenCipher) {
                            Text("Cipher")
                        }
                    }
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(context, PeopleCallActivity::class.java).apply {
                                action = PeopleCallActivity.ACTION_START_OUTGOING
                                putExtra(PeopleCallActivity.EXTRA_CHAT_ID, conversation.id)
                                putExtra(PeopleCallActivity.EXTRA_CALLER_UID, uid)
                                putExtra(PeopleCallActivity.EXTRA_CALLEE_UID, conversation.peer.uid)
                                putExtra(PeopleCallActivity.EXTRA_PEER_NAME, conversation.peer.displayName)
                                putExtra(PeopleCallActivity.EXTRA_MEDIA_TYPE, "audio")
                            }
                        )
                    }) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = socialLabel(language, "Start audio call", "অডিও কল শুরু করুন", "ऑडियो कॉल शुरू करें")
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(
                            Intent(context, PeopleCallActivity::class.java).apply {
                                action = PeopleCallActivity.ACTION_START_OUTGOING
                                putExtra(PeopleCallActivity.EXTRA_CHAT_ID, conversation.id)
                                putExtra(PeopleCallActivity.EXTRA_CALLER_UID, uid)
                                putExtra(PeopleCallActivity.EXTRA_CALLEE_UID, conversation.peer.uid)
                                putExtra(PeopleCallActivity.EXTRA_PEER_NAME, conversation.peer.displayName)
                                putExtra(PeopleCallActivity.EXTRA_MEDIA_TYPE, "video")
                            }
                        )
                    }) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = socialLabel(language, "Start video call", "ভিডিও কল শুরু করুন", "वीडियो कॉल शुरू करें")
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = socialLabel(language, "Conversation options", "কথোপকথনের বিকল্প", "बातचीत के विकल्प")
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        socialLabel(
                                            language,
                                            if (showMessageSearch) "Close message search" else "Search messages",
                                            if (showMessageSearch) "বার্তা খোঁজা বন্ধ করুন" else "বার্তা খুঁজুন",
                                            if (showMessageSearch) "संदेश खोज बंद करें" else "संदेश खोजें"
                                        )
                                    )
                                },
                                onClick = {
                                    showMessageSearch = !showMessageSearch
                                    if (!showMessageSearch) messageSearchQuery = ""
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        socialLabel(
                                            language,
                                            if (notificationsMuted) "Unmute notifications" else "Mute notifications",
                                            if (notificationsMuted) "নোটিফিকেশন চালু করুন" else "নোটিফিকেশন বন্ধ করুন",
                                            if (notificationsMuted) "सूचनाएँ अनम्यूट करें" else "सूचनाएँ म्यूट करें"
                                        )
                                    )
                                },
                                onClick = {
                                    notificationsMuted = !notificationsMuted
                                    store.setPeopleChatMuted(conversation.id, notificationsMuted)
                                    if (notificationsMuted) {
                                        PeopleMessageNotifications.cancel(context, conversation.id)
                                    }
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(socialLabel(language, "Report user", "ব্যবহারকারীকে রিপোর্ট করুন", "उपयोगकर्ता की रिपोर्ट करें")) },
                                leadingIcon = { Icon(Icons.Filled.Flag, null) },
                                onClick = { showMenu = false; showReportDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(socialLabel(language, "Block user", "ব্যবহারকারীকে ব্লক করুন", "उपयोगकर्ता को ब्लॉक करें")) },
                                leadingIcon = { Icon(Icons.Filled.Block, null) },
                                onClick = { showMenu = false; showBlockConfirmation = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        socialLabel(language, "Both people can read these messages. Never share passwords, verification codes, or your home address.", "এই বার্তা দুজনেই দেখতে পারেন। পাসওয়ার্ড, যাচাইকরণ কোড বা বাড়ির ঠিকানা শেয়ার করবেন না।", "ये संदेश दोनों लोग पढ़ सकते हैं। पासवर्ड, सत्यापन कोड या घर का पता साझा न करें।"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (showMessageSearch) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = messageSearchQuery,
                        onValueChange = { messageSearchQuery = it.take(160) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(socialLabel(language, "Search messages", "বার্তা খুঁজুন", "संदेश खोजें"))
                        },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            showMessageSearch = false
                            messageSearchQuery = ""
                        }
                    ) {
                        Text(socialLabel(language, "Close", "বন্ধ", "बंद करें"))
                    }
                }
            }
            if (error.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 14.dp)
            ) {
                if (!loadingMessages && historyMessages.isNotEmpty() && hasOlderMessages && visibleMessages.isNotEmpty()) {
                    item(key = "older-messages") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TextButton(
                                onClick = { loadOlderMessages() },
                                enabled = !loadingOlderMessages
                            ) {
                                Text(
                                    if (loadingOlderMessages) {
                                        socialLabel(language, "Loading older messages…", "আগের বার্তা লোড হচ্ছে…", "पुराने संदेश लोड हो रहे हैं…")
                                    } else {
                                        socialLabel(language, "Load older messages", "আগের বার্তা দেখুন", "पुराने संदेश लोड करें")
                                    }
                                )
                            }
                            if (olderMessagesError.isNotBlank()) {
                                Text(
                                    olderMessagesError,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                if (loadingMessages) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (displayedMessages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                socialLabel(language, "Start with a friendly hello", "একটি সুন্দর হ্যালো দিয়ে শুরু করুন", "एक दोस्ताना नमस्ते से शुरुआत करें"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                socialLabel(language, "You can send the first message.", "আপনি প্রথম বার্তা পাঠাতে পারেন।", "आप पहला संदेश भेज सकते हैं।"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (showMessageSearch && messageSearchQuery.isNotBlank() && visibleMessages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (hasOlderMessages) {
                                    socialLabel(
                                        language,
                                        "No matches in loaded messages. Load older messages to search further back.",
                                        "লোড করা বার্তায় মিল নেই। আরও পুরোনো বার্তা খুঁজতে সেগুলো লোড করুন।",
                                        "लोड किए गए संदेशों में कोई मेल नहीं। और पुराने संदेश खोजने के लिए उन्हें लोड करें।"
                                    )
                                } else {
                                    socialLabel(language, "No matching messages.", "মিল থাকা কোনো বার্তা নেই।", "कोई मेल खाते संदेश नहीं हैं।")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasOlderMessages) {
                                TextButton(
                                    onClick = { loadOlderMessages() },
                                    enabled = !loadingOlderMessages
                                ) {
                                    Text(
                                        if (loadingOlderMessages) {
                                            socialLabel(language, "Loading older messages…", "আগের বার্তা লোড হচ্ছে…", "पुराने संदेश लोड हो रहे हैं…")
                                        } else {
                                            socialLabel(language, "Load older messages", "আগের বার্তা দেখুন", "पुराने संदेश लोड करें")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                items(visibleMessages, key = { it.id }) { message ->
                    val mine = message.senderUid == uid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 320.dp),
                            shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
                            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    message.text,
                                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (message.createdAtMillis > 0L) {
                                    Text(
                                        formatPeopleMessageTime(message.createdAtMillis, language),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (mine && (message.deliveryState != SocialMessageDeliveryState.SENT
                                    || message.id == latestOutgoingMessageId)
                                ) {
                                    Text(
                                        when (message.deliveryState) {
                                            SocialMessageDeliveryState.SENDING -> socialLabel(language, "Sending…", "পাঠানো হচ্ছে…", "भेजा जा रहा है…")
                                            SocialMessageDeliveryState.WAITING -> socialLabel(language, "Saved on this device · waiting to send", "ডিভাইসে সংরক্ষিত · পাঠানোর অপেক্ষায়", "डिवाइस पर सेव · भेजने की प्रतीक्षा में")
                                            SocialMessageDeliveryState.SENT -> socialLabel(language, "Sent", "পাঠানো হয়েছে", "भेजा गया")
                                            SocialMessageDeliveryState.FAILED -> socialLabel(language, "Not sent", "পাঠানো হয়নি", "नहीं भेजा गया")
                                        },
                                        modifier = if (message.id == latestOutgoingMessageId) {
                                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                        } else Modifier,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (message.deliveryState == SocialMessageDeliveryState.FAILED)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                    )
                                }
                                if (mine && message.deliveryState in setOf(
                                        SocialMessageDeliveryState.WAITING,
                                        SocialMessageDeliveryState.FAILED
                                    )
                                ) {
                                    TextButton(
                                        onClick = { deliverMessage(message) },
                                        enabled = !busy
                                    ) {
                                        Text(
                                            if (message.deliveryState == SocialMessageDeliveryState.WAITING) {
                                                socialLabel(language, "Try now", "এখন চেষ্টা করুন", "अभी कोशिश करें")
                                            } else {
                                                socialLabel(language, "Retry", "আবার চেষ্টা করুন", "फिर कोशिश करें")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it.take(MAX_SOCIAL_MESSAGE_LENGTH) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            maxLines = 4,
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (draft.isBlank()) Text(
                                        socialLabel(language, "Write a message…", "বার্তা লিখুন…", "संदेश लिखें…"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    inner()
                                }
                            }
                        )
                    }
                    IconButton(
                        enabled = !busy && draft.isNotBlank(),
                        onClick = {
                            val text = draft.trim()
                            if (text.isBlank()) return@IconButton
                            val message = SocialChatMessage(
                                id = java.util.UUID.randomUUID().toString(),
                                senderUid = uid,
                                text = text,
                                createdAtMillis = System.currentTimeMillis(),
                                deliveryState = SocialMessageDeliveryState.SENDING
                            )
                            draft = ""
                            deliverMessage(message)
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    ) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Send, contentDescription = socialLabel(language, "Send message", "বার্তা পাঠান", "संदेश भेजें"), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            title = { Text(socialLabel(language, "Block this person?", "এই ব্যক্তিকে ব্লক করবেন?", "इस व्यक्ति को ब्लॉक करें?")) },
            text = { Text(socialLabel(language, "They will no longer be discoverable to you or able to send messages in this chat.", "তারা আর আপনার কাছে খুঁজে পাওয়া যাবে না বা এই চ্যাটে বার্তা পাঠাতে পারবে না।", "वे आपको खोज नहीं पाएँगे और इस चैट में संदेश नहीं भेज सकेंगे।")) },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirmation = false
                    scope.launch {
                        try {
                            repository.blockUser(uid, conversation.peer.uid)
                            onBack()
                        } catch (exception: Exception) {
                            error = peopleFailureMessage(
                                exception,
                                language,
                                socialLabel(language, "Could not block this person.", "ব্যবহারকারীকে ব্লক করা যায়নি।", "उपयोगकर्ता को ब्लॉक नहीं किया जा सका।")
                            )
                        }
                    }
                }) { Text(socialLabel(language, "Block", "ব্লক", "ब्लॉक"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirmation = false }) { Text(LocalizedText.CANCEL.resolve(language)) } }
        )
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(socialLabel(language, "Report this person", "এই ব্যক্তিকে রিপোর্ট করুন", "इस व्यक्ति की रिपोर्ट करें")) },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it.take(500) },
                    label = { Text(socialLabel(language, "What happened?", "কী ঘটেছে?", "क्या हुआ?")) },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    enabled = reportReason.trim().length >= 3,
                    onClick = {
                        showReportDialog = false
                        scope.launch {
                            try {
                                repository.reportUser(uid, conversation.peer.uid, conversation.id, reportReason)
                                reportReason = ""
                                error = socialLabel(language, "Report submitted.", "রিপোর্ট পাঠানো হয়েছে।", "रिपोर्ट भेज दी गई।")
                            } catch (exception: Exception) {
                                error = peopleFailureMessage(
                                    exception,
                                    language,
                                    socialLabel(language, "Could not submit report.", "রিপোর্ট পাঠানো যায়নি।", "रिपोर्ट नहीं भेजी जा सकी।")
                                )
                            }
                        }
                    }
                ) { Text(socialLabel(language, "Send report", "রিপোর্ট পাঠান", "रिपोर्ट भेजें")) }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text(LocalizedText.CANCEL.resolve(language)) } }
        )
    }
}

private fun newPublicProfile(
    uid: String,
    userName: String,
    userAge: String,
    userGender: String,
    country: String,
    bio: String,
    interests: String,
    isPublic: Boolean,
    contactPolicy: PeopleContactPolicy = PeopleContactPolicy.EVERYONE
): PublicPersonProfile = PublicPersonProfile(
    uid = uid,
    displayName = userName.trim().take(60),
    ageBand = socialAgeBand(userAge).orEmpty(),
    ageCohort = socialAgeCohort(userAge).orEmpty(),
    gender = userGender.trim().ifBlank { "Prefer not to say" }.take(40),
    country = country.trim().take(80),
    bio = bio.trim().take(500),
    interests = interests.trim().take(300),
    isPublic = isPublic,
    contactPolicy = contactPolicy
)

private fun socialLabel(language: AppLanguage, english: String, bangla: String, hindi: String): String = when (language) {
    AppLanguage.ENGLISH -> english
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
}

private fun formatPeopleMessageTime(timestampMillis: Long, language: AppLanguage): String {
    val locale = when (language) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.BANGLA -> Locale.forLanguageTag("bn")
        AppLanguage.HINDI -> Locale.forLanguageTag("hi")
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestampMillis))
}

private const val MAX_SOCIAL_MESSAGE_LENGTH = 2_000
