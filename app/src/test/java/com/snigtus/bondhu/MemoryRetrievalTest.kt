package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRetrievalTest {
    @Test
    fun legacyFactsAreProjectedIntoStructuredProfileSlots() {
        val profile = structuredProfileFromMemories(
            "Asha",
            listOf(
                UserMemory("family", "User has a brother named Rafi"),
                UserMemory("preferences", "User prefers green tea"),
                UserMemory("health", "User has a seasonal allergy")
            )
        )

        assertEquals("Asha", profile.name)
        assertEquals(listOf("User has a brother named Rafi"), profile.people)
        assertEquals(listOf("User prefers green tea"), profile.preferences)
    }

    @Test
    fun olderRelevantFactsRankAheadOfNewerUnrelatedFacts() {
        val memories = listOf(
            UserMemory("family", "User's brother Rafi is studying medicine"),
            UserMemory("other", "User bought a new umbrella")
        )

        val retrieved = retrieveRelevantUserMemories(
            "What is Rafi studying?",
            memories,
            StructuredUserProfile(),
        )

        assertEquals(listOf(memories.first()), retrieved)
    }

    @Test
    fun profileSlotsParticipateInPerTurnRetrieval() {
        val profile = StructuredUserProfile(
            name = "Asha",
            people = listOf("User's brother Rafi is studying medicine"),
            preferences = listOf("User prefers green tea")
        )

        val retrieved = retrieveRelevantUserMemories(
            "What is Rafi studying?",
            emptyList(),
            profile
        )

        assertEquals(listOf(UserMemory("people", "User's brother Rafi is studying medicine")), retrieved)
    }

    @Test
    fun relevantEpisodicSummaryIsRetrievedInsteadOfTheNewestOne() {
        val summaries = listOf(
            SessionSummary("older", 1, 2, "User and Dost discussed Rafi's medical school plans."),
            SessionSummary("newer", 3, 4, "User and Dost talked about the weekend weather.")
        )

        val retrieved = retrieveRelevantSessionSummaries("What did Rafi decide?", summaries)

        assertEquals(listOf(summaries.first()), retrieved)
    }

    @Test
    fun retrievalIsCappedAndDoesNotReturnUnmatchedItems() {
        val memories = List(12) { index -> UserMemory("other", "Rafi fact $index") } +
            UserMemory("other", "Unrelated recent detail")

        val retrieved = retrieveRelevantUserMemories("Tell me about Rafi", memories, StructuredUserProfile())

        assertEquals(MAX_RETRIEVED_USER_MEMORIES, retrieved.size)
        assertEquals((11 downTo 6).map { "Rafi fact $it" }, retrieved.map { it.fact })
    }
}
