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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
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
    catalogError: String?,
    addError: String?,
    addedCatalogIds: Set<String>,
    passedCatalogIds: Set<String>,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    customLimitReached: Boolean,
    showExplorePlus: Boolean = true,
    onRefresh: () -> Unit,
    onSelect: (SystemFriendListing) -> Unit,
    onPassCatalogFriend: (String) -> Unit,
    onStartOver: () -> Unit,
    onCreateCustomFriend: (Friend) -> Unit,
    onOpenPremium: () -> Unit,
    onManageFriends: () -> Unit,
    onBack: () -> Unit
) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val availableCatalog = availableSystemFriendDeck(catalog, addedCatalogIds)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val filteredCatalog = filterSystemFriendCatalog(availableCatalog, searchQuery)
    var showCreateFriend by remember { mutableStateOf(false) }
    var pendingSwipeSelection by remember { mutableStateOf<SystemFriendListing?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(when { bangla -> "AI বন্ধু"; hindi -> "AI दोस्त"; else -> "AI friends" }) },
                actions = {
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = when {
                                showSearch && bangla -> "খোঁজা বন্ধ করুন"
                                showSearch && hindi -> "खोज बंद करें"
                                showSearch -> "Close search"
                                bangla -> "বন্ধু খুঁজুন"
                                hindi -> "दोस्त खोजें"
                                else -> "Search friends"
                            }
                        )
                    }
                    if (!customLimitReached || showExplorePlus) {
                        TextButton(
                            onClick = {
                                if (customLimitReached) onOpenPremium() else showCreateFriend = true
                            }
                        ) {
                            Text(when {
                                customLimitReached && bangla -> "Explore Plus"
                                customLimitReached && hindi -> "Explore Plus"
                                customLimitReached -> "Explore Plus"
                                bangla -> "নিজের বন্ধু তৈরি করুন"
                                hindi -> "अपना दोस्त बनाएँ"
                                else -> "Create your own friend"
                            }, maxLines = 1)
                        }
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
                    bangla -> "যোগ করতে ডানে সরান (নিশ্চিত করুন), এড়াতে বামে"
                    hindi -> "जोड़ने के लिए दाएँ स्वाइप करें (पुष्टि करें), छोड़ने के लिए बाएँ"
                    else -> "Swipe right to add (confirm next), or left to pass"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    when {
                        bangla -> "এগুলো কাল্পনিক AI চরিত্র, বাস্তব মানুষ নয়। AI-এর উত্তর ভুল হতে পারে।"
                        hindi -> "ये काल्पनिक AI पात्र हैं, असली लोग नहीं। AI के जवाब गलत हो सकते हैं।"
                        else -> "These are fictional AI characters, not real people. AI replies may be inaccurate."
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    enabled = addingCatalogId == null,
                    singleLine = true,
                    label = {
                        Text(when {
                            bangla -> "নাম, আগ্রহ বা কথোপকথনের ধরন খুঁজুন"
                            hindi -> "नाम, रुचि या बातचीत की शैली खोजें"
                            else -> "Search name, interests, or conversation style"
                        })
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = localizedText(language, "Clear search", "खोज साफ़ करें", "সার্চ মুছুন")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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
                                bangla -> if (showExplorePlus) "বিনামূল্যের ক্যাটালগ বন্ধু সীমায় পৌঁছেছেন। সীমা বাড়াতে Explore Plus দেখুন।" else "বিনামূল্যের ক্যাটালগ বন্ধু সীমায় পৌঁছেছেন।"
                                hindi -> if (showExplorePlus) "मुफ़्त कैटलॉग दोस्त सीमा पूरी हो गई है। सीमा बढ़ाने के लिए Explore Plus देखें।" else "मुफ़्त कैटलॉग दोस्त सीमा पूरी हो गई है।"
                                else -> if (showExplorePlus) "You’ve reached the free catalog-friend limit. Explore Plus removes the limits." else "You’ve reached the free catalog-friend limit."
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (showExplorePlus) TextButton(onClick = onOpenPremium) {
                            Text("Explore Plus")
                        }
                    }
                }
            }

            if (isLoading && catalog.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        when {
                            bangla -> "AI বন্ধুদের তালিকা হালনাগাদ হচ্ছে…"
                            hindi -> "AI दोस्तों की सूची रीफ़्रेश हो रही है…"
                            else -> "Refreshing the AI friend list…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (catalogError != null && catalog.isNotEmpty()) {
                CatalogRefreshFailedNotice(language, isLoading, onRefresh)
            }

            when {
                isLoading && catalog.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingSystemFriends(language)
                }
                catalog.isEmpty() && catalogError != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CatalogUnavailableState(language, isLoading, onRefresh)
                }
                catalog.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MoreFriendsState(language, onRetry = onRefresh)
                }
                availableCatalog.isEmpty() && addingCatalogId == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MoreFriendsState(language, onManageFriends = onManageFriends)
                }
                searchQuery.isNotBlank() && filteredCatalog.isEmpty() -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    NoSystemFriendMatches(language) { searchQuery = "" }
                }
                availableCatalog.isNotEmpty() -> SystemFriendCardDeck(
                    catalog = filteredCatalog,
                    language = language,
                    addingCatalogId = addingCatalogId,
                    systemLimitReached = systemLimitReached,
                    initialPassedIds = passedCatalogIds,
                    onSelect = onSelect,
                    onSwipeSelect = { pendingSwipeSelection = it },
                    onPass = onPassCatalogFriend,
                    onStartOver = onStartOver,
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

            addError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            if (showCreateFriend) AddFriendDialog(
                language = language,
                onDismiss = { showCreateFriend = false },
                onAdd = { friend ->
                    showCreateFriend = false
                    onCreateCustomFriend(friend)
                }
            )
            pendingSwipeSelection?.let { listing ->
                AlertDialog(
                    onDismissRequest = { pendingSwipeSelection = null },
                    title = {
                        Text(when {
                            bangla -> "${listing.name}-কে যোগ করবেন?"
                            hindi -> "क्या ${listing.name} को जोड़ें?"
                            else -> "Add ${listing.name}?"
                        })
                    },
                    text = {
                        Text(when {
                            bangla -> "এই বন্ধুটিকে আপনার তালিকায় যোগ করবেন? পরে সরাতে পারবেন।"
                            hindi -> "इस दोस्त को अपनी सूची में जोड़ें? आप इसे बाद में हटा सकते हैं।"
                            else -> "Add this friend to your list? You can remove them later."
                        })
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingSwipeSelection = null
                            onSelect(listing)
                        }) {
                            Text(when { bangla -> "যোগ করুন"; hindi -> "जोड़ें"; else -> "Add friend" })
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingSwipeSelection = null }) {
                            Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" })
                        }
                    }
                )
            }
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
private fun CatalogUnavailableState(language: AppLanguage, isLoading: Boolean, onRetry: () -> Unit) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            when {
                bangla -> "AI বন্ধুদের তালিকা পাওয়া যাচ্ছে না"
                hindi -> "AI दोस्तों की सूची उपलब्ध नहीं है"
                else -> "AI friend catalog unavailable"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            when {
                bangla -> "আবার চেষ্টা করুন, অথবা ফিরে যান। এতে আপনার বর্তমান বন্ধুদের কোনো পরিবর্তন হবে না।"
                hindi -> "फिर कोशिश करें या वापस जाएँ। इससे आपके मौजूदा दोस्तों में कोई बदलाव नहीं होगा।"
                else -> "Try again or go back. Your existing friends won't be changed."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRetry, enabled = !isLoading) {
            Text(when {
                bangla -> "আবার চেষ্টা করুন"
                hindi -> "फिर कोशिश करें"
                else -> "Try again"
            })
        }
    }
}

