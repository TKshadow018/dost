package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeopleRepositoryTest {
    @Test
    fun contactPolicyDefaultsLegacyProfilesToEveryoneAndRejectsUnknownValues() {
        assertEquals(PeopleContactPolicy.EVERYONE, PeopleContactPolicy.fromFirestore(null))
        assertEquals(PeopleContactPolicy.REQUESTS, PeopleContactPolicy.fromFirestore("requests"))
        assertEquals(PeopleContactPolicy.NOBODY, PeopleContactPolicy.fromFirestore("nobody"))
        try {
            PeopleContactPolicy.fromFirestore("unknown")
            throw AssertionError("Unknown contact preferences must be rejected.")
        } catch (_: IllegalStateException) {
            // Expected: malformed stored preferences must not silently broaden contact access.
        }
    }

    @Test
    fun socialDiscoveryOnlyAcceptsAdultCohorts() {
        assertNull(socialAgeCohort("13"))
        assertNull(socialAgeCohort("17"))
        assertEquals("adult", socialAgeCohort("18"))
        assertNull(socialAgeCohort("12"))
        assertNull(socialAgeCohort("18.5"))
    }

    @Test
    fun publicAgeBandsAvoidExposingExactAge() {
        assertNull(socialAgeBand("13"))
        assertNull(socialAgeBand("17"))
        assertEquals("18–24", socialAgeBand("18"))
        assertEquals("18–24", socialAgeBand("24"))
        assertEquals("25–34", socialAgeBand("25"))
        assertEquals("35+", socialAgeBand("35"))
        assertNull(socialAgeBand("121"))
    }

    @Test
    fun conversationIdIsStableAndIndependentOfParticipantOrder() {
        val first = socialConversationId("user-a", "user-b")

        assertEquals(first, socialConversationId("user-b", "user-a"))
        assertNotEquals(first, socialConversationId("user-a", "user-c"))
    }
}
