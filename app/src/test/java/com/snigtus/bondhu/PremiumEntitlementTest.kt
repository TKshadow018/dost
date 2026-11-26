package com.snigtus.dost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementTest {
    @Test
    fun premiumClaimsRespectLifetimeAndSubscriptionExpiry() {
        val now = 1_790_000_000L

        assertTrue(premiumClaimIsActive(mapOf("dostPremiumLifetime" to true), now))
        assertTrue(premiumClaimIsActive(mapOf("dostPremiumUntil" to now + 1), now))
        assertFalse(premiumClaimIsActive(mapOf("dostPremiumUntil" to now), now))
        assertFalse(premiumClaimIsActive(mapOf("dostPremiumUntil" to (now - 1).toDouble()), now))
        assertFalse(premiumClaimIsActive(emptyMap(), now))
    }

    @Test
    fun introSourceIsExposedOnlyWhileItsExpiryIsActive() {
        val now = 1_790_000_000L
        val active = premiumEntitlementFromClaims(
            mapOf("dostPremiumUntil" to now + 60, "dostPremiumSource" to "intro"),
            now
        )
        val expired = premiumEntitlementFromClaims(
            mapOf("dostPremiumUntil" to now, "dostPremiumSource" to "intro"),
            now
        )

        assertTrue(active.isActive)
        assertTrue(active.source == "intro")
        assertFalse(expired.isActive)
        assertTrue(expired.source == "intro")
        assertTrue(premiumIntroIsNearExpiry(active, now))
        assertFalse(premiumIntroIsNearExpiry(expired, now))
        assertFalse(
            premiumIntroIsNearExpiry(
                active.copy(expiresAtEpochSeconds = now + 15 * 86400),
                now
            )
        )
        assertFalse(premiumIntroIsNearExpiry(active.copy(source = "play"), now))
    }
}
