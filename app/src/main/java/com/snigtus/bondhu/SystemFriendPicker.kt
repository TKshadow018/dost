package com.snigtus.dost

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SystemFriendPickerDialog(
    language: AppLanguage,
    catalog: List<SystemFriendListing>,
    isLoading: Boolean,
    error: String?,
    addedCatalogIds: Set<String>,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    onRefresh: () -> Unit,
    onSelect: (SystemFriendListing) -> Unit,
    onManageFriends: () -> Unit,
    onDismiss: () -> Unit
) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val availableCatalog = availableSystemFriendDeck(catalog, addedCatalogIds)
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(420).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).heightIn(max = maxHeight),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when { bangla -> "সিস্টেম বন্ধু"; hindi -> "सिस्टम दोस्त"; else -> "Meet a friend" },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            when {
                                bangla -> "পছন্দ হলে ডানে, না হলে বামে সরান"
                                hindi -> "पसंद आए तो दाएं, नहीं तो बाएं स्वाइप करें"
                                else -> "Swipe right to meet, left to pass"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(when { bangla -> "বন্ধ করুন"; hindi -> "बंद करें"; else -> "Close" })
                    }
                }

                if (systemLimitReached) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                when {
                                    bangla -> "আপনার সব সিস্টেম বন্ধু যোগ করা হয়েছে। নতুন কাউকে জায়গা দিতে বন্ধু পরিচালনা করুন।"
                                    hindi -> "आपके सभी सिस्टम दोस्त जुड़ चुके हैं। किसी नए दोस्त के लिए जगह बनाने हेतु दोस्तों को प्रबंधित करें।"
                                    else -> "You’ve met all your system friends. Manage friends to make room for someone new."
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = onManageFriends) {
                                Text(when { bangla -> "পরিচালনা"; hindi -> "प्रबंधित करें"; else -> "Manage" })
                            }
                        }
                    }
                }

                when {
                    isLoading && catalog.isEmpty() -> LoadingSystemFriends(language)
                    catalog.isEmpty() && error == null -> MoreFriendsState(language)
                    availableCatalog.isEmpty() && error == null && addingCatalogId == null -> MoreFriendsState(language)
                    availableCatalog.isNotEmpty() -> SystemFriendCardDeck(
                        catalog = availableCatalog,
                        language = language,
                        addingCatalogId = addingCatalogId,
                        systemLimitReached = systemLimitReached,
                        onSelect = onSelect
                    )
                    addingCatalogId != null -> Row(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(when { bangla -> "বন্ধুকে যোগ করা হচ্ছে…"; hindi -> "दोस्त जोड़ा जा रहा है…"; else -> "Adding your friend…" })
                    }
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (error != null && catalog.isEmpty()) {
                    TextButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) {
                        Text(when { bangla -> "আবার চেষ্টা করুন"; hindi -> "फिर कोशिश करें"; else -> "Try again" })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingSystemFriends(language: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(when (language) {
            AppLanguage.BANGLA -> "বন্ধুদের তালিকা আনা হচ্ছে…"
            AppLanguage.HINDI -> "दोस्तों की सूची लोड हो रही है…"
            AppLanguage.ENGLISH -> "Loading friends…"
        })
    }
}

@Composable
private fun MoreFriendsState(language: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("✦", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            when (language) {
                AppLanguage.BANGLA -> "আরও বন্ধু শিগগিরই আসছে"
                AppLanguage.HINDI -> "और दोस्त जल्द आ रहे हैं"
                AppLanguage.ENGLISH -> "More friends coming soon"
            },
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun SystemFriendCardDeck(
    catalog: List<SystemFriendListing>,
    language: AppLanguage,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    onSelect: (SystemFriendListing) -> Unit
) {
    var passedIds by remember { mutableStateOf(emptySet<String>()) }
    var showFullProfile by remember { mutableStateOf(false) }
    val remaining = catalog.filterNot { it.id in passedIds }
    val current = remaining.firstOrNull()
    val next = remaining.getOrNull(1)
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI

    LaunchedEffect(current?.id) { showFullProfile = false }

    if (current == null) {
        MoreFriendsState(language)
        return
    }

    if (addingCatalogId == current.id) {
        Row(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(when { bangla -> "বন্ধুকে যোগ করা হচ্ছে…"; hindi -> "दोस्त जोड़ा जा रहा है…"; else -> "Adding your friend…" })
        }
        return
    }

    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val cardHeight = (LocalConfiguration.current.screenHeightDp * 0.49f).coerceIn(300f, 390f).dp
    Box(
        modifier = Modifier.fillMaxWidth().height(cardHeight + 70.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        next?.let {
            CharacterCard(
                listing = it,
                language = language,
                showFullProfile = false,
                onToggleProfile = {},
                modifier = Modifier.fillMaxWidth().height(cardHeight).padding(top = 12.dp).graphicsLayer {
                    scaleX = 0.96f
                    scaleY = 0.96f
                }
            )
        }

        SwipeableCharacterCard(
            listing = current,
            language = language,
            showFullProfile = showFullProfile,
            onToggleProfile = { showFullProfile = !showFullProfile },
            systemLimitReached = systemLimitReached,
            isAdding = addingCatalogId != null,
            screenWidthPx = screenWidthPx,
            cardHeight = cardHeight,
            onPass = { passedIds = passedIds + current.id },
            onSelect = { onSelect(current) }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { passedIds = passedIds + current.id },
            enabled = addingCatalogId == null,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
        ) {
            Text(when { bangla -> "বাদ দিন"; hindi -> "पास करें"; else -> "Pass" })
        }
        OutlinedButton(
            onClick = { showFullProfile = !showFullProfile },
            enabled = addingCatalogId == null,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(when { bangla -> "তথ্য"; hindi -> "जानकारी"; else -> "Info" })
        }
        Button(
            onClick = { onSelect(current) },
            enabled = !systemLimitReached && addingCatalogId == null,
            modifier = Modifier.weight(1.25f).heightIn(min = 48.dp)
        ) {
            Text(when {
                bangla -> "যোগ করুন"
                hindi -> "जोड़ें"
                else -> "Add friend"
            })
        }
    }
}

@Composable
private fun SwipeableCharacterCard(
    listing: SystemFriendListing,
    language: AppLanguage,
    showFullProfile: Boolean,
    onToggleProfile: () -> Unit,
    systemLimitReached: Boolean,
    isAdding: Boolean,
    screenWidthPx: Float,
    cardHeight: androidx.compose.ui.unit.Dp,
    onPass: () -> Unit,
    onSelect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val releaseOffset = remember(listing.id) { Animatable(0f) }
    var dragOffset by remember(listing.id) { mutableFloatStateOf(0f) }
    val horizontalOffset = dragOffset + releaseOffset.value
    val threshold = (screenWidthPx * 0.24f).coerceAtLeast(88f)
    val flipAngle by animateFloatAsState(
        targetValue = if (showFullProfile) 180f else 0f,
        animationSpec = tween(380),
        label = "systemFriendProfileFlip"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .offset { IntOffset(horizontalOffset.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = (horizontalOffset / screenWidthPx.coerceAtLeast(1f) * 14f).coerceIn(-14f, 14f)
            }
            .clickable(enabled = !isAdding, onClick = onToggleProfile)
            .pointerInput(listing.id, systemLimitReached, isAdding) {
                detectDragGestures(
                    onDragEnd = {
                        val direction = when {
                            dragOffset > threshold -> 1
                            dragOffset < -threshold -> -1
                            else -> 0
                        }
                        val canCompleteSwipe = direction < 0 || !systemLimitReached && !isAdding
                        scope.launch {
                            val start = dragOffset
                            dragOffset = 0f
                            releaseOffset.snapTo(start)
                            if (direction != 0 && canCompleteSwipe) {
                                releaseOffset.animateTo(
                                    targetValue = if (direction > 0) screenWidthPx * 1.3f else -screenWidthPx * 1.3f,
                                    animationSpec = tween(durationMillis = 220)
                                )
                                if (direction > 0) onSelect() else onPass()
                            } else {
                                releaseOffset.animateTo(0f, animationSpec = spring())
                            }
                            releaseOffset.snapTo(0f)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            val start = dragOffset
                            dragOffset = 0f
                            releaseOffset.snapTo(start)
                            releaseOffset.animateTo(0f, animationSpec = spring())
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount.x).coerceIn(-screenWidthPx, screenWidthPx)
                    }
                )
            },
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationY = if (flipAngle > 90f) flipAngle - 180f else flipAngle
                cameraDistance = 16f * density
            }
        ) {
            if (flipAngle <= 90f) {
                CharacterCardFront(listing, language)
            } else {
                CharacterCardBack(listing, language)
            }
            Text(
                text = when (language) {
                    AppLanguage.BANGLA -> "পছন্দ"
                    AppLanguage.HINDI -> "पसंद"
                    AppLanguage.ENGLISH -> "LIKE"
                },
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp).graphicsLayer {
                    alpha = (horizontalOffset / threshold).coerceIn(0f, 1f)
                }.background(Color(0xFF1F8A65), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when (language) {
                    AppLanguage.BANGLA -> "বাদ"
                    AppLanguage.HINDI -> "पास"
                    AppLanguage.ENGLISH -> "PASS"
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).graphicsLayer {
                    alpha = (-horizontalOffset / threshold).coerceIn(0f, 1f)
                }.background(Color(0xFFB8414C), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CharacterCard(
    listing: SystemFriendListing,
    language: AppLanguage,
    showFullProfile: Boolean,
    onToggleProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(onClick = onToggleProfile)) {
            if (showFullProfile) CharacterCardBack(listing, language) else CharacterCardFront(listing, language)
        }
    }
}

@Composable
private fun CharacterCardFront(listing: SystemFriendListing, language: AppLanguage) {
    val interests = listing.interests.split(Regex("[,;|•\\n]+")).map(String::trim).filter(String::isNotBlank).take(4)
    Box(modifier = Modifier.fillMaxSize()) {
        FriendPhoto(listing.photoUrl, listing.name, Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.88f)),
                    startY = 120f
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "${listing.name}, ${listing.age}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(listing.gender, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Text(
                listing.personality.ifBlank { localizedText(language, "A new friend to meet", "एक नए दोस्त से मिलें", "নতুন একজন বন্ধুর সঙ্গে পরিচিত হন") }.take(120),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (interests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    interests.forEach { interest ->
                        Text(
                            interest,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardBack(listing: SystemFriendListing, language: AppLanguage) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val scheduleStart = java.time.LocalTime.of(listing.busyStartHour.coerceIn(0, 23), 0)
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
        (when { bangla -> "দৈনিক সময়সূচি"; hindi -> "दैनिक समय-सारणी"; else -> "Daily schedule" }) to "${scheduleStart.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}–${scheduleEnd.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} (${listing.timeZoneId})",
        (when { bangla -> "সময় অঞ্চল"; hindi -> "समय क्षेत्र"; else -> "Time zone" }) to listing.timeZoneId
    )
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${listing.name}, ${listing.age}", style = MaterialTheme.typography.titleLarge)
        Text(listing.gender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when { bangla -> "সম্পূর্ণ প্রোফাইল"; hindi -> "पूरी प्रोफ़ाइल"; else -> "Full profile" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        details.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun localizedText(language: AppLanguage, english: String, hindi: String, bangla: String): String = when (language) {
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
    AppLanguage.ENGLISH -> english
}
