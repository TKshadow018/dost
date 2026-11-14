@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SystemFriendPickerScreen(
    language: AppLanguage,
    catalog: List<SystemFriendListing>,
    isLoading: Boolean,
    error: String?,
    addedCatalogIds: Set<String>,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    customLimitReached: Boolean,
    onRefresh: () -> Unit,
    onSelect: (SystemFriendListing) -> Unit,
    onCreateCustomFriend: (Friend) -> Unit,
    onManageFriends: () -> Unit,
    onBack: () -> Unit
) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val availableCatalog = availableSystemFriendDeck(catalog, addedCatalogIds)
    var showCreateFriend by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(when { bangla -> "বন্ধুরা"; hindi -> "दोस्त"; else -> "Friends" }) },
                actions = {
                    TextButton(onClick = { showCreateFriend = true }, enabled = !customLimitReached) {
                        Text(when {
                            bangla -> "নিজের বন্ধু তৈরি করুন"
                            hindi -> "अपना दोस्त बनाएँ"
                            else -> "Create your own friend"
                        }, maxLines = 1)
                    }
                },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    bangla -> "পছন্দ হলে ডানে, না হলে বামে সরান"
                    hindi -> "पसंद आए तो दाएं, नहीं तो बाएं स्वाइप करें"
                    else -> "Swipe right to meet, left to pass"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                                bangla -> "সব উপলভ্য বন্ধু যোগ করা হয়েছে। নতুন কাউকে জায়গা দিতে বন্ধু পরিচালনা করুন।"
                                hindi -> "सभी उपलब्ध दोस्त जुड़ चुके हैं। किसी नए दोस्त के लिए जगह बनाने हेतु दोस्तों को प्रबंधित करें।"
                                else -> "You’ve met all available catalog friends. Manage friends to make room for someone new."
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
                isLoading && catalog.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingSystemFriends(language)
                }
                catalog.isEmpty() && error == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MoreFriendsState(language, onRetry = onRefresh)
                }
                availableCatalog.isEmpty() && error == null && addingCatalogId == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MoreFriendsState(language, onManageFriends = onManageFriends)
                }
                availableCatalog.isNotEmpty() -> SystemFriendCardDeck(
                    catalog = availableCatalog,
                    language = language,
                    addingCatalogId = addingCatalogId,
                    systemLimitReached = systemLimitReached,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                addingCatalogId != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(when { bangla -> "বন্ধুকে যোগ করা হচ্ছে…"; hindi -> "दोस्त जोड़ा जा रहा है…"; else -> "Adding your friend…" })
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            if (error != null && catalog.isEmpty()) {
                TextButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.align(Alignment.End)) {
                    Text(when { bangla -> "আবার চেষ্টা করুন"; hindi -> "फिर कोशिश करें"; else -> "Try again" })
                }
            }
            if (showCreateFriend) AddFriendDialog(
                language = language,
                onDismiss = { showCreateFriend = false },
                onAdd = { friend ->
                    showCreateFriend = false
                    onCreateCustomFriend(friend)
                }
            )
        }
    }
}

@Composable
private fun LoadingSystemFriends(language: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
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
private fun MoreFriendsState(
    language: AppLanguage,
    onRetry: (() -> Unit)? = null,
    onManageFriends: (() -> Unit)? = null,
    onStartOver: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_auto_awesome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Text(
            when (language) {
                AppLanguage.BANGLA -> if (onStartOver != null) "সব বন্ধুকে দেখে ফেলেছেন" else "আরও বন্ধু শিগগিরই আসছে"
                AppLanguage.HINDI -> if (onStartOver != null) "आप सभी दोस्तों को देख चुके हैं" else "और दोस्त जल्द आ रहे हैं"
                AppLanguage.ENGLISH -> if (onStartOver != null) "You’ve seen all the friends" else "More friends coming soon"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            when (language) {
                AppLanguage.BANGLA -> if (onStartOver != null) "আবার শুরু করুন, অথবা বন্ধু পরিচালনা করে জায়গা খালি করুন।" else "তালিকাটি আবার লোড করে দেখুন।"
                AppLanguage.HINDI -> if (onStartOver != null) "फिर से शुरू करें या दोस्तों को प्रबंधित करके जगह बनाएँ।" else "सूची को फिर से लोड करके देखें।"
                AppLanguage.ENGLISH -> if (onStartOver != null) "Start over or manage friends to make room." else "Try loading the list again."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onStartOver?.let { onClick ->
                OutlinedButton(onClick = onClick) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "আবার শুরু"
                        AppLanguage.HINDI -> "फिर शुरू करें"
                        AppLanguage.ENGLISH -> "Start over"
                    })
                }
            }
            onRetry?.let { onClick ->
                OutlinedButton(onClick = onClick) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "আবার চেষ্টা করুন"
                        AppLanguage.HINDI -> "फिर कोशिश करें"
                        AppLanguage.ENGLISH -> "Try again"
                    })
                }
            }
            onManageFriends?.let { onClick ->
                Button(onClick = onClick) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "বন্ধু পরিচালনা"
                        AppLanguage.HINDI -> "दोस्त प्रबंधित करें"
                        AppLanguage.ENGLISH -> "Manage friends"
                    })
                }
            }
        }
    }
}

@Composable
private fun SystemFriendCardDeck(
    catalog: List<SystemFriendListing>,
    language: AppLanguage,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    onSelect: (SystemFriendListing) -> Unit,
    modifier: Modifier = Modifier
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
        MoreFriendsState(language, onStartOver = { passedIds = emptySet() })
        return
    }

    if (addingCatalogId == current.id) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(when { bangla -> "বন্ধুকে যোগ করা হচ্ছে…"; hindi -> "दोस्त जोड़ा जा रहा है…"; else -> "Adding your friend…" })
            }
        }
        return
    }

    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    Column(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val cardHeight = maxHeight.coerceAtLeast(220.dp)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                next?.let {
                    CharacterCard(
                        listing = it,
                        language = language,
                        showFullProfile = false,
                        onToggleProfile = {},
                        modifier = Modifier.fillMaxSize().padding(top = 12.dp).graphicsLayer {
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
    val interests = listing.tags.split(Regex("[,;|•\\n]+")).map(String::trim).filter(String::isNotBlank).take(4)
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
                listing.description.ifBlank { localizedText(language, "A new friend to meet", "एक नए दोस्त से मिलें", "নতুন একজন বন্ধুর সঙ্গে পরিচিত হন") }.take(120),
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
    val details = listOf(
        localizedText(language, "Category", "श्रेणी", "ধরন") to listing.category,
        localizedText(language, "About", "परिचय", "পরিচিতি") to listing.description,
        localizedText(language, "Interests", "रुचियाँ", "আগ্রহ") to listing.tags,
        localizedText(language, "How you met", "आपकी पहचान", "তোমাদের পরিচয়") to listing.relationship,
        localizedText(language, "First message", "पहला संदेश", "প্রথম বার্তা") to listing.greeting
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
