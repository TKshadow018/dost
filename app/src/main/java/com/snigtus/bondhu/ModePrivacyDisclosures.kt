package com.snigtus.dost

internal enum class PrivacyMode { CIRCLE, EXPLORE, SHIELD, CIPHER }

internal data class ModePrivacyDisclosure(
    val mode: PrivacyMode,
    val title: String,
    val text: String
)

internal object ModePrivacyDisclosures {
    val all = listOf(
        ModePrivacyDisclosure(
            PrivacyMode.CIRCLE,
            "Circle",
            "Circle is the default free messenger for people you already know. You add contacts by QR, invite, or handle. Messages, group membership, read receipts, and typing indicators are stored in Firestore for delivery and are visible to conversation members. Data remains until the conversation or account is deleted; provider backups may persist under their own schedule. Handle lookup is exact-match only, rate-limited, and off by default for teens."
        ),
        ModePrivacyDisclosure(
            PrivacyMode.EXPLORE,
            "Explore",
            "AI characters are not real people. AI chats send your prompts, earlier turns, selected friend details, relevant saved memories, and optional attached images to Dost's API, OpenRouter, and the selected model provider. Dost's active archive is kept for up to 1,095 days; delete it in Settings. Provider retention is separate. People profiles, messages, and age-eligibility checks use Firebase. Dost is for ages 13 and older. Teens can use teen-safe AI, but not stranger discovery or Explore Plus; both require server-verified 18+ eligibility."
        ),
        ModePrivacyDisclosure(
            PrivacyMode.SHIELD,
            "Shield",
            "Organization membership, audit records, and text messages are stored separately in Firebase and are not sent to AI, analytics, product improvement, or testing. Firebase administrators may technically access them; Shield is not end-to-end encrypted. Data remains until the owner requests deletion. Owners can contact Dost to discuss a scoped export or manual move to their own server; neither is self-service or guaranteed."
        ),
        ModePrivacyDisclosure(
            PrivacyMode.CIPHER,
            "Cipher",
            "Cipher is not available in this build. If released, message contents are intended to be end-to-end encrypted using a chat passphrase that Dost never receives and cannot recover. Delivery metadata (participants, timestamps, message sizes, and delivery tokens) will still be processed by Dost and required delivery providers. The planned deletion path is chat/account deletion; provider backups may persist under their own schedule. Final metadata retention and sharing must be confirmed before release."
        )
    )

    val available = all.filter { it.mode in setOf(PrivacyMode.CIRCLE, PrivacyMode.EXPLORE, PrivacyMode.SHIELD) }
    val planned = all.filterNot { it in available }

    fun forMode(mode: PrivacyMode): ModePrivacyDisclosure =
        all.first { it.mode == mode }
}

internal fun modeDisclosurePreferenceKey(uid: String, mode: PrivacyMode): String =
    "acknowledged_${uid.ifBlank { "local" }}_${mode.name.lowercase()}"
