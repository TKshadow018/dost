package com.snigtus.dost

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreModeTest {
    @Test
    fun exploreRequestsAreDeniedUntilTheModeIsEnabled() {
        assertFalse(exploreRequestAllowed(false))
        assertTrue(exploreRequestAllowed(true))
    }

    @Test
    fun disclosureIsRequiredOnlyOnFirstEnable() {
        assertTrue(exploreDisclosureRequired(true, false))
        assertFalse(exploreDisclosureRequired(true, true))
        assertFalse(exploreDisclosureRequired(false, false))
    }

    @Test
    fun catalogClientDoesNotMakeARequestWhenExploreIsOff() = runBlocking {
        val error = runCatching {
            SystemFriendClient(requestAllowed = { false }).getCatalog(AppLanguage.ENGLISH)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("Explore mode is turned off"))
    }

    @Test
    fun aiClientDoesNotMakeARequestWhenExploreIsOff() = runBlocking {
        val error = runCatching {
            OpenRouterClient(requestAllowed = { false }).generateFriendProfile(
                "", "", "Friend", "18", "unspecified", "friendly", AppLanguage.ENGLISH
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("Explore mode is turned off"))
    }

    @Test
    fun aiClientDoesNotLogMessagesWhenExploreIsOff() = runBlocking {
        val client = OpenRouterClient(requestAllowed = { false })
        val message = ChatMessage(role = "user", content = "hello")

        assertTrue(client.logConversationMessage("installation", "user", "friend", "friend", message))
    }
}
