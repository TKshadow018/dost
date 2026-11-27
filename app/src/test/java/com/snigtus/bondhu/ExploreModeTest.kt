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
    fun discoveryAndExplorePlusAreAdultsOnly() {
        assertFalse(adultExploreFeatureAllowed(true, "17"))
        assertFalse(adultExploreFeatureAllowed(false, "18"))
        assertTrue(adultExploreFeatureAllowed(true, "18"))
        assertFalse(adultExploreFeatureAllowed(true, "unknown"))
    }

    @Test
    fun teenAiSafetyAppliesOnlyToTeenProfiles() {
        assertTrue(teenAiSafetyRequired("13"))
        assertTrue(teenAiSafetyRequired("17"))
        assertFalse(teenAiSafetyRequired("12"))
        assertFalse(teenAiSafetyRequired("18"))
        assertFalse(teenAiSafetyRequired(""))
    }

    @Test
    fun teensCannotUseAdultAiCharacters() {
        assertTrue(aiFriendAgeAllowed("16", "17"))
        assertFalse(aiFriendAgeAllowed("16", "18"))
        assertTrue(aiFriendAgeAllowed("18", "18"))
        assertFalse(aiFriendAgeAllowed("12", "17"))
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
    fun aiClientDoesNotLogMessagesWhenExploreIsOff() = runBlocking {
        val client = OpenRouterClient(requestAllowed = { false })
        val message = ChatMessage(role = "user", content = "hello")

        assertTrue(client.logConversationMessage("installation", "user", "friend", "friend", message))
    }
}
