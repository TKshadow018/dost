package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemFriendExperienceTest {
    @Test
    fun initialGreetingIsAnAssistantMessageInTheFirstSession() {
        val friend = Friend("friend", "Asha", "22", "Female", greeting = "হেই!")

        val message = initialGreetingMessage(friend, emptyList(), "first-session")

        assertEquals("assistant", message?.role)
        assertEquals("greeting", message?.category)
        assertEquals("হেই!", message?.content)
        assertEquals("first-session", message?.sessionId)
    }

    @Test
    fun initialGreetingIsNotRepeatedOrCreatedWhenBlank() {
        val friend = Friend("friend", "Asha", "22", "Female", greeting = "Hello!")
        val existing = ChatMessage("user", "Hi there")

        assertNull(initialGreetingMessage(friend, listOf(existing), "session"))
        assertNull(initialGreetingMessage(friend.copy(greeting = " "), emptyList(), "session"))
    }

    @Test
    fun generatedProfileDoesNotReplaceUserDefinedPersonalityOrGreeting() {
        val friend = Friend("friend", "Asha", "22", "Female", personality = "Quietly funny", greeting = "Hello!")
        val generated = FriendProfile(
            personality = "Very outgoing",
            interests = "Photography",
            family = "",
            familyMembers = "",
            familyActivities = "",
            financialCondition = "",
            address = "",
            height = "",
            weight = "",
            facialFeatures = "",
            bodyFeatures = "",
            conversationStyle = "Warm"
        )

        val updated = friend.withGeneratedProfile(generated)

        assertEquals("Quietly funny", updated.personality)
        assertEquals("Hello!", updated.greeting)
        assertEquals("Photography", updated.interests)
    }
}
