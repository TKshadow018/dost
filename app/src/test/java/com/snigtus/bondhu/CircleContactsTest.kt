package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleContactsTest {
    @Test
    fun handleNormalizationAndValidation() {
        assertEquals("alice_one", normalizeCircleHandle(" @Alice_One "))
        assertTrue(isValidCircleHandle("alice"))
        assertTrue(isValidCircleHandle("a" + "b".repeat(19)))
        assertFalse(isValidCircleHandle("ab"))
        assertFalse(isValidCircleHandle("Alice"))
        assertFalse(isValidCircleHandle("1alice"))
    }

    @Test
    fun inviteLinksParseFromWebAndDeepLinks() {
        val token = "a".repeat(64)
        assertEquals(token, parseCircleInviteToken("dost://circle/invite/$token"))
        assertEquals(token, parseCircleInviteToken("https://bisque-bear-900175.hostingersite.com/api/circle_invite.php?t=$token"))
        assertEquals(token, parseCircleInviteToken(token))
        assertNull(parseCircleInviteToken("dost://circle/invite/not-a-token"))
        assertNull(parseCircleInviteToken("https://example.com/?t=$token"))
        assertTrue(newCircleInviteToken().matches(Regex("^[a-f0-9]{64}$")))
        assertEquals("dost://circle/invite/$token", circleInviteDeepLink(token))
    }

    @Test
    fun directChatIdsAreStableAndSorted() {
        assertEquals("alice_bob", circleDirectChatId("bob", "alice"))
        assertEquals("alice_bob", circleDirectChatId("alice", "bob"))
    }
}
