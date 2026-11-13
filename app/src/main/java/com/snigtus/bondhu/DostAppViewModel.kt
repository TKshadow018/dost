package com.snigtus.dost

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

class DostAppViewModel(application: Application) : AndroidViewModel(application) {
    val store = DostStore(application)
    val language = mutableStateOf(store.language())
    val screen = mutableStateOf(AppScreen.SPLASH)
    val registerStep = mutableStateOf(1)
    val name = mutableStateOf(store.userName())
    val email = mutableStateOf(store.userEmail())
    val age = mutableStateOf(store.userAge())
    val gender = mutableStateOf(store.userGender())
    val userTimeZoneId = mutableStateOf(store.userTimeZoneId())
    val openRouterApiKey = mutableStateOf(store.openRouterApiKey())
    val aiModel = mutableStateOf(store.aiModel())
    val friends = mutableStateListOf<Friend>()
    val selectedFriend = mutableStateOf<Friend?>(null)
    val chatDrafts = mutableStateMapOf<String, String>()
    val chatImageDrafts = mutableStateMapOf<String, String>()
    val closeAfterOverlayPermission = mutableStateOf(false)
    val bubbleFriendId = mutableStateOf<String?>(null)
    val generatingProfileIds = mutableStateOf(setOf<String>())
    val profileErrors = mutableStateOf(mapOf<String, ProfileFailure>())

    init {
        store.clearLockedLoveScores()
        friends.addAll(store.loadFriends())
        ConversationManager.initialize(application)
    }

    fun restoreNavigationState(screenName: String?, friendId: String?) {
        screen.value = screenName
            ?.let { runCatching { AppScreen.valueOf(it) }.getOrDefault(AppScreen.SPLASH) }
            ?: AppScreen.SPLASH
        selectedFriend.value = friendId?.let { id -> friends.firstOrNull { it.id == id } }
    }
}
