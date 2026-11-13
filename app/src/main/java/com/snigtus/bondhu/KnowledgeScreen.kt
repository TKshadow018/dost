@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.snigtus.dost

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.*
import android.os.*
import android.provider.*
import java.io.*
import java.time.*
import java.time.format.*
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.*
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.*

@Composable
internal fun KnowledgeScreen(language: AppLanguage, friends: List<Friend>, store: DostStore, listState: LazyListState, friendIdFilter: String? = null, onBack: () -> Unit, onOpenChat: (Friend) -> Unit) {
    var revision by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Pair<String, UserMemory>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, UserMemory>?>(null) }
    val visibleFriends = remember(friends, friendIdFilter) {
        if (friendIdFilter == null) friends else friends.filter { it.id == friendIdFilter }
    }
    val knowledge = remember(visibleFriends, revision) {
        visibleFriends.mapNotNull { friend ->
            val memories = store.loadUserMemories(friend.id).filter { it.fact.isNotBlank() }
            if (memories.isEmpty()) null else friend to memories
        }
    }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val friendName = visibleFriends.singleOrNull()?.name
                    Text(
                        if (friendIdFilter != null && friendName != null) when {
                            bangla -> "$friendName আমার সম্পর্কে যা জানে"
                            hindi -> "$friendName मेरे बारे में क्या जानता है"
                            else -> "What $friendName remembers"
                        } else when {
                            bangla -> "কে আমার সম্পর্কে কী জানে"
                            hindi -> "मेरे बारे में कौन क्या जानता है"
                            else -> "Who knows what about me"
                        }
                    )
                },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        if (knowledge.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                Text(when { bangla -> "এখনও কোনো তথ্য শেখা হয়নি"; hindi -> "अभी तक कोई व्यक्तिगत जानकारी नहीं सीखी गई"; else -> "No personal information learned yet" })
                Text(
                    when {
                        visibleFriends.isEmpty() && bangla -> "তথ্য শিখতে আগে একটি বন্ধুর সঙ্গে চ্যাট শুরু করুন।"
                        visibleFriends.isEmpty() && hindi -> "जानकारी सीखने के लिए पहले किसी दोस्त से बातचीत शुरू करें।"
                        visibleFriends.isEmpty() -> "Add a friend and start a chat to build a personal profile."
                        bangla -> "বন্ধুর সঙ্গে কথা বললে তারা আপনার সম্পর্কে প্রাসঙ্গিক তথ্য শিখতে পারে।"
                        hindi -> "दोस्तों से बात करने पर वे आपके बारे में उपयोगी जानकारी सीख सकते हैं।"
                        else -> "Start a conversation to help a friend learn relevant details about you."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                visibleFriends.filterNot { friend -> knowledge.any { it.first.id == friend.id } }.forEach { friend ->
                    OutlinedButton(onClick = { onOpenChat(friend) }, modifier = Modifier.fillMaxWidth()) {
                        Text(when {
                            bangla -> "${friend.name}-এর সঙ্গে চ্যাট শুরু করুন"
                            hindi -> "${friend.name} से चैट शुरू करें"
                            else -> "Start a chat with ${friend.name}"
                        })
                    }
                }
                if (visibleFriends.isEmpty()) {
                    TextButton(onClick = onBack) {
                        Text(when { bangla -> "ফিরে যান"; hindi -> "वापस"; else -> "Back to settings" })
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                knowledge.forEach { (friend, memories) ->
                    item(key = "friend-${friend.id}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(friend.name, style = MaterialTheme.typography.titleLarge)
                            memories.forEach { memory ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(memoryCategoryLabel(memory.category, language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(memory.fact, style = MaterialTheme.typography.bodyLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { editing = friend.id to memory }) {
                                            Text(when { bangla -> "ঠিক করুন"; hindi -> "ठीक करें"; else -> "Correct" })
                                        }
                                        TextButton(onClick = { deleting = friend.id to memory }) {
                                            Text(when { bangla -> "মুছুন"; hindi -> "हटाएँ"; else -> "Delete" }, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { (friendId, memory) ->
        CorrectMemoryDialog(
            language = language,
            memory = memory,
            onDismiss = { editing = null },
            onSave = { updated ->
                store.updateUserMemory(friendId, memory, updated)
                editing = null
                revision += 1
            }
        )
    }
    deleting?.let { (friendId, memory) ->
        val friendName = friends.firstOrNull { it.id == friendId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(when { bangla -> "এই তথ্য মুছবেন?"; hindi -> "यह जानकारी हटाएँ?"; else -> "Delete this fact?" }) },
            text = { Text(when { bangla -> "$friendName আর এটা মনে রাখবে না।"; hindi -> "$friendName अब इसे याद नहीं रखेगा।"; else -> "$friendName will no longer remember this." }) },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteUserMemory(friendId, memory)
                    deleting = null
                    revision += 1
                }) { Text(when { bangla -> "মুছুন"; hindi -> "हटाएँ"; else -> "Delete" }, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" }) } }
        )
    }
}

@Composable
private fun CorrectMemoryDialog(language: AppLanguage, memory: UserMemory, onDismiss: () -> Unit, onSave: (UserMemory) -> Unit) {
    var category by remember(memory) { mutableStateOf(memory.category) }
    var fact by remember(memory) { mutableStateOf(memory.fact) }
    val bangla = language == AppLanguage.BANGLA
    val hindi = language == AppLanguage.HINDI
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when { bangla -> "এই তথ্য ঠিক করুন"; hindi -> "इस जानकारी को ठीक करें"; else -> "Correct this fact" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    category,
                    { category = it },
                    label = { Text(when { bangla -> "বিভাগ"; hindi -> "श्रेणी"; else -> "Category" }) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    fact,
                    { fact = it },
                    label = { Text(when { bangla -> "তথ্য"; hindi -> "जानकारी"; else -> "Fact" }) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(UserMemory(category, fact)) }, enabled = fact.isNotBlank()) {
                Text(when { bangla -> "সংরক্ষণ"; hindi -> "सहेजें"; else -> "Save" })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(when { bangla -> "বাতিল"; hindi -> "रद्द करें"; else -> "Cancel" }) } }
    )
}
