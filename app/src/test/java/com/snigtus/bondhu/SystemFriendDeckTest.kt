package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemFriendDeckTest {
    @Test
    fun deckSkipsAddedCharactersAndKeepsCatalogOrder() {
        val catalog = listOf(listing("first"), listing("already-added"), listing("last"))

        assertEquals(
            listOf("first", "last"),
            availableSystemFriendDeck(catalog, setOf("already-added")).map { it.id }
        )
    }

    @Test
    fun deckIsEmptyWhenEveryCharacterHasBeenAdded() {
        val catalog = listOf(listing("one"), listing("two"))

        assertEquals(emptyList<SystemFriendListing>(), availableSystemFriendDeck(catalog, setOf("one", "two")))
    }

    @Test
    fun deckHidesAddedAndPassedProfiles() {
        val catalog = listOf(listing("first"), listing("passed"), listing("added"), listing("last"))

        assertEquals(
            listOf("first", "last"),
            availableSystemFriendDeck(catalog, setOf("added"), setOf("passed")).map { it.id }
        )
    }

    @Test
    fun passedProfilesReappearAfterSevenDays() {
        val passedAtMillis = 1_000_000L

        assertTrue(isSystemFriendPassActive(passedAtMillis, passedAtMillis + SYSTEM_FRIEND_PASS_DURATION_MILLIS - 1L))
        assertFalse(isSystemFriendPassActive(passedAtMillis, passedAtMillis + SYSTEM_FRIEND_PASS_DURATION_MILLIS))
        assertFalse(isSystemFriendPassActive(passedAtMillis + 1L, passedAtMillis))
    }

    @Test
    fun passedPeopleProfilesExpireAfterNinetyDays() {
        val passedAtMillis = 1_000_000L

        assertTrue(isPeopleProfilePassActive(passedAtMillis, passedAtMillis + PEOPLE_PROFILE_PASS_DURATION_MILLIS - 1L))
        assertFalse(isPeopleProfilePassActive(passedAtMillis, passedAtMillis + PEOPLE_PROFILE_PASS_DURATION_MILLIS))
        assertFalse(isPeopleProfilePassActive(passedAtMillis + 1L, passedAtMillis))
    }

    @Test
    fun catalogSearchMatchesInterestsStyleAndLanguageCaseInsensitively() {
        val interestMatch = listing("interest-match").copy(tags = "Books and music")
        val styleMatch = listing("style-match").copy(conversationStyle = "Warm, natural, and concise")
        val languageMatch = listing("language-match").copy(conversationLanguage = AppLanguage.BANGLA)

        assertEquals(
            listOf("interest-match"),
            filterSystemFriendCatalog(listOf(interestMatch, styleMatch, languageMatch), " MUSIC ")
                .map { it.id }
        )
        assertEquals(
            listOf("style-match"),
            filterSystemFriendCatalog(listOf(interestMatch, styleMatch, languageMatch), "NATURAL")
                .map { it.id }
        )
        assertEquals(
            listOf("language-match"),
            filterSystemFriendCatalog(listOf(interestMatch, styleMatch, languageMatch), "bangla")
                .map { it.id }
        )
    }

    @Test
    fun blankCatalogSearchPreservesAllProfiles() {
        val catalog = listOf(listing("first"), listing("second"))

        assertEquals(catalog, filterSystemFriendCatalog(catalog, " \n "))
    }

    private fun listing(id: String) = SystemFriendListing(
        id = id,
        name = id,
        age = "24",
        gender = "Female",
        category = "friend",
        description = "",
        tags = "",
        relationship = "",
        greeting = "",
        photoUrl = ""
    )
}
