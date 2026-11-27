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
    fun shieldOwnerClaimRequiresAValidSeatLimitAndFutureExpiry() {
        val now = 1_800_000_000L

        assertTrue(shieldOwnerClaimIsActive(mapOf("dostShieldUntil" to now + 1, "dostShieldSeats" to 10), now))
        assertTrue(shieldOwnerClaimIsActive(mapOf("dostShieldUntil" to now + 1, "dostShieldSeats" to 25), now))
        assertFalse(shieldOwnerClaimIsActive(mapOf("dostShieldUntil" to now, "dostShieldSeats" to 10), now))
        assertFalse(shieldOwnerClaimIsActive(mapOf("dostShieldUntil" to now + 1, "dostShieldSeats" to 11), now))
        assertFalse(shieldOwnerClaimIsActive(emptyMap(), now))
    }
}
