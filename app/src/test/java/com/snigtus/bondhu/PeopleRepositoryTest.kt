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
        assertEquals(PeopleContactPolicy.MATCHES, PeopleContactPolicy.fromFirestore("matches"))
        assertEquals(PeopleContactPolicy.NOBODY, PeopleContactPolicy.fromFirestore("nobody"))
        try {
            PeopleContactPolicy.fromFirestore("unknown")
            throw AssertionError("Unknown contact preferences must be rejected.")
        } catch (_: IllegalStateException) {
            // Expected: malformed stored preferences must not silently broaden contact access.
        }
    }

    @Test
    fun contactRequestStatusRejectsUnknownValues() {
        assertEquals(SocialContactRequestStatus.PENDING, SocialContactRequestStatus.fromFirestore("pending"))
        assertEquals(SocialContactRequestStatus.ACCEPTED, SocialContactRequestStatus.fromFirestore("accepted"))
        assertEquals(SocialContactRequestStatus.IGNORED, SocialContactRequestStatus.fromFirestore("ignored"))
        try {
            SocialContactRequestStatus.fromFirestore("unknown")
            throw AssertionError("Unknown request statuses must be rejected.")
        } catch (_: IllegalStateException) {
            // Expected: malformed stored request states must not be presented as pending.
        }
    }

    @Test
    fun peopleFailureMessagesLocalizeTransientAndRateLimitErrorsWithoutExposingDetails() {
        val fallback = "Internal backend detail"

        assertEquals(
            "Connection problem. Check your internet and try again.",
            peopleFailureMessage(
                java.io.IOException("private network detail"),
                AppLanguage.ENGLISH,
                fallback
            )
        )
        assertEquals(
            "অনেকবার চেষ্টা হয়েছে। একটু অপেক্ষা করে আবার চেষ্টা করুন।",
            peopleFailureMessageForFirestoreCode("RESOURCE_EXHAUSTED", AppLanguage.BANGLA)
        )
        assertEquals(
            "This action is unavailable for this profile or account. Try again later.",
            peopleFailureMessageForFirestoreCode("PERMISSION_DENIED", AppLanguage.ENGLISH)
        )
        assertEquals(
            fallback,
            peopleFailureMessage(IllegalStateException("private profile detail"), AppLanguage.ENGLISH, fallback)
        )
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
    fun optionalInterestFilterOnlyMatchesSelfDescribedInterests() {
        val people = listOf(
            profile("music-fan", "Books and music"),
            profile("hiker", "Hiking and nature")
        )

        assertEquals(people, filterPeopleByInterest(people, "  "))
        assertEquals(listOf(people.first()), filterPeopleByInterest(people, " MUSIC "))
        assertEquals(emptyList<PublicPersonProfile>(), filterPeopleByInterest(people, "languages"))
    }

    @Test
    fun peopleMessageSearchMatchesLoadedHistoryCaseInsensitively() {
        val messages = listOf(
            SocialChatMessage("first", "user-a", "We should visit the museum", 1L),
            SocialChatMessage("second", "user-b", "See you tomorrow", 2L),
            SocialChatMessage("third", "user-a", "The museum closes at six", 3L)
        )

        assertEquals(
            listOf(messages[0], messages[2]),
            searchSocialMessages(messages, "  MUSEUM ")
        )
        assertEquals(messages, searchSocialMessages(messages, " \n "))
        assertEquals(emptyList<SocialChatMessage>(), searchSocialMessages(messages, "library"))
    }

    @Test
    fun conversationIdIsStableAndIndependentOfParticipantOrder() {
        val first = socialConversationId("user-a", "user-b")

        assertEquals(first, socialConversationId("user-b", "user-a"))
        assertNotEquals(first, socialConversationId("user-a", "user-c"))
    }

    private fun profile(uid: String, interests: String) = PublicPersonProfile(
        uid = uid,
        displayName = uid,
        ageBand = "25–34",
        ageCohort = "adult",
        gender = "Female",
        country = "BD",
        bio = "",
        interests = interests,
        isPublic = true
    )
}
