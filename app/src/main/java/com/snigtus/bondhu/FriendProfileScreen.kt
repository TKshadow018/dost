package com.snigtus.dost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendProfileScreen(
    friend: Friend,
    language: AppLanguage,
    userAge: String,
    userTimeZoneId: String,
    memoryCount: Int,
    plannedEvents: List<PlannedEvent>,
    sharedMoments: List<SharedMoment>,
    anonymousTelemetryEnabled: Boolean,
    proactivePausedUntil: Long,
    onBack: () -> Unit,
    onOpenMemories: () -> Unit,
    onTimeZoneChange: (String) -> Unit,
    onSaveSchedule: (List<RoutineBlock>, BusyReplyBehavior, Int) -> Unit,
    onSaveReminder: (PlannedEvent) -> Unit,
    onSaveMoment: (SharedMoment) -> Unit,
    onDeleteMoment: (String) -> Unit,
    onPauseProactive: (Long) -> Unit,
    onResumeProactive: () -> Unit
) {
    var showScheduleEditor by remember(friend.id) { mutableStateOf(false) }
    var showReminderEditor by remember(friend.id) { mutableStateOf(false) }
    var editingReminder by remember(friend.id) { mutableStateOf<PlannedEvent?>(null) }
    var localEvents by remember(friend.id, plannedEvents) { mutableStateOf(plannedEvents) }
    var localMoments by remember(friend.id, sharedMoments) { mutableStateOf(sharedMoments) }
    var localPauseUntil by remember(friend.id) { mutableLongStateOf(proactivePausedUntil) }
    var currentTimeMillis by remember(friend.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(friend.id) {
        AnonymousTelemetry.record(anonymousTelemetryEnabled, AnonymousTelemetryEvent.FRIEND_PROFILE_OPENED)
        while (true) {
            kotlinx.coroutines.delay(60_000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(friend.name) },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FriendPhoto(friend.photoUri, friend.name, Modifier.size(72.dp).clip(CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(friend.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${friendshipLevel(friend.friendshipScore, language)} · ${friend.friendshipScore}/100",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ProfileSection(profileLabel(language, "About", "পরিচিতি", "परिचय")) {
                ProfileDetail(profileLabel(language, "Age", "বয়স", "उम्र"), friend.age)
                ProfileDetail(profileLabel(language, "Gender", "লিঙ্গ", "लिंग"), friend.gender)
                ProfileDetail(profileLabel(language, "Personality", "ব্যক্তিত্ব", "व्यक्तित्व"), friend.personality)
                ProfileDetail(profileLabel(language, "Interests", "আগ্রহ", "रुचियाँ"), friend.interests)
                ProfileDetail(profileLabel(language, "Conversation style", "কথোপকথনের ধরন", "बातचीत की शैली"), friend.conversationStyle)
                ProfileDetail(profileLabel(language, "How you met", "কীভাবে পরিচয়", "कैसे मिले"), friend.relationship)
                ProfileDetail(profileLabel(language, "First greeting", "প্রথম শুভেচ্ছা", "पहला अभिवादन"), friend.greeting)
            }

            ProfileSection(profileLabel(language, "Daily routine", "দৈনন্দিন রুটিন", "रोज़मर्रा की दिनचर्या")) {
                friend.effectiveRoutineBlocks().forEach { block ->
                    val start = java.time.LocalTime.of(block.startMinute / 60, block.startMinute % 60)
                    val end = start.plusMinutes(block.durationMinutes.toLong())
                    ProfileDetail(
                        block.reason.ifBlank { profileLabel(language, "Routine", "রুটিন", "दिनचर्या") },
                        "${start.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}–${end.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} · ${routineDaysText(block.daysOfWeek, language)}"
                    )
                }
                ProfileDetail(
                    profileLabel(language, "Reply while busy", "ব্যস্ত থাকলে উত্তর", "व्यस्त होने पर जवाब"),
                    when (friend.busyReplyBehavior) {
                        BusyReplyBehavior.WAIT -> profileLabel(language, "After the routine ends", "রুটিন শেষ হলে", "दिनचर्या समाप्त होने के बाद")
                        BusyReplyBehavior.IMMEDIATE -> profileLabel(language, "Immediately, with a brief busy note", "ব্যস্ততার কথা জানিয়ে এখনই", "व्यस्त होने की बात बताकर तुरंत")
                    }
                )
                TimeZonePicker(
                    profileLabel(language, "Friend's time zone", "বন্ধুর সময় অঞ্চল", "दोस्त का समय क्षेत्र"),
                    friend.timeZoneId,
                    onTimeZoneChange
                )
                OutlinedButton(onClick = { showScheduleEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(profileLabel(language, "Edit schedule", "সময়সূচি সম্পাদনা", "समय-सारणी बदलें"))
                }
            }

            ProfileSection(profileLabel(language, "Planned events", "পরিকল্পিত ঘটনা", "आने वाले कार्यक्रम")) {
                Text(
                    profileLabel(
                        language,
                        "Add something you want this friend to check in about. Reminders are sent in chat at the time you choose.",
                        "যে বিষয় নিয়ে বন্ধুটি খোঁজ নিক তা যোগ করুন। নির্ধারিত সময়ে চ্যাটে অনুস্মারক পাঠানো হবে।",
                        "जिस बात पर दोस्त हालचाल पूछे, उसे जोड़ें। चुने समय पर चैट में याद दिलाई जाएगी।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                localEvents.filter { it.state != PlannedEventState.DISMISSED }
                    .sortedBy { it.dueAtMillis }
                    .forEach { event ->
                        val dueAt = java.time.Instant.ofEpochMilli(event.dueAtMillis)
                            .atZone(validZoneId(userTimeZoneId))
                            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a"))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "$dueAt · ${when (event.state) {
                                    PlannedEventState.UPCOMING -> if (event.dueAtMillis < currentTimeMillis) {
                                        profileLabel(language, "Overdue", "সময় পেরিয়েছে", "समय निकल गया")
                                    } else {
                                        profileLabel(language, "Upcoming", "আসন্ন", "आने वाला")
                                    }
                                    PlannedEventState.COMPLETED -> profileLabel(language, "Done", "সম্পন্ন", "पूरा हुआ")
                                    PlannedEventState.DISMISSED -> ""
                                }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (event.state == PlannedEventState.UPCOMING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            editingReminder = event
                                            showReminderEditor = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(profileLabel(language, "Edit", "সম্পাদনা", "बदलें"))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val completed = event.copy(state = PlannedEventState.COMPLETED)
                                            localEvents = localEvents.map { if (it.id == event.id) completed else it }
                                            val moment = completedEventMoment(completed)
                                            localMoments = localMoments.filterNot { it.id == moment.id } + moment
                                            onSaveReminder(completed)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(profileLabel(language, "Done", "সম্পন্ন", "पूरा हुआ"))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val dismissed = event.copy(state = PlannedEventState.DISMISSED)
                                            localEvents = localEvents.map { if (it.id == event.id) dismissed else it }
                                            onSaveReminder(dismissed)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(profileLabel(language, "Dismiss", "বাদ দিন", "हटाएँ"))
                                    }
                                }
                            }
                        }
                    }
                Button(
                    enabled = localEvents.size < 100 || editingReminder != null,
                    onClick = {
                        editingReminder = null
                        showReminderEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(profileLabel(language, "Add planned event", "পরিকল্পিত ঘটনা যোগ করুন", "कार्यक्रम जोड़ें"))
                }
            }

            ProfileSection(profileLabel(language, "Shared moments", "একসঙ্গে কাটানো মুহূর্ত", "साझा यादें")) {
                if (localMoments.isEmpty()) {
                    Text(
                        profileLabel(
                            language,
                            "Relationship milestones and moments you save from chat will appear here.",
                            "সম্পর্কের মাইলফলক ও চ্যাট থেকে সংরক্ষিত মুহূর্ত এখানে দেখা যাবে।",
                            "रिश्ते के पड़ाव और चैट से सेव की गई यादें यहाँ दिखेंगी।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                localMoments.sortedWith(compareByDescending<SharedMoment> { it.pinned }.thenByDescending { it.createdAtMillis })
                    .forEach { moment ->
                        val stageTitle = moment.stageIndex?.let { friendshipLevel(friendshipStageScore(it), language) }
                        val title = if (moment.kind == "relationship_stage" && stageTitle != null) {
                            when (language) {
                                AppLanguage.BANGLA -> "বন্ধুত্বের নতুন ধাপ: $stageTitle"
                                AppLanguage.HINDI -> "दोस्ती का नया पड़ाव: $stageTitle"
                                AppLanguage.ENGLISH -> "Friendship reached $stageTitle"
                            }
                        } else {
                            moment.title
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            val momentDetail = if (moment.kind == "relationship_stage") {
                                profileLabel(
                                    language,
                                    "A conversation helped your friendship reach a new stage.",
                                    "একটি কথোপকথন আপনাদের বন্ধুত্বকে নতুন ধাপে এগিয়ে নিতে সাহায্য করেছে।",
                                    "एक बातचीत ने आपकी दोस्ती को नए पड़ाव तक पहुँचाने में मदद की।"
                                )
                            } else {
                                moment.detail
                            }
                            if (momentDetail.isNotBlank()) {
                                Text(momentDetail, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                java.time.Instant.ofEpochMilli(moment.createdAtMillis)
                                    .atZone(validZoneId(userTimeZoneId))
                                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val updated = moment.copy(pinned = !moment.pinned)
                                        localMoments = localMoments.map { if (it.id == moment.id) updated else it }
                                        onSaveMoment(updated)
                                    }
                                ) {
                                    Text(if (moment.pinned) {
                                        profileLabel(language, "Unpin", "পিন সরান", "पिन हटाएँ")
                                    } else {
                                        profileLabel(language, "Pin", "পিন করুন", "पिन करें")
                                    })
                                }
                                TextButton(
                                    onClick = {
                                        localMoments = localMoments.filterNot { it.id == moment.id }
                                        onDeleteMoment(moment.id)
                                    }
                                ) {
                                    Text(profileLabel(language, "Remove", "সরান", "हटाएँ"), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
            }

            ProfileSection(profileLabel(language, "Friend-initiated messages", "বন্ধুর শুরু করা বার্তা", "दोस्त के शुरू किए संदेश")) {
                Text(
                    profileLabel(
                        language,
                        "Pause or snooze greetings and automatic check-ins. Replies to messages you send are not paused.",
                        "শুভেচ্ছা ও স্বয়ংক্রিয় খোঁজ নেওয়া বিরতি বা পরে করতে পারেন। আপনার পাঠানো বার্তার উত্তর বন্ধ হবে না।",
                        "अभिवादन और स्वचालित हालचाल रोकें या बाद के लिए रखें। आपके भेजे संदेशों के जवाब नहीं रुकेंगे।"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (localPauseUntil > System.currentTimeMillis()) {
                    Text(
                        if (localPauseUntil == Long.MAX_VALUE) {
                            profileLabel(language, "Paused until you resume", "আবার চালু না করা পর্যন্ত বিরতি", "आपके फिर शुरू करने तक रुका")
                        } else {
                            profileLabel(language, "Snoozed for 24 hours", "২৪ ঘণ্টার জন্য বিরতি", "24 घंटे के लिए स्थगित")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            localPauseUntil = 0L
                            onResumeProactive()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profileLabel(language, "Resume messages", "বার্তা চালু করুন", "संदेश फिर शुरू करें"))
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            localPauseUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                            onPauseProactive(localPauseUntil)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profileLabel(language, "Snooze for 24 hours", "২৪ ঘণ্টার জন্য বিরতি", "24 घंटे के लिए स्थगित"))
                    }
                    OutlinedButton(
                        onClick = {
                            localPauseUntil = Long.MAX_VALUE
                            onPauseProactive(Long.MAX_VALUE)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profileLabel(language, "Pause until I resume", "আমি চালু না করা পর্যন্ত বিরতি", "मेरे फिर शुरू करने तक रोकें"))
                    }
                }
            }

            ProfileSection(profileLabel(language, "Your relationship", "আপনাদের সম্পর্ক", "आपका रिश्ता")) {
                Text(
                    "${friendshipLevel(friend.friendshipScore, language)} · ${friend.friendshipScore}/100",
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = { friend.friendshipScore.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                if (romanceAllowed(userAge, friend.age)) {
                    val loveStatus = when {
                        friend.friendshipScore <= 70 -> profileLabel(language, "Unlocks as your friendship grows", "বন্ধুত্ব বাড়লে খুলবে", "दोस्ती बढ़ने पर खुलेगा")
                        else -> "${loveLevel(friend.loveScore)} · ${friend.loveScore}/100"
                    }
                    Text(
                        "${profileLabel(language, "Love", "ভালোবাসা", "प्यार")}: $loveStatus",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (friend.friendshipScore > 70) {
                        LinearProgressIndicator(
                            progress = { friend.loveScore.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        profileLabel(
                            language,
                            "Romance is unavailable unless both you and your friend are 18 or older.",
                            "তুমি এবং তোমার বন্ধু দুজনের বয়স ১৮ বা তার বেশি না হলে রোমান্স বন্ধ থাকবে।",
                            "रोमांस तभी उपलब्ध है जब आप और आपका दोस्त दोनों 18 या उससे बड़े हों।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ProfileSection(profileLabel(language, "What this friend remembers", "এই বন্ধু যা মনে রেখেছে", "यह दोस्त क्या याद रखता है")) {
                Text(
                    when {
                        memoryCount == 0 -> profileLabel(language, "No saved memories yet.", "এখনও কোনো স্মৃতি সংরক্ষিত নেই।", "अभी कोई याद सेव नहीं है।")
                        else -> profileLabel(
                            language,
                            "$memoryCount saved ${if (memoryCount == 1) "memory" else "memories"}",
                            "${memoryCount}টি স্মৃতি সংরক্ষিত",
                            "$memoryCount यादें सेव हैं"
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenMemories, modifier = Modifier.fillMaxWidth()) {
                    Text(profileLabel(language, "Open memories", "স্মৃতি দেখুন", "यादें खोलें"))
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
    if (showScheduleEditor) {
        FriendScheduleDialog(
            friend = friend,
            language = language,
            onSave = onSaveSchedule,
            onDismiss = { showScheduleEditor = false }
        )
    }
    if (showReminderEditor) {
        ReminderEditorDialog(
            language = language,
            userTimeZoneId = userTimeZoneId,
            existing = editingReminder,
            onDismiss = {
                showReminderEditor = false
                editingReminder = null
            },
            onSave = { event ->
                localEvents = (localEvents.filterNot { it.id == event.id } + event).sortedBy { it.dueAtMillis }
                onSaveReminder(event)
                showReminderEditor = false
                editingReminder = null
            }
        )
    }
}

@Composable
internal fun ReminderEditorDialog(
    language: AppLanguage,
    userTimeZoneId: String,
    existing: PlannedEvent?,
    confirmSuggestion: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (PlannedEvent) -> Unit
) {
    val initialDateTime = remember(existing?.id) {
        existing?.dueAtMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(validZoneId(userTimeZoneId)).toLocalDateTime() }
            ?: java.time.LocalDateTime.now().plusHours(1)
    }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var date by remember(existing?.id) { mutableStateOf(initialDateTime.toLocalDate().toString()) }
    var time by remember(existing?.id) {
        mutableStateOf(initialDateTime.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
    }
    var confirmed by remember(existing?.id) { mutableStateOf(existing?.confidence != "uncertain") }
    var followUpRequested by remember(existing?.id) { mutableStateOf(existing?.followUpRequested ?: true) }
    val dueAtMillis = runCatching {
        val timeParts = time.split(':').mapNotNull(String::toIntOrNull)
        require(timeParts.size == 2 && timeParts[0] in 0..23 && timeParts[1] in 0..59)
        val localDateTime = java.time.LocalDate.parse(date).atTime(java.time.LocalTime.of(timeParts[0], timeParts[1]))
        val zone = validZoneId(userTimeZoneId)
        require(zone.rules.getValidOffsets(localDateTime).isNotEmpty())
        localDateTime.atZone(zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
    val valid = description.trim().isNotEmpty() && description.length <= 500 &&
        dueAtMillis != null && dueAtMillis > System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    confirmSuggestion -> profileLabel(language, "Confirm reminder", "অনুস্মারক নিশ্চিত করুন", "रिमाइंडर की पुष्टि करें")
                    existing == null -> profileLabel(language, "Add planned event", "পরিকল্পিত ঘটনা যোগ করুন", "कार्यक्रम जोड़ें")
                    else -> profileLabel(language, "Edit planned event", "পরিকল্পিত ঘটনা সম্পাদনা করুন", "कार्यक्रम बदलें")
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text(profileLabel(language, "What should they check in about?", "কোন বিষয়ে খোঁজ নেবে?", "किस बात पर हालचाल पूछे?")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(profileLabel(language, "Date (YYYY-MM-DD)", "তারিখ (YYYY-MM-DD)", "तारीख (YYYY-MM-DD)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text(profileLabel(language, "Time (24-hour)", "সময় (২৪-ঘণ্টা)", "समय (24 घंटे)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text(
                        profileLabel(
                            language,
                            "The date and details are confirmed",
                            "তারিখ ও বিবরণ নিশ্চিত",
                            "तारीख और जानकारी पक्की है"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = followUpRequested, onCheckedChange = { followUpRequested = it })
                    Text(
                        profileLabel(language, "Send a chat check-in", "চ্যাটে খোঁজ নিতে বলুন", "चैट में हालचाल पूछे"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (dueAtMillis == null || dueAtMillis <= System.currentTimeMillis()) {
                    Text(
                        profileLabel(
                            language,
                            "Enter a valid future date and time that exists in your time zone.",
                            "আপনার সময় অঞ্চলে বৈধ ভবিষ্যৎ তারিখ ও সময় দিন।",
                            "अपने समय क्षेत्र में मौजूद भविष्य की सही तारीख और समय दर्ज करें।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        PlannedEvent(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            description = description.trim(),
                            dueAtMillis = dueAtMillis ?: return@TextButton,
                            confidence = if (confirmed) "confirmed" else "uncertain",
                            followUpRequested = followUpRequested,
                            state = PlannedEventState.UPCOMING
                        )
                    )
                }
            ) {
                Text(profileLabel(language, "Save", "সংরক্ষণ", "सेव करें"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(profileLabel(language, "Cancel", "বাতিল", "रद्द करें"))
            }
        }
    )
}

private fun routineDaysText(days: Set<Int>, language: AppLanguage): String {
    val labels = when (language) {
        AppLanguage.BANGLA -> listOf("সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি", "রবি")
        AppLanguage.HINDI -> listOf("सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि", "रवि")
        AppLanguage.ENGLISH -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    return days.sorted().mapNotNull { day -> labels.getOrNull(day - 1) }.joinToString(", ")
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProfileDetail(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun profileLabel(language: AppLanguage, english: String, bangla: String, hindi: String): String = when (language) {
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
    AppLanguage.ENGLISH -> english
}
