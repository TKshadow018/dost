package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeGateTest {
    @Test
    fun registrationAcceptsThirteenAndRefusesYoungerOrUnusableAges() {
        assertEquals(AccountAgeResult.VALID, accountAgeResult("13"))
        assertEquals(AccountAgeResult.VALID, accountAgeResult(" 18 "))
        assertEquals(AccountAgeResult.VALID, accountAgeResult("120"))
        assertEquals(AccountAgeResult.UNDER_MINIMUM, accountAgeResult("12"))
        assertEquals(AccountAgeResult.UNDER_MINIMUM, accountAgeResult("1"))
        assertEquals(AccountAgeResult.EMPTY, accountAgeResult("  "))
        assertEquals(AccountAgeResult.NOT_A_WHOLE_NUMBER, accountAgeResult("13abc"))
        assertEquals(AccountAgeResult.NOT_A_WHOLE_NUMBER, accountAgeResult("0"))
        assertEquals(AccountAgeResult.NOT_A_WHOLE_NUMBER, accountAgeResult("-4"))
        assertEquals(AccountAgeResult.ABOVE_MAXIMUM, accountAgeResult("121"))
    }

    @Test
    fun friendAgeMayBeUnderEighteenButMustBeARealAge() {
        assertEquals(AccountAgeResult.VALID, friendAgeResult("17"))
        assertEquals(AccountAgeResult.VALID, friendAgeResult("1"))
        assertEquals(AccountAgeResult.VALID, friendAgeResult("18"))
        assertEquals(AccountAgeResult.NOT_A_WHOLE_NUMBER, friendAgeResult("fifteen"))
        assertEquals(AccountAgeResult.ABOVE_MAXIMUM, friendAgeResult("121"))
        assertEquals(AccountAgeResult.EMPTY, friendAgeResult(""))
    }

    @Test
    fun romanceRequiresBothPeopleToBeEighteenOrOlder() {
        assertTrue(romanceAllowed("18", "18"))
        assertTrue(romanceAllowed("30", "25"))
        assertFalse(romanceAllowed("17", "25"))
        assertFalse(romanceAllowed("25", "17"))
        assertFalse(romanceAllowed("16", "15"))
        assertFalse(romanceAllowed("18", ""))
        assertFalse(romanceAllowed("", "30"))
        assertFalse(romanceAllowed("abc", "30"))
        assertFalse(romanceAllowed("18", "18 years"))
    }

    @Test
    fun loveScoreStaysAtZeroWhenRomanceIsLocked() {
        assertEquals(0, nextLoveScore(40, 2, 100, "17", "30"))
        assertEquals(0, nextLoveScore(40, 2, 100, "30", "15"))
        assertEquals(0, nextLoveScore(40, 2, 100, "", "30"))
        assertEquals(0, nextLoveScore(10, 2, 70, "30", "30"))
        assertEquals(12, nextLoveScore(10, 2, 71, "30", "30"))
        assertEquals(100, nextLoveScore(100, 2, 90, "30", "30"))
        assertEquals(0, nextLoveScore(0, -1, 90, "30", "30"))
    }

    @Test
    fun storedProfilesAndNewMinorFriendsCannotKeepALoveScore() {
        val minorFriend = Friend(id = "kid", name = "A", age = "15", gender = "female", friendshipScore = 100, loveScore = 55)
        val adultFriend = minorFriend.copy(id = "adult", age = "24", loveScore = 55)

        assertEquals(0, minorFriend.withLoveMeterGated("25").loveScore)
        assertEquals(55, adultFriend.withLoveMeterGated("25").loveScore)
        assertEquals(0, adultFriend.withLoveMeterGated("16").loveScore)
        assertEquals(0, nextLoveScore(minorFriend.loveScore, 2, minorFriend.friendshipScore, "25", minorFriend.age))
        assertFalse(romanceAllowed("25", minorFriend.age))
    }

    @Test
    fun relationshipDeltasAccumulateWhileLoveRemainsAgeGated() {
        val adult = Friend(id = "adult", name = "A", age = "24", gender = "female", friendshipScore = 80, loveScore = 10)
        val first = adult.withRelationshipDelta(2, 2, "25")
        val second = first.withRelationshipDelta(2, 2, "25")
        assertEquals(84, second.friendshipScore)
        assertEquals(14, second.loveScore)

        listOf(adult.copy(age = "17"), adult.copy(age = ""), adult).forEachIndexed { index, friend ->
            val updated = friend.withRelationshipDelta(2, 2, if (index == 2) "17" else "25")
            assertEquals(82, updated.friendshipScore)
            assertEquals(0, updated.loveScore)
        }
    }

    @Test
    fun modelLoveImpactIsDroppedWhenRomanceIsLocked() {
        val reply = AiReply(text = "hi", friendshipImpact = 2, loveImpact = 2)
        val locked = applyLoveImpactGate(reply, "15", "20")
        assertEquals(0, locked.loveImpact)
        assertEquals(2, locked.friendshipImpact)
        assertEquals(2, applyLoveImpactGate(reply, "20", "22").loveImpact)
        assertEquals(0, applyLoveImpactGate(reply, "30", "15").loveImpact)
    }

    @Test
    fun promptAsksForLoveDeltasOnlyWhenBothAreAdults() {
        val adultState = relationshipStatePrompt(90, 80, "25", "24")
        val adultImpact = relationshipImpactPrompt("25", "24")
        assertTrue(adultState.contains("Love meter: 80/100 (${loveLevel(80)})"))
        assertTrue(adultImpact.contains("love_impact must be an integer from -1 to 2 only when friendship is above 70 and the conversation is clearly flirty or romantic"))
        assertTrue(adultImpact.contains("romantic/intimate-but-non-explicit talk only when friendship is above 70 and love is above 30"))
        assertTrue(relationshipStatePrompt(50, 0, "30", "30").contains("locked until friendship is above 70"))

        val userMinorState = relationshipStatePrompt(90, 80, "16", "24")
        val userMinorImpact = relationshipImpactPrompt("16", "24")
        assertTrue(userMinorState.contains("Love meter: off"))
        assertFalse(userMinorState.contains("80/100"))
        assertFalse(userMinorState.contains(loveLevel(80)))
        assertFalse(userMinorState.contains("locked until friendship is above 70"))
        assertTrue(userMinorImpact.contains("love_impact must be 0"))
        assertFalse(userMinorImpact.contains("flirty or romantic"))
        assertFalse(userMinorImpact.contains("romantic/intimate"))

        val friendMinorImpact = relationshipImpactPrompt("30", "17")
        assertTrue(friendMinorImpact.contains("love_impact must be 0"))
        assertFalse(friendMinorImpact.contains("flirty or romantic"))
        assertTrue(relationshipImpactPrompt("", "30").contains("love_impact must be 0"))
    }

    @Test
    fun minorCharacterProfilesDoNotAskForRomance() {
        assertEquals("", profileRomanceClause("18"))
        assertEquals("", profileRomanceClause("40"))
        assertTrue(profileRomanceClause("17").contains("under 18"))
        assertTrue(profileRomanceClause("17").contains("Do not invent romance"))
        assertTrue(profileRomanceClause("").contains("Do not invent romance"))
    }
}
