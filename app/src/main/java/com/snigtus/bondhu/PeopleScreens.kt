@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.snigtus.dost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class PeopleTab { DISCOVER, CHATS, REQUESTS, PROFILE }

@Composable
internal fun PeopleScreen(
    language: AppLanguage,
    uid: String,
    userName: String,
    userAge: String,
    userGender: String,
    store: DostStore,
    onBack: () -> Unit,
    onOpenConversation: (SocialConversation) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    val cohort = socialAgeCohort(userAge)
    var tab by rememberSaveable { mutableStateOf(PeopleTab.DISCOVER) }
    var ownProfile by remember(uid) { mutableStateOf<PublicPersonProfile?>(null) }
    val savedDraft = remember(uid) { store.peopleProfileDraft() }
    var country by rememberSaveable(uid) { mutableStateOf(savedDraft.country) }
    var bio by rememberSaveable(uid) { mutableStateOf(savedDraft.bio) }
    var interests by rememberSaveable(uid) { mutableStateOf(savedDraft.interests) }
    var contactPolicy by rememberSaveable(uid) { mutableStateOf(PeopleContactPolicy.EVERYONE) }
    var discovered by remember { mutableStateOf(emptyList<PublicPersonProfile>()) }
    var conversations by remember { mutableStateOf(emptyList<SocialConversation>()) }
    var contactRequests by remember { mutableStateOf(emptyList<SocialContactRequest>()) }
    var blockedIds by remember { mutableStateOf(emptySet<String>()) }
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val warning = socialLabel(
            language,
            "Enable notifications to receive incoming call alerts.",
            "ইনকামিং কলের সতর্কতা পেতে নোটিফিকেশন চালু করুন।",
            "इनकमिंग कॉल अलर्ट पाने के लिए नोटिफिकेशन चालू करें।"
        )
        if (!granted) notice = warning
        else if (notice == warning) notice = ""
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
            fatalError = error.message ?: socialLabel(language, "Could not load your profile.", "আপনার প্রোফাইল লোড করা যায়নি।", "आपकी प्रोफ़ाइल लोड नहीं हो सकी।")
        } finally {
            loadingProfile = false
        }
    }

    LaunchedEffect(uid) {
        if (uid.isBlank()) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            repository.messagingToken(uid)
            if (!ReplyNotifier.canNotify(context)) {
                notice = socialLabel(
                    language,
                    "Enable notifications to receive incoming call alerts.",
                    "ইনকামিং কলের সতর্কতা পেতে নোটিফিকেশন চালু করুন।",
                    "इनकमिंग कॉल अलर्ट पाने के लिए नोटिफिकेशन चालू करें।"
                )
            }
        } catch (error: Exception) {
            notice = error.message ?: socialLabel(
                language,
                "Could not enable incoming call alerts on this device.",
                "এই ডিভাইসে ইনকামিং কলের সতর্কতা চালু করা যায়নি।",
                "इस डिवाइस पर इनकमिंग कॉल अलर्ट चालू नहीं हो सके।"
            )
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
            fatalError = error.message ?: socialLabel(language, "Could not load people.", "মানুষের তালিকা লোড করা যায়নি।", "लोगों की सूची लोड नहीं हो सकी।")
        } finally {
            loadingPeople = false
        }
    }

    DisposableEffect(uid, tab) {
        if (tab != PeopleTab.CHATS || uid.isBlank()) {
            onDispose { }
        } else {
            val registration = try {
                repository.listenConversations(
                    uid,
                    onChange = { conversations = it },
                    onError = { fatalError = it.message ?: "Could not load conversations." }
                )
            } catch (error: Exception) {
                fatalError = error.message ?: "Could not load conversations."
                null
            }
            onDispose { registration?.remove() }
        }
    }

    DisposableEffect(uid, tab) {
        if (tab != PeopleTab.REQUESTS || uid.isBlank()) {
            onDispose { }
        } else {
            loadingRequests = true
            val registration = try {
                repository.listenIncomingRequests(
                    uid,
                    onChange = {
                        contactRequests = it
                        loadingRequests = false
                    },
                    onError = {
                        fatalError = it.message ?: "Could not load contact requests."
                        loadingRequests = false
                    }
                )
            } catch (error: Exception) {
                fatalError = error.message ?: "Could not load contact requests."
                loadingRequests = false
                null
            }
            onDispose { registration?.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(socialLabel(language, "People", "মানুষ", "लोग")) },
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
                PeopleTab.values().forEach { item ->
                    FilterChip(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = {
                            Text(when (item) {
                                PeopleTab.DISCOVER -> socialLabel(language, "Discover", "খুঁজুন", "खोजें")
                                PeopleTab.CHATS -> socialLabel(language, "Chats", "চ্যাট", "चैट")
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
                                    notice = socialLabel(language, "Your profile is public to people in your age group.", "আপনার বয়সের গোষ্ঠীর মানুষের কাছে প্রোফাইলটি প্রকাশিত হয়েছে।", "आपकी प्रोफ़ाइल आपके आयु-वर्ग के लोगों को दिख रही है।")
                                } catch (error: Exception) {
                                    fatalError = error.message ?: socialLabel(language, "Could not publish your profile.", "প্রোফাইল প্রকাশ করা যায়নি।", "प्रोफ़ाइल प्रकाशित नहीं हो सकी।")
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
                                fatalError = error.message ?: "Could not hide your profile."
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
                        socialLabel(language, "No public profiles are available in your age group yet.", "আপনার বয়সের গোষ্ঠীতে এখনো কোনো প্রকাশিত প্রোফাইল নেই।", "आपके आयु-वर्ग में अभी कोई सार्वजनिक प्रोफ़ाइल नहीं है।")
                    )
                    else -> PeopleSwipeDeck(
                        people = discovered,
                        language = language,
                        enabled = !busy,
                        onSelect = { person ->
                            busy = true
                            fatalError = ""
                            scope.launch {
                                try {
                                    when (val result = repository.startConversationOrRequest(uid, currentProfile, person)) {
                                        is PeopleContactStartResult.Conversation -> onOpenConversation(result.value)
                                        PeopleContactStartResult.RequestSent -> notice = socialLabel(
                                            language,
                                            "Contact request sent.",
                                            "যোগাযোগের অনুরোধ পাঠানো হয়েছে।",
                                            "संपर्क अनुरोध भेज दिया गया।"
                                        )
                                    }
                                } catch (error: Exception) {
                                    fatalError = error.message ?: socialLabel(language, "Could not start chat.", "চ্যাট শুরু করা যায়নি।", "चैट शुरू नहीं हो सकी।")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    )
                }
                PeopleTab.CHATS -> if (conversations.isEmpty()) {
                    EmptyPeopleState(language, socialLabel(language, "Chats with people you selected will appear here.", "আপনার নির্বাচিত মানুষের চ্যাট এখানে দেখা যাবে।", "आपके चुने हुए लोगों की चैट यहाँ दिखेंगी।"))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                    ) {
                        items(conversations.filterNot { it.peer.uid in blockedIds }, key = { it.id }) { conversation ->
                            ConversationCard(conversation, language) { onOpenConversation(conversation) }
                        }
                    }
                }
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
                                            fatalError = error.message ?: "Could not accept this request."
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
                                            fatalError = error.message ?: "Could not ignore this request."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                onBlock = { blockRequestConfirmation = request },
                                onReport = {
                                    requestReportReason = ""
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
                                fatalError = error.message ?: "Could not delete the profile."
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
                            fatalError = error.message ?: "Could not block this person."
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
            onDismissRequest = { if (!busy) reportRequest = null },
            title = { Text(socialLabel(language, "Report this person", "এই ব্যক্তিকে রিপোর্ট করুন", "इस व्यक्ति की रिपोर्ट करें")) },
            text = {
                OutlinedTextField(
                    value = requestReportReason,
                    onValueChange = { requestReportReason = it.take(500) },
                    label = { Text(socialLabel(language, "Reason (3–500 characters)", "কারণ (৩–৫০০ অক্ষর)", "कारण (3–500 अक्षर)")) },
                    minLines = 3,
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(enabled = !busy && requestReportReason.trim().length in 3..500, onClick = {
                    reportRequest = null
                    busy = true
                    scope.launch {
                        try {
                            repository.reportContactRequest(uid, request.sender.uid, requestReportReason)
                            requestReportReason = ""
                            notice = socialLabel(language, "Report submitted.", "রিপোর্ট পাঠানো হয়েছে।", "रिपोर्ट भेज दी गई।")
                        } catch (error: Exception) {
                            fatalError = error.message ?: "Could not submit report."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(socialLabel(language, "Send report", "রিপোর্ট পাঠান", "रिपोर्ट भेजें")) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { reportRequest = null }) {
                    Text(LocalizedText.CANCEL.resolve(language))
                }
            }
        )
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
                    "Your profile includes your display name, age band, gender, country, bio, interests, and contact preference. Your email and exact age are never shown. Age groups are based on the age entered at signup and are not independently verified.",
                    "আপনার প্রোফাইলে দেখানোর নাম, বয়সের পরিসর, লিঙ্গ, দেশ, পরিচিতি, আগ্রহ ও যোগাযোগের পছন্দ থাকবে। ইমেইল ও সঠিক বয়স দেখানো হবে না। বয়সের গোষ্ঠী সাইন-আপে দেওয়া বয়সের ভিত্তিতে, যা আলাদাভাবে যাচাই করা হয় না।",
                    "आपकी प्रोफ़ाइल में दिखाने का नाम, आयु-वर्ग, लिंग, देश, परिचय, रुचियाँ और संपर्क प्राथमिकता शामिल हैं। ईमेल और सही उम्र नहीं दिखाई जाएगी। आयु-वर्ग साइन-अप में दी उम्र पर आधारित है, जिसका अलग सत्यापन नहीं होता।"
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(socialLabel(language, "Who can start a chat?", "কে চ্যাট শুরু করতে পারবে?", "कौन चैट शुरू कर सकता है?"), style = MaterialTheme.typography.titleSmall)
                listOf(
                    PeopleContactPolicy.EVERYONE to socialLabel(language, "Anyone in my age group", "আমার বয়সের গোষ্ঠীর যে কেউ", "मेरे आयु-वर्ग में कोई भी"),
                    PeopleContactPolicy.REQUESTS to socialLabel(language, "Requests first", "আগে অনুরোধ পাঠাতে হবে", "पहले अनुरोध भेजें"),
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
                        "Existing chats remain available unless you block someone.",
                        "কাউকে ব্লক না করলে আগের চ্যাট চালু থাকবে।",
                        "किसी को ब्लॉक न करने तक मौजूदा चैट उपलब्ध रहेंगी।"
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
                    "Discovery shows adult age groups only. A profile becomes visible only after you publish it. You can hide or delete it at any time.",
                    "ডিসকভারিতে শুধু প্রাপ্তবয়স্কদের বয়সের গোষ্ঠী দেখানো হয়। প্রকাশ করলে তবেই প্রোফাইল দেখা যাবে। যেকোনো সময় লুকাতে বা মুছতে পারবেন।",
                    "डिस्कवरी में केवल वयस्क आयु-वर्ग दिखते हैं। प्रकाशित करने पर ही प्रोफ़ाइल दिखेगी। आप इसे कभी भी छिपा या हटा सकते हैं।"
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
    language: AppLanguage,
    enabled: Boolean,
    onSelect: (PublicPersonProfile) -> Unit
) {
    var passedIds by remember(people) { mutableStateOf(emptySet<String>()) }
    var dragOffset by remember { mutableIntStateOf(0) }
    val current = people.firstOrNull { it.uid !in passedIds }
    LaunchedEffect(current?.uid) { dragOffset = 0 }

    if (current == null) {
        EmptyPeopleState(
            language,
            socialLabel(language, "You’ve seen everyone in this list.", "এই তালিকার সবাইকে দেখা হয়েছে।", "इस सूची में सभी को देख लिया है।"),
            onProfile = { passedIds = emptySet() },
            onProfileLabel = socialLabel(language, "Start over", "আবার শুরু করুন", "फिर शुरू करें")
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            socialLabel(language, "Swipe right to contact or left to pass. Their preference may require a request.", "যোগাযোগ করতে ডানে, এড়িয়ে যেতে বামে সরান। তাদের পছন্দ অনুযায়ী অনুরোধ লাগতে পারে।", "संपर्क करने के लिए दाएँ, छोड़ने के लिए बाएँ स्वाइप करें। उनकी प्राथमिकता के अनुसार अनुरोध ज़रूरी हो सकता है।"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .offset { IntOffset(dragOffset, 0) }
                .pointerInput(current.uid, enabled) {
                    detectDragGestures(
                        onDragEnd = {
                            when {
                                dragOffset > 100 -> onSelect(current)
                                dragOffset < -100 -> passedIds = passedIds + current.uid
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
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(current.displayName, style = MaterialTheme.typography.headlineMedium)
                Text("${current.ageBand} · ${current.gender} · ${current.country}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (current.contactPolicy) {
                    PeopleContactPolicy.EVERYONE -> Unit
                    PeopleContactPolicy.REQUESTS -> Text(
                        socialLabel(language, "They require a contact request first.", "আগে যোগাযোগের অনুরোধ পাঠাতে হবে।", "पहले संपर्क अनुरोध भेजना होगा।"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    PeopleContactPolicy.NOBODY -> Text(
                        socialLabel(language, "They are not accepting new chats.", "তারা নতুন চ্যাট নিচ্ছেন না।", "वे नई चैट स्वीकार नहीं कर रहे हैं।"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (current.bio.isNotBlank()) Text(current.bio, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                if (current.interests.isNotBlank()) {
                    Text(
                        socialLabel(language, "Interests: ${current.interests}", "আগ্রহ: ${current.interests}", "रुचियाँ: ${current.interests}"),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { passedIds = passedIds + current.uid },
                enabled = enabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = socialLabel(language, "Pass", "এড়িয়ে যান", "छोड़ें"), tint = MaterialTheme.colorScheme.error)
            }
            IconButton(
                onClick = { onSelect(current) },
                enabled = enabled,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = socialLabel(language, "Contact", "যোগাযোগ করুন", "संपर्क करें"), tint = MaterialTheme.colorScheme.primary)
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
internal fun PeopleChatScreen(
    language: AppLanguage,
    uid: String,
    conversation: SocialConversation,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    var messages by remember(conversation.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var loadingMessages by remember(conversation.id) { mutableStateOf(true) }
    var draft by rememberSaveable(conversation.id) { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by rememberSaveable(conversation.id) { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(uid, conversation.id) {
        val registration = try {
            repository.listenMessages(
                uid,
                conversation.id,
                onChange = {
                    messages = it
                    loadingMessages = false
                },
                onError = {
                    loadingMessages = false
                    error = it.message ?: socialLabel(language, "Could not load chat messages.", "চ্যাটের বার্তা লোড করা যায়নি।", "चैट संदेश लोड नहीं हो सके।")
                }
            )
        } catch (exception: Exception) {
            loadingMessages = false
            error = exception.message ?: "Could not load chat messages."
            null
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LaunchedEffect(uid, conversation.id, messages.lastOrNull()?.id) {
        val latestMessage = messages.lastOrNull()
        if (latestMessage == null || latestMessage.senderUid != uid) {
            try {
                repository.markConversationRead(uid, conversation.id)
            } catch (exception: Exception) {
                error = exception.message ?: socialLabel(language, "Could not mark messages as read.", "বার্তা পড়া হিসেবে চিহ্নিত করা যায়নি।", "संदेशों को पढ़ा हुआ चिह्नित नहीं किया जा सका।")
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
                if (loadingMessages) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (messages.isEmpty()) {
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
                }
                items(messages, key = { it.id }) { message ->
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
                            draft = ""
                            error = ""
                            busy = true
                            scope.launch {
                                try {
                                    repository.sendMessage(uid, conversation.id, text)
                                } catch (exception: Exception) {
                                    draft = text
                                    error = exception.message ?: socialLabel(language, "Could not send message.", "বার্তা পাঠানো যায়নি।", "संदेश नहीं भेजा जा सका।")
                                } finally {
                                    busy = false
                                }
                            }
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
                            error = exception.message ?: socialLabel(language, "Could not block this person.", "ব্যবহারকারীকে ব্লক করা যায়নি।", "उपयोगकर्ता को ब्लॉक नहीं किया जा सका।")
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
                                error = exception.message ?: socialLabel(language, "Could not submit report.", "রিপোর্ট পাঠানো যায়নি।", "रिपोर्ट नहीं भेजी जा सकी।")
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
