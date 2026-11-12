package com.snigtus.dost

import org.junit.Assert.assertEquals
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