@Composable
private fun CatalogRefreshFailedNotice(language: AppLanguage, isLoading: Boolean, onRetry: () -> Unit) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                when {
                    bangla -> "তালিকা হালনাগাদ করা যায়নি"
                    hindi -> "सूची रीफ़्रेश नहीं हो सकी"
                    else -> "Couldn't refresh the list"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        bangla -> "আগের লোড করা তালিকা দেখানো হচ্ছে; এটি পুরোনো হতে পারে।"
                        hindi -> "पहले लोड की गई सूची दिख रही है; यह पुरानी हो सकती है।"
                        else -> "Showing the previously loaded list; it may be out of date."
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onRetry, enabled = !isLoading) {
                    Text(when {
                        bangla -> "আবার চেষ্টা"
                        hindi -> "फिर कोशिश"
                        else -> "Retry"
                    })
                }
            }
        }
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
                AppLanguage.BANGLA -> if (onStartOver != null) "সব বন্ধুকে দেখে ফেলেছেন" else "এখন কোনো AI বন্ধু উপলভ্য নেই"
                AppLanguage.HINDI -> if (onStartOver != null) "आप सभी दोस्तों को देख चुके हैं" else "अभी कोई AI दोस्त उपलब्ध नहीं है"
                AppLanguage.ENGLISH -> if (onStartOver != null) "You’ve seen all the friends" else "No AI friends are available right now"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            when (language) {
                AppLanguage.BANGLA -> if (onStartOver != null) "আবার শুরু করুন, অথবা বন্ধু পরিচালনা করে জায়গা খালি করুন।" else "তালিকাটি আবার চেষ্টা করুন, অথবা ফিরে যান।"
                AppLanguage.HINDI -> if (onStartOver != null) "फिर से शुरू करें या दोस्तों को प्रबंधित करके जगह बनाएँ।" else "सूची फिर से देखें या वापस जाएँ।"
                AppLanguage.ENGLISH -> if (onStartOver != null) "Start over or manage friends to make room." else "Retry the list or go back."
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
private fun NoSystemFriendMatches(language: AppLanguage, onClearSearch: () -> Unit) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            when {
                bangla -> "কোনো AI বন্ধু মেলেনি"
                hindi -> "कोई AI दोस्त मेल नहीं खाता"
                else -> "No AI friends match"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            when {
                bangla -> "অন্য নাম, আগ্রহ বা কথোপকথনের ধরন খুঁজুন।"
                hindi -> "दूसरा नाम, रुचि या बातचीत की शैली खोजें।"
                else -> "Try a different name, interest, or conversation style."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onClearSearch) {
            Text(when {
                bangla -> "সার্চ মুছুন"
                hindi -> "खोज साफ़ करें"
                else -> "Clear search"
            })
        }
    }
}

