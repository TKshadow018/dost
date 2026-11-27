package com.snigtus.dost

import org.junit.Assert.assertTrue
import org.junit.Test

class ModePrivacyDisclosuresTest {
    @Test
    fun availableModeDisclosuresDescribeSharingAndImportantLimits() {
        val explore = ModePrivacyDisclosures.forMode(PrivacyMode.EXPLORE).text
        val shield = ModePrivacyDisclosures.forMode(PrivacyMode.SHIELD).text

        assertTrue(explore.contains("OpenRouter"))
        assertTrue(explore.contains("1,095 days"))
        assertTrue(explore.contains("server-verified 18+"))
        assertTrue(explore.contains("AI characters are not real people"))
        assertTrue(explore.contains("optional attached images"))
        assertTrue(explore.contains("this build requires users to be 18 or older"))
        assertTrue(shield.contains("not end-to-end encrypted"))
        assertTrue(shield.contains("not sent to AI, analytics, product improvement, or testing"))
        assertTrue(shield.contains("until the owner requests deletion"))
    }

    @Test
    fun plannedModeDisclosuresDoNotClaimUnavailableFeaturesAreLive() {
        val circle = ModePrivacyDisclosures.forMode(PrivacyMode.CIRCLE).text
        val cipher = ModePrivacyDisclosures.forMode(PrivacyMode.CIPHER).text

        assertTrue(circle.contains("not available in this build"))
        assertTrue(circle.contains("QR, invite, or handle"))
        assertTrue(circle.contains("until the conversation or account is deleted"))
        assertTrue(cipher.contains("not available in this build"))
        assertTrue(cipher.contains("passphrase that Dost never receives"))
        assertTrue(cipher.contains("participants, timestamps, message sizes, and delivery tokens"))
        assertTrue(cipher.contains("chat/account deletion"))
    }

    @Test
    fun disclosureAcknowledgementsAreScopedToTheAccountAndMode() {
        assertTrue(
            modeDisclosurePreferenceKey("first-user", PrivacyMode.EXPLORE) !=
                modeDisclosurePreferenceKey("second-user", PrivacyMode.EXPLORE)
        )
        assertTrue(
            modeDisclosurePreferenceKey("first-user", PrivacyMode.EXPLORE) !=
                modeDisclosurePreferenceKey("first-user", PrivacyMode.SHIELD)
        )
    }
}
