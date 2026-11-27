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
            "Circle is planned as the default mode for people you choose. Planned contact methods are QR, invite, or handle. Messages, group membership, read receipts, and typing indicators will be stored in Firestore for delivery and visible to conversation members. Data is planned to remain until the conversation or account is deleted; provider backups may persist under their own schedule. These Circle features are not available in this build."
        ),
        ModePrivacyDisclosure(
            PrivacyMode.EXPLORE,
            "Explore",
            "AI characters are not real people. AI chats send your prompts, earlier turns, selected friend details, relevant saved memories, and optional attached images to Dost's API, OpenRouter, and the selected model provider. Dost's active archive is kept for up to 1,095 days; delete it in Settings. Provider retention is separate. People profiles, messages, and age-eligibility checks use Firebase. Teen restrictions apply only after the planned 13+ release; this build requires users to be 18 or older. Explore Plus is only for adults with server-verified 18+ eligibility."
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

    val available = all.filter { it.mode == PrivacyMode.EXPLORE || it.mode == PrivacyMode.SHIELD }
    val planned = all.filterNot { it in available }

    fun forMode(mode: PrivacyMode): ModePrivacyDisclosure =
        all.first { it.mode == mode }
}

internal fun modeDisclosurePreferenceKey(uid: String, mode: PrivacyMode): String =
    "acknowledged_${uid.ifBlank { "local" }}_${mode.name.lowercase()}"
