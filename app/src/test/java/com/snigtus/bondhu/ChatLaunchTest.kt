package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatLaunchTest {
    private val friends = setOf("friend-a", "friend-b")

    @Test
    fun notificationOpensTheNamedFriend() {
        assertEquals("friend-a", ChatLaunch.friendToOpen(hasUserProfile = true, "friend-a", friends))
        assertEquals("friend-b", ChatLaunch.friendToOpen(hasUserProfile = true, "friend-b", friends))
    }

    @Test
    fun missingOrBlankFriendDoesNotOpenChat() {
        assertNull(ChatLaunch.friendToOpen(hasUserProfile = true, null, friends))
        assertNull(ChatLaunch.friendToOpen(hasUserProfile = true, "  ", friends))
        assertNull(ChatLaunch.friendToOpen(hasUserProfile = true, "deleted", friends))
    }

    @Test
    fun registrationIsRequiredBeforeANotificationCanOpenChat() {
        assertNull(ChatLaunch.friendToOpen(hasUserProfile = false, "friend-a", friends))
    }
}
