# Dost: Improvement and Marketing Proposals

*Product and growth ideas, not a list of features already shipped. Updated November 2026.*

## Product direction

Dost's strongest product idea is a continuing circle of fictional AI friends with distinct personalities, friend-specific memories, configurable routines, and English, Bangla, and Hindi language choices. The optional People area is a separate, adult-eligible text-chat experience.

Recommended positioning:

> **Dost is a multilingual companion app where you create fictional AI friends, shape how they talk, and return to the details you choose to remember. Eligible adults can also use a separate People experience for text chat.**

Keep the distinction between AI characters and real people obvious in product language and marketing. Do not claim that an AI is human, that it never forgets, or that it can replace professional care.

## Improvement proposals

### Priority 0: Build trust before growing People

1. **Finish launch readiness.** Configure and test the age-eligibility service, deploy matching Firestore rules, add a People feature flag, and confirm the adult-access flow on Play-installed builds. The source roadmap lists these as prerequisites; code alone does not mean production services have been deployed.
2. **Reduce unwanted contact.** Add message requests, acceptance/ignore controls, per-account rate limits, discovery throttles, and a documented block/report response process. The current profile-selection flow starts a chat directly.
3. **Complete user-facing policies.** Publish concise Community Guidelines and Terms, clarify retention and deletion for real-person chats, and provide a support route users can find from the app.
4. **Give users clear expectations.** In onboarding, explain what is AI-generated, what is stored locally, what is sent to AI providers, and how the optional People feature differs.

### Priority 1: Make messaging dependable

1. **Add opt-in message notifications.** Use privacy-safe lock-screen text by default and provide per-conversation notification controls. Ask for Android notification permission only after explaining the benefit.
2. **Finish message states and recovery.** Show sending and confirmed-sent states, visible failures, and retry actions. Use stable client message IDs so retrying cannot duplicate a message.
3. **Support poor connections.** Queue outgoing messages safely, retry when connectivity returns, and explain when a message has not reached the service.
4. **Make long chats easier to use.** Add history pagination, conversation search, and fast navigation between AI chats and People chats.
5. **Add privacy-conscious read and availability controls.** If sender-facing read receipts, typing indicators, or last-seen status are added, make them understandable and controllable; presence should be opt-in.

### Priority 1-2: Make People discovery more consensual

1. Add a message-request inbox with accept, ignore, block, and report actions.
2. Let users choose who can contact them, including a setting that disables new requests.
3. Offer mutual matching as an option rather than making direct contact the only path.
4. Explain clearly that a public profile can be seen by eligible users and that profiles are not identity verification.

These features should be supported by rate limits, abuse monitoring, and a real report-review process before discovery traffic is increased.

### Priority 2: Add useful everyday chat features

- Add replies/quotes and emoji reactions; define edit and delete behavior before offering message editing.
- Consider profile and chat image sharing only after the existing Hostinger upload service is hardened for authentication, authorization, file validation, quotas, private retrieval, and deletion.
- Keep media on the existing Hostinger service and `baseUrl`; **do not add Firebase Storage**.
- Add mute, archive, and pin controls for conversations.
- Explore small AI-friend activities that build on the existing games, such as a cooperative word or trivia game, without putting game results into memory unless the user chooses.
- Keep voice calls, video calls, voice notes, and audio-message services out of scope.

### Priority 2-3: Improve accessibility and retention

- Test TalkBack labels, larger text, keyboard/IME behavior, contrast, and narrow-screen layouts.
- Review Bangla and Hindi translations, including long labels and text scaling.
- Improve low-bandwidth and error states, especially for AI response failures and real-person messaging.
- Make memory controls and proactive-message settings easy to find and understand.
- Add export and deletion guidance for real-person data before adding more social content.

## Marketing proposals

### 1. Lead with a clear, honest difference

Test messaging centered on:

- **Friends with distinct personalities**, rather than a generic AI chatbot.
- **Continuity you control**, through friend-specific memories that can be reviewed or removed.
- **Language choice**, with separate app-interface and AI-chat language settings.
- **Optional social chat**, described as a separate feature for eligible adults, not as a guaranteed match or verified community.

Avoid claims such as "AI that feels exactly human," "never forgets," "safe dating," "verified people," "private messaging," or "mental-health support." Human People chats are not end-to-end encrypted, and AI responses can be inaccurate.

### 2. Make short demonstrations, not abstract claims

Create short vertical videos and screenshots showing real, synthetic examples:

1. Choose an app language and a different AI-chat language.
2. Create a fictional friend with a distinct personality.
3. Save a detail, review it later, then correct or remove it.
4. Show how a routine or proactive-message setting works, including that proactive messages are off by default.
5. Explain the separate People experience and its adult eligibility, block/report controls, and text-only scope.

Never use a real user's private conversation, profile, or photo in promotional material without specific permission.

### 3. Localize the store listing and creator outreach

- Prepare English, Bangla, and Hindi store copy and screenshots; translate the product itself before promising complete language parity.
- Work with small creators who can demonstrate the app transparently in their own language.
- Start with an invited adult beta and community feedback, then expand only after messaging support and moderation are ready.
- Consider referral links only after spam controls are in place; do not reward unsolicited messages or profile scraping.

### 4. Run small, measurable experiments

Test a few store-listing headlines and screenshots at a time. Compare ideas such as "Build your circle of AI friends" and "AI companions with memories you control." Keep the AI nature of the characters explicit in every variant.

Use privacy-preserving, aggregated measures where possible:

- Store-listing view-to-install conversion.
- Setup completion and time to first conversation.
- Successful message delivery and response reliability.
- Voluntary return rate after one and seven days.
- Crash-free sessions and support-request trends.

Do not collect message content for marketing analytics. Explain optional analytics clearly, keep it off by default where applicable, and avoid optimizing for notification volume or excessive engagement.

## Suggested release sequence

1. Close age-eligibility, rules-deployment, policy, moderation, and feature-flag prerequisites.
2. Improve message requests, notification preferences, failure recovery, and chat history handling.
3. Run a small adult beta and resolve safety, reliability, accessibility, and support issues.
4. Publish truthful, localized store materials and expand marketing gradually.
5. Add media only after upload and retrieval security, privacy disclosures, quotas, and deletion are in place.

See the [People feature roadmap](./PEOPLE_FEATURE_ROADMAP.md) for implementation details and release gates, and the [privacy policy](./app/api/privacy-policy.txt) for current data disclosures.
