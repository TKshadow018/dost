package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipScoreTest {

    @Test
    fun oneScoreOutcomePerConversationSessionPreventsRepeatedTurnFarming() {
        val openingFriendship = 20
        var storedFriendship = openingFriendship
        var storedLove = 0
        val outcomes = mutableSetOf<String>()
        val replies = listOf("session-1" to 2, "session-1" to 2, "session-2" to 1, "session-3" to -1, "session-3" to 2)
        replies.forEach { (sessionId, impact) ->
            if (shouldApplyRelationshipOutcome(outcomes, sessionId)) {
                val next = applyRelationshipDelta(storedFriendship, storedLove, impact, 0)
                storedFriendship = next.friendship
                storedLove = next.love
                outcomes += sessionId
            }
        }
        assertEquals(20, openingFriendship)
        assertEquals(22, storedFriendship)
        assertEquals(0, storedLove)
    }

    @Test
    fun loveAccumulatesOnlyAfterFriendshipPasses70() {
        var friendship = 68
        var love = 4
        var next = applyRelationshipDelta(friendship, love, 2, 2)
        friendship = next.friendship
        love = next.love
        assertEquals(70, friendship)
        assertEquals(0, love)

        next = applyRelationshipDelta(friendship, love, 2, 2)
        friendship = next.friendship
        love = next.love
        assertEquals(72, friendship)
        assertEquals(2, love)

        next = applyRelationshipDelta(friendship, love, 1, 2)
        assertEquals(73, next.friendship)
        assertEquals(4, next.love)
    }

    @Test
    fun perTurnImpactStaysClampedBetweenMinusOneAndTwo() {
        assertEquals(12, applyRelationshipDelta(10, 0, 9, 0).friendship)
        assertEquals(9, applyRelationshipDelta(10, 0, -4, 0).friendship)
        val loved = applyRelationshipDelta(80, 10, 2, 8)
        assertEquals(82, loved.friendship)
        assertEquals(12, loved.love)
    }

    @Test
    fun scoresStayInsideZeroToOneHundred() {
        assertEquals(100, applyRelationshipDelta(99, 0, 2, 0).friendship)
        assertEquals(0, applyRelationshipDelta(0, 0, -1, 0).friendship)
        assertEquals(100, applyRelationshipDelta(90, 99, 0, 2).love)
    }

    @Test
    fun twoRegeneratesEqualOneApplication() {
        val opening = RelationshipScores(68, 0)
        val afterLive = applyRelationshipDelta(opening.friendship, opening.love, 2, 2)
        val afterTwoRegenerates = applyRegeneratedReply(
            applyRegeneratedReply(afterLive, 2, 2),
            2,
            2
        )
        assertEquals(afterLive, afterTwoRegenerates)
        assertEquals(70, afterTwoRegenerates.friendship)
        assertEquals(0, afterTwoRegenerates.love)
        val farmed = applyRelationshipDelta(
            applyRelationshipDelta(afterLive.friendship, afterLive.love, 2, 2).friendship,
            applyRelationshipDelta(afterLive.friendship, afterLive.love, 2, 2).love,
            2,
            2
        )
        assertEquals(74, farmed.friendship)
        assertTrue(farmed.friendship > 70)
        assertTrue(farmed.love > 0)
    }

    @Test
    fun automaticCheckInsDoNotAwardRelationshipProgress() {
        val afterCheckIn = RelationshipScores(30, 0)
        val afterLiveSend = applyRelationshipDelta(afterCheckIn.friendship, afterCheckIn.love, 1, 0)
        assertEquals(31, afterLiveSend.friendship)
    }

    @Test
    fun relationshipOutcomeIsAppliedOnlyOncePerSessionId() {
        assertTrue(shouldApplyRelationshipOutcome(emptySet(), "session-1"))
        assertTrue(shouldApplyRelationshipOutcome(setOf("session-1"), "session-2"))
        org.junit.Assert.assertFalse(shouldApplyRelationshipOutcome(setOf("session-1"), "session-1"))
        org.junit.Assert.assertFalse(shouldApplyRelationshipOutcome(emptySet(), ""))
    }

    @Test
    fun repeatedIdenticalTurnsCannotEarnProgressAcrossSessions() {
        val fingerprint = relationshipOutcomeFingerprint("  I had a good day. ")
        assertTrue(fingerprint != null)
        assertTrue(shouldApplyRelationshipOutcome(emptySet(), emptySet(), "session-1", fingerprint))
        org.junit.Assert.assertFalse(
            shouldApplyRelationshipOutcome(emptySet(), setOf(fingerprint!!), "session-2", fingerprint)
        )
        org.junit.Assert.assertEquals(fingerprint, relationshipOutcomeFingerprint("i HAD a good day."))
        org.junit.Assert.assertEquals(null, relationshipOutcomeFingerprint("   "))
    }

    @Test
    fun friendshipStageThresholdsAreDeterministic() {
        assertEquals(0, friendshipStageIndex(0))
        assertEquals(0, friendshipStageIndex(10))
        assertEquals(1, friendshipStageIndex(11))
        assertEquals(6, friendshipStageIndex(70))
        assertEquals(7, friendshipStageIndex(71))
        assertEquals(9, friendshipStageIndex(100))
    }

    @Test
    fun conversationStartersUseDistinctFriendInterests() {
        val friend = Friend(
            id = "friend",
            name = "Sam",
            age = "24",
            gender = "Unspecified",
            interests = "Music, hiking, Music"
        )
        val starters = conversationStarters(friend, AppLanguage.ENGLISH)
        assertEquals(2, starters.size)
        assertTrue(starters[0].contains("Music"))
        assertTrue(starters[1].contains("hiking"))
    }
}
