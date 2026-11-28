package com.snigtus.dost

import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun CircleContactsPanel(
    language: AppLanguage,
    uid: String,
    ageCohort: String,
    store: DostStore,
    pendingInviteToken: String?,
    onConsumedInviteToken: () -> Unit,
    onOpenChat: (CircleDirectChat) -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var contacts by remember(uid) { mutableStateOf(emptyList<CircleContact>()) }
    var chats by remember(uid) { mutableStateOf(emptyList<CircleDirectChat>()) }
    var notice by remember { mutableStateOf("") }
    var handleDraft by rememberSaveable(uid) { mutableStateOf(store.circleHandle()) }
    var lookupDraft by rememberSaveable(uid) { mutableStateOf("") }
    var inviteLink by remember { mutableStateOf("") }
    var lookupEnabled by remember { mutableStateOf(ageCohort != "teen") }
    var busy by remember { mutableStateOf(false) }
    val qr = remember(inviteLink) {
        if (inviteLink.isBlank()) null else circleQrBitmap(inviteLink)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (_: Exception) {
                notice = socialLabel(language, "Could not open that photo.", "ছবিটি খোলা যায়নি।", "वह फ़ोटो नहीं खोली जा सकी।")
                return@launch
            }
            val token = decodeCircleInviteFromBitmap(bitmap)
            if (token == null) {
                notice = socialLabel(language, "No Dost invite QR was found in that photo.", "সেই ছবিতে Dost আমন্ত্রণ QR পাওয়া যায়নি।", "उस फ़ोटो में Dost आमंत्रण QR नहीं मिला।")
            } else {
                busy = true
                try {
                    repository.redeemCircleInvite(uid, token, "qr")
                    notice = socialLabel(language, "Contact added.", "পরিচিতি যোগ হয়েছে।", "संपर्क जोड़ दिया गया।")
                } catch (error: Exception) {
                    notice = peopleFailureMessage(error, language, socialLabel(language, "Could not add that contact.", "পরিচিতি যোগ করা যায়নি।", "संपर्क नहीं जोड़ा जा सका।"))
                } finally {
                    busy = false
                }
            }
        }
    }

    DisposableEffect(uid) {
        val contactsReg = repository.listenCircleContacts(uid, { notice = it.message.orEmpty() }) { contacts = it }
        val chatsReg = repository.listenCircleDirectChats(uid, { notice = it.message.orEmpty() }) { chats = it }
        onDispose {
            contactsReg.remove()
            chatsReg.remove()
        }
    }
    LaunchedEffect(uid, ageCohort) {
        try {
            val profile = repository.ensureCirclePrivacyProfile(uid, ageCohort)
            lookupEnabled = profile.handleLookupEnabled
        } catch (error: Exception) {
            notice = peopleFailureMessage(error, language, socialLabel(language, "Could not load your Circle profile.", "Circle প্রোফাইল লোড করা যায়নি।", "Circle प्रोफ़ाइल लोड नहीं हो सकी।"))
        }
    }
    LaunchedEffect(pendingInviteToken, uid) {
        val token = pendingInviteToken ?: return@LaunchedEffect
        busy = true
        try {
            repository.redeemCircleInvite(uid, token, "invite")
            notice = socialLabel(language, "Contact added from your invite link.", "আমন্ত্রণ লিংক থেকে পরিচিতি যোগ হয়েছে।", "आमंत्रण लिंक से संपर्क जोड़ दिया गया।")
        } catch (error: Exception) {
            notice = peopleFailureMessage(error, language, socialLabel(language, "This invite link could not be used.", "এই আমন্ত্রণ লিংক ব্যবহার করা যায়নি।", "यह आमंत्रण लिंक इस्तेमाल नहीं हो सका।"))
        } finally {
            busy = false
            onConsumedInviteToken()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(socialLabel(language, "Add people you already know by QR, invite link, or exact handle. Circle is not a public directory.", "যাদের চেনেন তাদের QR, আমন্ত্রণ লিংক বা সঠিক হ্যান্ডেল দিয়ে যোগ করুন। Circle কোনো পাবলিক ডিরেক্টরি নয়।", "जिन्हें आप जानते हैं उन्हें QR, आमंत्रण लिंक या सही हैंडल से जोड़ें। Circle सार्वजनिक निर्देशिका नहीं है।"))
        if (notice.isNotBlank()) Text(notice)
        Text(socialLabel(language, "Your handle", "আপনার হ্যান্ডেল", "आपका हैंडल"))
        OutlinedTextField(
            value = handleDraft,
            onValueChange = { handleDraft = normalizeCircleHandle(it).take(20) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("@handle") },
            enabled = !busy
        )
        if (ageCohort == "teen" || lookupEnabled || true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = lookupEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            try {
                                repository.updateCirclePrivacyProfile(uid, ageCohort, enabled, "contacts", "contacts")
                                lookupEnabled = enabled
                            } catch (error: Exception) {
                                notice = peopleFailureMessage(error, language, socialLabel(language, "Could not update handle lookup.", "হ্যান্ডেল খোঁজা আপডেট করা যায়নি।", "हैंडल खोज अपडेट नहीं हो सकी।"))
                            }
                        }
                    }
                )
                Text(socialLabel(language, "Allow people to find me by @handle. This is off by default for teens.", "অন্যরা @হ্যান্ডেল দিয়ে আমাকে খুঁজতে পারবে। কিশোরদের জন্য এটি ডিফল্টভাবে বন্ধ।", "लोग मुझे @हैंडल से ढूंढ सकते हैं। किशोरों के लिए यह डिफ़ॉल्ट रूप से बंद है।"))
            }
        }
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        repository.claimCircleHandle(uid, ageCohort, handleDraft)
                        store.setCircleHandle(normalizeCircleHandle(handleDraft))
                        notice = socialLabel(language, "Handle saved.", "হ্যান্ডেল সংরক্ষিত।", "हैंडल सहेजा गया।")
                    } catch (error: Exception) {
                        notice = peopleFailureMessage(error, language, socialLabel(language, "That handle is not available.", "সেই হ্যান্ডেল পাওয়া যায়নি।", "वह हैंडल उपलब्ध नहीं है।"))
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && isValidCircleHandle(normalizeCircleHandle(handleDraft))
        ) { Text(socialLabel(language, "Save handle", "হ্যান্ডেল সংরক্ষণ করুন", "हैंडल सहेजें")) }

        OutlinedTextField(
            value = lookupDraft,
            onValueChange = { lookupDraft = it.take(32) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(socialLabel(language, "Find an exact @handle", "সঠিক @হ্যান্ডেল খুঁজুন", "सही @हैंडल खोजें")) },
            enabled = !busy
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val peer = repository.lookupCircleHandle(uid, lookupDraft)
                        if (peer == uid) {
                            notice = socialLabel(language, "That's your own handle.", "এটি আপনার নিজের হ্যান্ডেল।", "यह आपका अपना हैंडल है।")
                        } else {
                            repository.sendCircleHandleRequest(uid, peer)
                            notice = socialLabel(language, "Request sent. They need to accept before you can chat.", "অনুরোধ পাঠানো হয়েছে। চ্যাট করতে তাদের সম্মতি লাগবে।", "अनुरोध भेजा गया। चैट से पहले उन्हें स्वीकार करना होगा।")
                        }
                    } catch (error: Exception) {
                        notice = peopleFailureMessage(error, language, socialLabel(language, "No one is using that handle.", "সেই হ্যান্ডেল কেউ ব্যবহার করছে না।", "कोई उस हैंडल का उपयोग नहीं कर रहा।"))
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && isValidCircleHandle(normalizeCircleHandle(lookupDraft))
        ) { Text(socialLabel(language, "Send request", "অনুরোধ পাঠান", "अनुरोध भेजें")) }

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val token = repository.createCircleInviteLink(uid)
                        inviteLink = circleInviteWebUrl(token)
                    } catch (error: Exception) {
                        notice = peopleFailureMessage(error, language, socialLabel(language, "Could not create an invite link.", "আমন্ত্রণ লিংক তৈরি করা যায়নি।", "आमंत्रण लिंक नहीं बन सका।"))
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy
        ) { Text(socialLabel(language, "Create QR and invite link", "QR ও আমন্ত্রণ লিংক তৈরি করুন", "QR और आमंत्रण लिंक बनाएँ")) }
        if (inviteLink.isNotBlank()) {
            Text(inviteLink)
            qr?.let {
                Image(bitmap = it, contentDescription = socialLabel(language, "Your Circle invite QR code", "আপনার Circle আমন্ত্রণ QR", "आपका Circle आमंत्रण QR"), modifier = Modifier.size(220.dp), contentScale = ContentScale.Fit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(inviteLink)) }) {
                    Text(socialLabel(language, "Copy link", "লিংক কপি করুন", "लिंक कॉपी करें"))
                }
                Button(onClick = { photoPicker.launch("image/*") }) {
                    Text(socialLabel(language, "Scan a QR photo", "QR ছবি স্ক্যান করুন", "QR फ़ोटो स्कैन करें"))
                }
            }
        } else {
            Button(onClick = { photoPicker.launch("image/*") }, enabled = !busy) {
                Text(socialLabel(language, "Scan a QR photo", "QR ছবি স্ক্যান করুন", "QR फ़ोटो स्कैन करें"))
            }
        }
        var pasteToken by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = pasteToken,
            onValueChange = { pasteToken = it.take(200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(socialLabel(language, "Paste an invite link", "আমন্ত্রণ লিংক পেস্ট করুন", "आमंत्रण लिंक पेस्ट करें")) }
        )
        Button(
            onClick = {
                val token = parseCircleInviteToken(pasteToken)
                if (token == null) {
                    notice = socialLabel(language, "That invite link is invalid.", "সেই আমন্ত্রণ লিংক অবৈধ।", "वह आमंत्रण लिंक अमान्य है।")
                } else {
                    scope.launch {
                        busy = true
                        try {
                            repository.redeemCircleInvite(uid, token, "invite")
                            pasteToken = ""
                            notice = socialLabel(language, "Contact added.", "পরিচিতি যোগ হয়েছে।", "संपर्क जोड़ दिया गया।")
                        } catch (error: Exception) {
                            notice = peopleFailureMessage(error, language, socialLabel(language, "Could not add that contact.", "পরিচিতি যোগ করা যায়নি।", "संपर्क नहीं जोड़ा जा सका।"))
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy && pasteToken.isNotBlank()
        ) { Text(socialLabel(language, "Add from link", "লিংক থেকে যোগ করুন", "लिंक से जोड़ें")) }

        val incoming = contacts.filter { it.status == "pending" && it.incoming }
        if (incoming.isNotEmpty()) {
            Text(socialLabel(language, "Requests", "অনুরোধ", "अनुरोध"))
            incoming.forEach { request ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(request.uid.take(12))
                    Button(onClick = {
                        scope.launch {
                            try {
                                repository.acceptCircleHandleRequest(uid, request.uid)
                            } catch (error: Exception) {
                                notice = peopleFailureMessage(error, language, socialLabel(language, "Could not accept that request.", "অনুরোধ গ্রহণ করা যায়নি।", "अनुरोध स्वीकार नहीं हो सका।"))
                            }
                        }
                    }, enabled = !busy) { Text(socialLabel(language, "Accept", "গ্রহণ করুন", "स्वीकार करें")) }
                }
            }
        }
        val accepted = contacts.filter { it.status == "accepted" }
        if (accepted.isNotEmpty()) {
            Text(socialLabel(language, "Contacts", "পরিচিতি", "संपर्क"))
            accepted.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                try {
                                    onOpenChat(repository.openCircleDirectChat(uid, contact.uid))
                                } catch (error: Exception) {
                                    notice = peopleFailureMessage(error, language, socialLabel(language, "Could not open that chat.", "চ্যাট খোলা যায়নি।", "चैट नहीं खुल सकी।"))
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(contact.uid.take(16))
                    Text(socialLabel(language, "Message", "বার্তা", "संदेश"))
                }
            }
        }
        if (chats.isNotEmpty()) {
            Text(socialLabel(language, "Recent chats", "সাম্প্রতিক চ্যাট", "हाल की चैट"))
            chats.forEach { chat ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(chat) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(chat.peerUid.take(16))
                    if (chat.lastMessage.isNotBlank()) Text(chat.lastMessage.take(80))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CircleDirectChatScreen(
    language: AppLanguage,
    uid: String,
    chat: CircleDirectChat,
    store: DostStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    val scope = rememberCoroutineScope()
    var messages by remember(chat.id) { mutableStateOf(emptyList<SocialChatMessage>()) }
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var reportReason by rememberSaveable(chat.id) { mutableStateOf("") }
    var safetyAction by remember { mutableStateOf("") }
    var readReceiptsEnabled by remember(uid) { mutableStateOf(store.circleReadReceiptsEnabled()) }
    var typingIndicatorsEnabled by remember(uid) { mutableStateOf(store.circleTypingIndicatorsEnabled()) }
    var typingMembers by remember(chat.id) { mutableStateOf(emptyList<Pair<String, Long>>()) }

    DisposableEffect(uid, chat.id) {
        val messageReg = repository.listenCircleDirectMessages(uid, chat.id, { notice = it.message.orEmpty() }) { messages = it }
        val typingReg = repository.listenCircleDirectTyping(uid, chat.id, { }) { typingMembers = it }
        onDispose {
            messageReg.remove()
            typingReg.remove()
            scope.launch { runCatching { repository.setCircleDirectTyping(uid, chat.id, false) } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chat.peerUid.take(16)) },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = socialLabel(language, "Chat settings", "চ্যাট সেটিংস", "चैट सेटिंग्स"))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(socialLabel(language, if (readReceiptsEnabled) "Turn off read receipts" else "Turn on read receipts", if (readReceiptsEnabled) "পড়ার তথ্য বন্ধ করুন" else "পড়ার তথ্য চালু করুন", if (readReceiptsEnabled) "रीड रसीद बंद करें" else "रीड रसीद चालू करें")) },
                            onClick = {
                                readReceiptsEnabled = !readReceiptsEnabled
                                store.setCircleReadReceiptsEnabled(readReceiptsEnabled)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(socialLabel(language, if (typingIndicatorsEnabled) "Turn off typing indicators" else "Turn on typing indicators", if (typingIndicatorsEnabled) "টাইপিং সূচক বন্ধ করুন" else "টাইপিং সূচক চালু করুন", if (typingIndicatorsEnabled) "टाइपिंग संकेत बंद करें" else "टाइपिंग संकेत चालू करें")) },
                            onClick = {
                                typingIndicatorsEnabled = !typingIndicatorsEnabled
                                store.setCircleTypingIndicatorsEnabled(typingIndicatorsEnabled)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(socialLabel(language, "Report", "রিপোর্ট করুন", "रिपोर्ट करें")) },
                            onClick = { showMenu = false; safetyAction = "report" }
                        )
                        DropdownMenuItem(
                            text = { Text(socialLabel(language, "Block", "ব্লক করুন", "ब्लॉक करें")) },
                            onClick = { showMenu = false; safetyAction = "block" }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice.isNotBlank()) Text(notice)
            if (typingIndicatorsEnabled && typingMembers.isNotEmpty()) {
                Text(socialLabel(language, "Typing…", "টাইপ করছে…", "टाइप कर रहे हैं…"))
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(messages, key = { it.id }) { message ->
                    Text(
                        (if (message.senderUid == uid) socialLabel(language, "You: ", "আপনি: ", "आप: ") else "") + message.text,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it.take(2000)
                    if (typingIndicatorsEnabled) {
                        scope.launch { runCatching { repository.setCircleDirectTyping(uid, chat.id, it.isNotBlank()) } }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(socialLabel(language, "Message", "বার্তা", "संदेश")) }
            )
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@Button
                    scope.launch {
                        try {
                            repository.sendCircleDirectMessage(uid, chat.id, text, UUID.randomUUID().toString().replace("-", "").take(32))
                            draft = ""
                            runCatching { repository.setCircleDirectTyping(uid, chat.id, false) }
                        } catch (error: Exception) {
                            notice = peopleFailureMessage(error, language, socialLabel(language, "Could not send that message.", "বার্তা পাঠানো যায়নি।", "संदेश नहीं भेजा जा सका।"))
                        }
                    }
                },
                enabled = draft.isNotBlank()
            ) { Text(socialLabel(language, "Send", "পাঠান", "भेजें")) }
        }
    }
    if (safetyAction == "report") {
        AlertDialog(
            onDismissRequest = { safetyAction = "" },
            title = { Text(socialLabel(language, "Report this chat", "এই চ্যাট রিপোর্ট করুন", "इस चैट की रिपोर्ट करें")) },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it.take(500) },
                    label = { Text(socialLabel(language, "What should we review?", "কী পর্যালোচনা করা উচিত?", "हमें क्या समीक्षा करनी चाहिए?")) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = reportReason.trim().length >= 3,
                    onClick = {
                        scope.launch {
                            try {
                                repository.reportCircleDirectContact(uid, chat.id, chat.peerUid, reportReason)
                                reportReason = ""
                                safetyAction = ""
                                Toast.makeText(context, socialLabel(language, "Report submitted for priority review.", "রিপোর্টটি অগ্রাধিকার পর্যালোচনার জন্য পাঠানো হয়েছে।", "रिपोर्ट प्राथमिकता समीक्षा के लिए भेजी गई।"), Toast.LENGTH_SHORT).show()
                            } catch (error: Exception) {
                                notice = peopleFailureMessage(error, language, socialLabel(language, "Could not submit the report.", "রিপোর্ট পাঠানো যায়নি।", "रिपोर्ट नहीं भेजी जा सकी।"))
                            }
                        }
                    }
                ) { Text(socialLabel(language, "Submit", "জমা দিন", "जमा करें")) }
            },
            dismissButton = { TextButton(onClick = { safetyAction = "" }) { Text(socialLabel(language, "Cancel", "বাতিল", "रद्द करें")) } }
        )
    }
    if (safetyAction == "block") {
        AlertDialog(
            onDismissRequest = { safetyAction = "" },
            title = { Text(socialLabel(language, "Block this person?", "এই ব্যক্তিকে ব্লক করবেন?", "इस व्यक्ति को ब्लॉक करें?")) },
            text = { Text(socialLabel(language, "They will not be able to message you in Circle.", "তারা Circle-এ আপনাকে মেসেজ করতে পারবে না।", "वे Circle में आपको संदेश नहीं भेज सकेंगे।")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            repository.blockCircleContact(uid, chat.peerUid)
                            safetyAction = ""
                            onBack()
                        } catch (error: Exception) {
                            notice = peopleFailureMessage(error, language, socialLabel(language, "Could not block this person.", "এই ব্যক্তিকে ব্লক করা যায়নি।", "इस व्यक्ति को ब्लॉक नहीं किया जा सका।"))
                        }
                    }
                }) { Text(socialLabel(language, "Block", "ব্লक করুন", "ब्लॉक करें")) }
            },
            dismissButton = { TextButton(onClick = { safetyAction = "" }) { Text(socialLabel(language, "Cancel", "বাতिल", "रद्द करें")) } }
        )
    }
}
