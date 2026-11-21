package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PlayIntegrityAgeEligibilityClientTest {
    @Test
    fun requestHashUsesTheCanonicalServerPayload() {
        val uid = "firebase-user-123"
        val nonce = "a".repeat(64)
        val payload = "dost-age-v1\n$uid\n1\n$nonce"
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        )

        assertEquals(expected, ageEligibilityRequestHash(uid, true, nonce))
    }

    @Test
    fun requestHashChangesWhenUserOrEligibilityChanges() {
        val nonce = "b".repeat(64)
        val original = ageEligibilityRequestHash("user-a", true, nonce)

        assertNotEquals(original, ageEligibilityRequestHash("user-b", true, nonce))
        assertNotEquals(original, ageEligibilityRequestHash("user-a", false, nonce))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestHashRejectsMalformedNonce() {
        ageEligibilityRequestHash("user-a", true, "not-a-nonce")
    }
}
