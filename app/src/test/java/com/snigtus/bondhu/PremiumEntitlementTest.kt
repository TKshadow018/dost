package com.snigtus.dost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementTest {
    @Test
    fun premiumClaimsRespectLifetimeAndSubscriptionExpiry() {
        val now = 1_790_000_000L

        assertTrue(premiumClaimIsActive(mapOf("dostPremiumLifetime" to true), now))
        assertTrue(premiumClaimIsActive(mapOf("dostExplorePlusLifetime" to true), now))
        assertTrue(premiumClaimIsActive(mapOf("dostExplorePlusUntil" to now + 1), now))
        assertTrue(premiumClaimIsActive(mapOf("dostPremiumUntil" to now + 1), now))
        assertFalse(premiumClaimIsActive(mapOf("dostPremiumUntil" to now), now))
        assertFalse(premiumClaimIsActive(mapOf("dostPremiumUntil" to (now - 1).toDouble()), now))
        assertFalse(premiumClaimIsActive(emptyMap(), now))
    }

    @Test
    fun modeClaimsRespectExpiryAndShieldSeatCount() {
        val now = 1_790_000_000L
        assertTrue(planClaimIsActive(mapOf("dostCipherUntil" to now + 1), "cipher", now))
        assertFalse(planClaimIsActive(mapOf("dostCipherUntil" to now), "cipher", now))
        assertFalse(
            planClaimIsActive(
                mapOf("dostPremiumLifetime" to true),
                "cipher",
                now,
            ),
        )
        assertFalse(
            planClaimIsActive(
                mapOf("dostCipherUntil" to now - 1),
                "cipher",
                now,
            ),
        )
        assertTrue(
            planClaimIsActive(
                mapOf("dostShieldUntil" to now + 1, "dostShieldSeats" to 10),
                "shield",
                now,
            ),
        )
        assertFalse(
            planClaimIsActive(
                mapOf("dostShieldUntil" to now + 1, "dostShieldSeats" to 0),
                "shield",
                now,
            ),
        )
    }
}
