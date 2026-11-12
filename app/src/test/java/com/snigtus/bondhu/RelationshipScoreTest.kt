package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RelationshipScoreTest {

    @Test
    fun liveRepliesAccumulateOnTheStoredScore() {
        val openingFriendship = 20
        var storedFriendship = openingFriendship
        var storedLove = 0
        val impacts = listOf(2, 2, 1, -1, 2)
        impacts.forEach { impact ->
            val next = applyRelationshipDelta(storedFriendship, storedLove, impact, 0)
            storedFriendship = next.friendship
            storedLove = next.love
        }
        assertEquals(26, storedFriendship)
        // The old live-send bug kept the opening score and applied only the latest delta.
        assertNotEquals(openingFriendship + impacts.last(), storedFriendship)
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
    fun checkInAndLiveSendShareTheSameStoredScorePath() {
        val afterCheckIn = applyRelationshipDelta(30, 0, 2, 0)
        val afterLiveSend = applyRelationshipDelta(afterCheckIn.friendship, afterCheckIn.love, 1, 0)
        assertEquals(33, afterLiveSend.friendship)
    }
}
