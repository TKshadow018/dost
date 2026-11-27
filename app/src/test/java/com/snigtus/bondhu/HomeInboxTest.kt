package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInboxTest {
    @Test
    fun exploreNavigationIsHiddenUnlessItsFeatureFlagIsEnabled() {
        assertEquals(
            listOf(MainTab.CHATS, MainTab.PROFILE),
            mainNavigationTabs(exploreEnabled = false)
        )
        assertEquals(
            listOf(MainTab.CHATS, MainTab.DISCOVER, MainTab.PROFILE),
            mainNavigationTabs(exploreEnabled = true)
        )
    }

    @Test
    fun circleInboxRemainsAvailableWhenExploreIsOff() {
        assertEquals(false, shouldShowExploreDisabledState(exploreEnabled = false, circleOnly = true))
        assertEquals(true, shouldShowExploreDisabledState(exploreEnabled = false, circleOnly = false))
        assertEquals(false, shouldShowExploreDisabledState(exploreEnabled = true, circleOnly = false))
    }

    @Test
    fun circleInboxPreservesArchivedFilter() {
        assertEquals(HomeInboxFilter.PEOPLE, circleInboxFilter(HomeInboxFilter.ALL))
        assertEquals(HomeInboxFilter.ARCHIVED, circleInboxFilter(HomeInboxFilter.ARCHIVED))
    }

    @Test
    fun teenCircleInboxShowsComingSoonWithoutAdultEligibility() {
        assertEquals(true, shouldShowCircleContactsComingSoon("teen"))
        assertEquals(false, shouldShowCircleContactsComingSoon("adult"))
        assertEquals(false, shouldShowCircleContactsComingSoon(""))
    }

    @Test
    fun circleInboxContainsOnlyPeopleConversations() {
        val person = PublicPersonProfile(
            uid = "person-uid",
            displayName = "Sam",
            ageBand = "25–34",
            ageCohort = "adult",
            gender = "man",
            country = "BD",
            bio = "",
            interests = "",
            isPublic = true
        )

        val entries = buildCircleInboxEntries(
            conversations = listOf(SocialConversation("people-chat", person, "Hello", 100L)),
            pinnedConversationIds = setOf("people:people-chat")
        )

        assertEquals(listOf("people:people-chat"), entries.map { it.key })
        assertEquals(true, entries.all { it is HomeInboxEntry.Person })
    }

    @Test
    fun combinesAiAndPeopleChatsByLatestActivity() {
        val aiFriend = Friend(id = "ai-friend", name = "Mira", age = "24", gender = "woman")
        val unusedFriend = Friend(id = "new-friend", name = "Noor", age = "24", gender = "woman")
        val person = PublicPersonProfile(
            uid = "person-uid",
            displayName = "Sam",
            ageBand = "25–34",
            ageCohort = "adult",
            gender = "man",
            country = "BD",
            bio = "",
            interests = "",
            isPublic = true
        )
        val peopleConversation = SocialConversation(
            id = "people-chat",
            peer = person,
            lastMessage = "Hello",
            lastMessageAtMillis = 200L
        )

        val entries = buildHomeInboxEntries(
            friends = listOf(aiFriend, unusedFriend),
            messagesByFriend = mapOf(
                aiFriend.id to listOf(
                    ChatMessage(role = "user", content = "Older", timestampMillis = 100L),
                    ChatMessage(role = "assistant", content = "Latest", timestampMillis = 300L)
                )
            ),
            peopleConversations = listOf(peopleConversation)
        )

        assertEquals(
            listOf("ai:ai-friend", "people:people-chat", "ai:new-friend"),
            entries.map { it.key }
        )
    }

    @Test
    fun favoriteAiFriendsBreakActivityTiesWithoutChangingRecentOrder() {
        val favorite = Friend(
            id = "favorite",
            name = "Zara",
            age = "24",
            gender = "woman",
            favorite = true
        )
        val other = Friend(id = "other", name = "Ari", age = "24", gender = "woman")
        val latest = ChatMessage(role = "assistant", content = "Hi", timestampMillis = 100L)

        val entries = buildHomeInboxEntries(
            friends = listOf(other, favorite),
            messagesByFriend = mapOf(other.id to listOf(latest), favorite.id to listOf(latest)),
            peopleConversations = emptyList()
        )

        assertEquals(listOf("ai:favorite", "ai:other"), entries.map { it.key })
    }

    @Test
    fun pinnedConversationsStayAheadOfNewerUnpinnedChats() {
        val aiFriend = Friend(id = "ai-friend", name = "Mira", age = "24", gender = "woman")
        val person = PublicPersonProfile(
            uid = "person-uid",
            displayName = "Sam",
            ageBand = "25–34",
            ageCohort = "adult",
            gender = "man",
            country = "BD",
            bio = "",
            interests = "",
            isPublic = true
        )
        val entries = buildHomeInboxEntries(
            friends = listOf(aiFriend),
            messagesByFriend = mapOf(
                aiFriend.id to listOf(ChatMessage(role = "assistant", content = "Recent", timestampMillis = 500L))
            ),
            peopleConversations = listOf(
                SocialConversation("people-chat", person, "Older", 100L)
            ),
            pinnedConversationIds = setOf("people:people-chat")
        )

        assertEquals(listOf("people:people-chat", "ai:ai-friend"), entries.map { it.key })
    }

    @Test
    fun archivedFilterSearchesArchivedChatsAndKeepsThemOutOfActiveInbox() {
        val aiFriend = Friend(id = "ai-friend", name = "Mira", age = "24", gender = "woman")
        val activeFriend = Friend(id = "active-friend", name = "Noor", age = "24", gender = "woman")
        val messages = mapOf(
            aiFriend.id to listOf(ChatMessage(role = "user", content = "Project comet", timestampMillis = 10L))
        )
        val entries = buildHomeInboxEntries(
            friends = listOf(aiFriend, activeFriend),
            messagesByFriend = messages,
            peopleConversations = emptyList()
        )
        val archivedIds = setOf("ai:ai-friend")

        assertEquals(
            listOf("ai:ai-friend"),
            filterHomeInboxEntries(
                entries,
                HomeInboxFilter.ARCHIVED,
                "comet",
                archivedIds,
                messages
            ).map { it.key }
        )
        assertEquals(
            emptyList<String>(),
            filterHomeInboxEntries(
                entries,
                HomeInboxFilter.ALL,
                "comet",
                archivedIds,
                messages
            ).map { it.key }
        )
    }
}