@Composable
private fun SystemFriendCardDeck(
    catalog: List<SystemFriendListing>,
    language: AppLanguage,
    addingCatalogId: String?,
    systemLimitReached: Boolean,
    initialPassedIds: Set<String>,
    onSelect: (SystemFriendListing) -> Unit,
    onSwipeSelect: (SystemFriendListing) -> Unit,
    onPass: (String) -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passedIds by remember(catalog, initialPassedIds) { mutableStateOf(initialPassedIds) }
    var showFullProfile by remember { mutableStateOf(false) }
    val remaining = availableSystemFriendDeck(catalog, emptySet(), passedIds)
    val current = remaining.firstOrNull()
    val next = remaining.getOrNull(1)
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI

    LaunchedEffect(current?.id) { showFullProfile = false }

    if (current == null) {
        MoreFriendsState(language, onStartOver = {
            passedIds = emptySet()
            onStartOver()
        })
        return
    }

    fun passCurrentFriend() {
        passedIds = passedIds + current.id
        onPass(current.id)
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
                    onPass = ::passCurrentFriend,
                    onSelect = { onSwipeSelect(current) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = ::passCurrentFriend,
                enabled = addingCatalogId == null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = localizedText(language, "Pass", "पास करें", "বাদ দিন"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(
                onClick = { showFullProfile = !showFullProfile },
                enabled = addingCatalogId == null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = localizedText(language, "Info", "जानकारी", "তথ্য"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = { onSelect(current) },
                enabled = !systemLimitReached && addingCatalogId == null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = localizedText(language, "Add friend", "दोस्त जोड़ें", "বন্ধু যোগ করুন"),
                    tint = MaterialTheme.colorScheme.primary
                )
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
                InterestPills(interests)
            }
        }
    }
}

@Composable
private fun InterestPills(interests: List<String>) {
    val density = LocalDensity.current
    Layout(
        content = {
                    interests.forEach { interest ->
                        Text(
                            interest,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val gap = with(density) { 6.dp.roundToPx() }
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val positions = ArrayList<Pair<Placeable, IntOffset>>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var widestRow = 0

        placeables.forEach { placeable ->
            if (x > 0 && x + gap + placeable.width > constraints.maxWidth) {
                widestRow = maxOf(widestRow, x)
                y += rowHeight + gap
                x = 0
                rowHeight = 0
            }
            val itemX = if (x == 0) 0 else x + gap
            positions += placeable to IntOffset(itemX, y)
            x = itemX + placeable.width
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        widestRow = maxOf(widestRow, x)

        val width = widestRow.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (if (placeables.isEmpty()) 0 else y + rowHeight)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            positions.forEach { (placeable, position) ->
                placeable.placeRelative(position.x, position.y)
            }
        }
    }
}

@Composable
private fun CharacterCardBack(listing: SystemFriendListing, language: AppLanguage) {
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    val chatLanguageName = when (listing.conversationLanguage) {
        AppLanguage.ENGLISH -> localizedText(language, "English", "अंग्रेज़ी", "ইংরেজি")
        AppLanguage.BANGLA -> localizedText(language, "Bangla", "बांग्ला", "বাংলা")
        AppLanguage.HINDI -> localizedText(language, "Hindi", "हिंदी", "হিন্দি")
    }
    val details = listOf(
        localizedText(language, "Personality", "व्यक्तित्व", "ব্যক্তিত্ব") to listing.personality,
        localizedText(language, "Category", "श्रेणी", "ধরন") to listing.category,
        localizedText(language, "About", "परिचय", "পরিচিতি") to listing.description,
        localizedText(language, "Interests", "रुचियाँ", "আগ্রহ") to listing.tags,
        localizedText(language, "Conversation style", "बातचीत की शैली", "কথোপকথনের ধরন") to listing.conversationStyle,
        localizedText(language, "Chat language", "चैट की भाषा", "চ্যাটের ভাষা") to chatLanguageName,
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
