# Dost Chat-First Product Roadmap

**Status:** Remaining product, validation, and release work  
**Goal:** Make Dost feel like a familiar, dependable messaging app by default, while keeping discovery for fictional AI friends and real people clear and separate.  
**Design guidance:** Repository-local UI-UX-Pro-Max prompt and searchable data, plus the current Android Compose theme and People roadmap.

## Remaining validation and release status — 2026-11-21

- **Still to validate:** conversation organization on device, including search, pin ordering, mute behavior, archive/restore, and account switching; AI add, Start chat, Undo, and catalog-detail rendering on device.
- **Still to validate:** People profile preview and adult-eligibility labeling on device; notification opt-in, mute behavior, generic lock-screen text, server deployment and two-account delivery, and outgoing request status; offline/process-death/reconnect behavior; visual behavior on device sizes/orientations, TalkBack and font scaling; and a real two-account People inbox smoke test. These are not claimed as completed.
- **Release gates:** Phase 0 People safety, moderation, and operational prerequisites remain open and are not changed by the inbox UI.

## 1. Product direction

Dost should open to conversations, not to a discovery deck. A user should be able to return to an existing AI-friend or real-person chat immediately, then deliberately start a new conversation with either kind of contact.

The product can borrow common messaging and swipe-discovery conventions without copying another app's exact branding or interface:

- **Chats first:** the default destination is the conversation inbox.
- **One familiar inbox, two distinct kinds of conversation:** AI and human chats can appear together in the inbox, but retain separate models, services, storage, safety rules, and disclosures.
- **Discovery is intentional:** the user chooses whether to browse AI characters or real people.
- **AI is always explicit:** AI characters are fictional and visibly labeled; a character must never be presented as a real person.
- **Human contact is consensual:** discovery actions respect the recipient's contact policy and provide request, block, and report paths.
- **Ship reliability before engagement features:** delivery, privacy, and recovery are more important than presence indicators, media, or richer matching.

## 2. Current baseline

This roadmap builds on existing screens and services rather than proposing a rewrite:

- Chats is the default unified inbox for AI and People conversations, with source filters, unread state, and conversation-level organization.
- AI friends already have a swipe picker: swipe right to add (with confirmation) and left to pass; users can also create a custom friend.
- People already has a separate discovery deck, People chats, contact requests, and a profile editor. Contact behavior respects the profile's contact policy, including the optional match-first mode.
- People discovery eligibility is based on the adult cohort, not exact age-band matching. Passed profile IDs persist for up to 90 days and can be cleared with **Start over**.
- People chats have a bounded 500-message live window, on-demand older-page loading, and search over currently loaded messages. The older pages must be loaded to include them in search.
- People audio and video calls exist as separate WebRTC experiences. Optional, generic message-notification plumbing is implemented and off by default; production endpoint deployment and two-account delivery remain to be verified. Sender-facing read receipts, typing indicators, and online status are not available.
- The app uses Jetpack Compose and Material 3. `app/build.gradle` already includes Compose, Firebase, and WebRTC dependencies.
- The People roadmap requires adult eligibility, privacy controls, moderation operations, and abuse limits to remain release gates. See [PEOPLE_FEATURE_ROADMAP.md](./PEOPLE_FEATURE_ROADMAP.md).

Keep the current AI and People experiences working while the shared inbox and navigation are introduced. A unified inbox is a presentation layer, not a reason to merge AI and human message storage.

## 3. Target information architecture

### Primary navigation

Use a small, stable bottom navigation bar:

1. **Chats** — the default screen and unified conversation inbox.
2. **Discover** — an explicit choice between AI friends and People.
3. **Profile** — account, privacy, notification, and app settings.

Keep games accessible from Chats or Profile rather than giving them equal weight to messaging. Keep calls inside the relevant People conversation until a real call-history experience is designed and supported.

### Chats screen

- Default to **All** conversations.
- Offer **All / AI friends / People** filters that preserve list position and search context.
- Add one visible **New chat** action that opens two choices: **Find an AI friend** and **Discover people**.
- Show unread state, most recent message preview, timestamp, and relevant mute/pin indicators.
- Distinguish human and AI entries with a clear label or avatar treatment; do not rely on color alone.
- Preserve the existing AI-friend list features, including favorites, friend profiles, and access to custom friends.

### Discover screen

Provide two clearly separated destinations:

- **AI friends:** fictional character cards, personality and interests, supported conversation language, and a direct path to start chatting.
- **People:** adult-eligible public profiles, contact preferences, mutual-match/request behavior, and clear block/report controls.

Do not put AI profiles and real-person profiles into one undifferentiated swipe deck.

## 4. UI/UX design direction

### UI-UX-Pro-Max findings and application

The repository's UI-UX-Pro-Max design-system retry returned a clean, minimal style and a Messenger-blue/online-green palette. The generated page pattern was a web product-demo layout, which does not apply to a native chat screen; this roadmap intentionally does not use a landing-page pattern.

Apply the relevant direction to the Android app:

- **Visual style:** functional, calm minimalism; strong hierarchy; restrained decoration; content-first chat surfaces.
- **Color:** preserve the existing semantic Compose theme. The current light palette uses Dost blue `#2563EB` and green `#047857`, with corresponding dark-theme colors. Do not copy the search result's alternate green or introduce per-screen hard-coded colors.
- **Typography:** keep the existing Compose typography and verify Bangla, Hindi, large text, and device rendering before considering a font change. The search's web-font recommendations are not a reason to download web fonts into the app.
- **Spacing and surfaces:** use a consistent 4/8dp spacing rhythm, semantic Material 3 surfaces, clear dividers, and predictable list gutters.
- **Icons and controls:** use the project's vector/Material icon approach, not emoji as structural icons. Give icon-only buttons accessible names and at least 48dp Android touch targets.
- **Motion:** use short, purposeful Compose transitions and responsive press feedback. Respect system reduced-motion settings; avoid motion that blocks sending or navigation.
- **Theme:** validate both light and dark modes independently, including text contrast, borders, message bubbles, dialogs, disabled controls, and status indicators.

### Screen-level interaction rules

#### Conversation inbox

- Use a virtualized `LazyColumn` for conversations and provide stable keys.
- Keep each row's main action (open conversation) separate from secondary actions (pin, mute, archive).
- Show an understandable empty state with a clear next action: start an AI chat or discover People.
- Loading, offline, and error states must explain what happened and expose retry when retry is possible.
- Search results should retain the selected conversation type and never search or send human messages through the AI service.

#### Conversation screen

- Preserve the familiar header, chronological message list, and bottom composer.
- Keep the composer above the keyboard and system navigation area; retain drafts when navigating away.
- Distinguish sending, sent, and failed states in text and semantics, not only color.
- Keep AI-specific controls (memory and AI settings) out of human chats; keep People-specific block/report/profile actions out of AI chats.
- Do not imply end-to-end encryption or guaranteed delivery.

#### Swipe decks

- Restrict horizontal swipe gestures to the card deck; do not make swipe the only way to decide.
- Provide visible **Pass** and **Connect/Add** buttons, a profile-details action, and accessible labels/states.
- Preserve vertical scrolling, Android back gestures, and TalkBack actions; do not make deck gestures conflict with system navigation.
- Provide clear results after an action: added and ready to chat for AI, or request/match status for People.
- Use restrained card transitions and a non-animated or reduced-motion path.

#### Accessibility and localization

- Test TalkBack order, labels, selected states, unread announcements, and the full swipe-button alternative.
- Support system font scaling, keyboard/IME behavior, and compact device heights without clipping the composer, cards, or actions.
- Check English, Bangla, and Hindi for truncation and layout expansion; do not assume English-sized controls fit translated labels.
- Keep fixed controls clear of status/navigation bars and the keyboard.
- Target at least 4.5:1 contrast for normal text and 3:1 for meaningful non-text controls; measure both themes rather than assuming the palette passes.

## 5. Detailed roadmap

The sequence is milestone-based rather than calendar-based. Complete the release gates before increasing real-person discovery traffic.

### Phase 0 — Product decisions and safety baseline

**Priority:** P0  
**Outcome:** A signed-off target flow, with no ambiguity about human-contact behavior or release readiness.

#### Work

- [ ] Confirm the primary navigation labels and whether **Discover** is a bottom-navigation destination or a Chats-level action.
- [ ] Confirm the meaning of AI swipe-right: recommended behavior is add the character, show an undo confirmation, and offer **Start chat** immediately.
- [x] Confirm People contact options: preserve current direct/request/no-new-chat behavior and make match-first an additional opt-in profile preference; do not silently change existing profiles.
- [ ] Inventory current navigation, conversation summaries, unread state, drafts, notification behavior, profile visibility, and deletion paths.
- [ ] Define a user-facing explanation of AI vs. human chats, what each service stores, and whether a contact action starts a chat or sends a request.
- [ ] Complete People release prerequisites from [PEOPLE_FEATURE_ROADMAP.md](./PEOPLE_FEATURE_ROADMAP.md): trusted rate limits, report operations, support route, published policies, and operational removal/deletion procedures.
- [ ] Keep the existing adult eligibility and Firestore rule gates in place; do not represent them as identity or age verification.

#### Exit criteria

- Product copy and screen flows distinguish AI characters from real people at every entry point.
- Existing accounts keep their existing friends, chats, contact settings, and drafts.
- The team can explain each People contact mode, its enforcement point, and how a user blocks or reports someone.
- No public-discovery expansion is scheduled before required moderation and rate-limit operations are ready.

### Phase 1 — Chat-first shell and unified inbox

**Priority:** P0  
**Outcome:** Users open Dost into a stable inbox and can find either kind of conversation without merging their data paths.

#### Work

- [x] Provide a unified Chats inbox with All, AI, People, and archived conversation views while retaining separate chat services and storage.
- [x] Preserve source-specific conversation opening, unread state, search context, stable list keys, and account-scoped summaries.
- [x] Provide a new-chat route that separates AI-character discovery from People discovery.

#### Exit criteria

- AI and People conversations appear in All and only in their respective filters.
- Opening an AI chat never opens a People screen or sends its text to the human-chat service, and vice versa.
- Search, unread counts, ordering, and scroll state remain correct after switching filters and returning from a chat.
- Profile/account switching cannot expose a previous account's conversation summaries.
- All list items have stable keys; empty and error states have usable next actions.

### Phase 2 — Dependable messaging basics

**Priority:** P1  
**Outcome:** Users can tell whether a message was sent and recover from common connectivity failures.

#### Work

- [x] Persist pending People messages locally and retry with stable message IDs; show sending, sent, waiting, and failed states.
- [x] Bound the live People message listener and load older history in pages on demand.
- [x] Add People-chat text search over loaded messages and an explicit path to load older pages for broader search.
- [x] Add optional, generic People message alerts with per-device opt-in and per-chat mute. The PHP/FCM path exists; production deployment and real two-account delivery remain release checks.
- [ ] Keep read receipts, typing indicators, and online/last-seen status out of the first inbox release; if later added, make them optional and independently configurable.

#### Exit criteria

- A failed message is visible and retryable; retrying does not duplicate it.
- Message and unread state recover after process death and reconnect.
- Notifications honor user settings, avoid exposing message text by default, and do not claim end-to-end encryption.
- Long conversations do not require unbounded history listeners.
- Denied notification permission does not block the rest of the app.

### Phase 3 — AI-friend discovery as “AI Tinder”

**Priority:** P1  
**Outcome:** Users can browse fictional AI characters quickly and start a conversation with a clear understanding of what they are adding.

#### Work

- [ ] Avoid implying that AI matching is psychological assessment or that the character is a real person.

#### Exit criteria

- A user can complete discovery with touch buttons and TalkBack, without swiping.
- A right swipe or Add action cannot create duplicate friends.
- The created AI friend appears in the AI filter and opens the correct AI conversation.
- Filters and card descriptions work with Bangla/Hindi and large text.
- Character details shown in the deck match the profile and prompt configuration used for the chat.

### Phase 4 — People discovery and consensual matching

**Priority:** P1 for safety; P2 for matching enhancements  
**Outcome:** Human discovery feels deliberate and contact behavior cannot bypass recipient preferences.

#### Work

- [x] Persist People pass history for up to 90 days and provide an explicit **Start over** action.
- [ ] Add language matching only after profiles explicitly publish language metadata; do not infer language from country or UI locale.
- [ ] Make block/report available from the profile and chat; enforce these states in trusted rules/services, not only by hiding buttons.
- [ ] Add server-side discovery/send limits and operational abuse monitoring before raising discovery exposure.
- [ ] Do not add nearby sorting, exact distance, identity badges, or photo sharing in this phase.

#### Exit criteria

- A contact action obeys the recipient's current policy in the UI, backend, and Firestore rules.
- Blocked users cannot re-contact through an alternate request or chat path.
- Request accept/ignore/block/report behavior is tested across separate accounts.
- Profile visibility and contact settings are understandable and reversible.
- The product does not claim that a profile, age signal, or moderation flow verifies a person's identity or guarantees safety.

### Phase 5 — Everyday polish and measured rollout

**Priority:** P2  
**Outcome:** Improve daily use without adding risky social surfaces prematurely.

#### Work

- [ ] Add a call-history destination only if users need it and the data model can represent missed, incoming, outgoing, audio, and video calls reliably.
- [ ] Consider replies/quotes and emoji reactions with clear deletion and notification behavior.
- [ ] Define delete-for-me and delete-for-everyone semantics, retention, tombstones, and audit behavior before implementing message edits/deletes.
- [ ] Keep image/media sharing out of the initial release. If later approved, follow the existing Hostinger upload direction and hardening requirements; do not add Firebase Storage.
- [ ] Improve TalkBack, large-text, landscape, low-bandwidth, and multilingual coverage.
- [ ] Roll out through internal testing, a limited eligible adult beta, then staged expansion.
- [ ] Review support reports and reliability before increasing discovery traffic.

#### Exit criteria

- Each new data type has a purpose, access model, retention rule, deletion behavior, and privacy disclosure.
- Any media retrieval is authorized and quota-limited; a hard-to-guess URL is not treated as private.
- Staged rollout has a disable/rollback path for People without disabling AI chats.

## 6. Proposed technical boundaries

### Inbox aggregation

Use a view-model-owned, presentation-only aggregation of AI and People conversation summaries. Each summary should identify its source and stable ID. Tapping a row dispatches to the source-specific conversation flow.

Do **not**:

- copy People message bodies into AI conversation storage;
- route People messages through OpenRouter or AI diagnostics;
- apply AI memory, proactive messaging, or friend-personality behavior to human chats;
- merge Firestore access rules with local AI-chat permissions;
- expose one account's cached inbox after sign-out or account switch.

### Navigation

Replace ad-hoc navigation growth with a clear screen/back-stack contract that preserves selected conversation IDs, drafts, and filter state. Evaluate AndroidX Navigation Compose only if the existing screen-state approach cannot reliably support the new destinations; do not add it solely to change the visual shell.

### Dependencies and storage

- The initial roadmap does not require a new chat SDK, swipe SDK, or Firebase service.
- Keep using Compose/Material 3 and the existing Firebase services for their current responsibilities.
- Do not add Firebase Storage; the People roadmap requires the existing Hostinger upload path if media is approved later.
- Avoid changing `app/build.gradle` for UI-only phases unless a later implementation demonstrates a concrete missing capability.

### Data and rules changes

Any People schema or policy change must be accompanied by matching Firestore rules, indexes, migration/backfill behavior, emulator tests, and privacy documentation in the same change. Keep feature flags and rollback behavior for changes that affect real-person discovery.

## 7. Validation plan

### Product and UI validation

- Test 360dp compact phones, typical phones, tablet widths, portrait and landscape.
- Test TalkBack, largest practical system font scale, keyboard-visible composer, and Android back gestures.
- Test light and dark themes independently; verify text and non-text contrast.
- Test reduced motion and ensure swipe actions have button alternatives.
- Test English, Bangla, and Hindi overflow, line wrapping, and profile-card height.
- Verify every icon-only action has a meaningful accessible label and state.

### App behavior

- Unit-test inbox aggregation, source filtering, ordering, unread counts, empty states, duplicate prevention, and account switching.
- Add Compose UI tests for bottom navigation, filters, new-chat chooser, both discovery flows, request outcomes, chat opening, and system back.
- Test AI chat and People chat independently to catch accidental service crossover.
- Test two People accounts on separate devices, same-account multi-device use, offline/online transitions, process death, retry, and notification permission denied.
- Run Firestore emulator rules tests for profile visibility, contact policy, requests, blocks, and chat creation.
- Run the repository's normal Kotlin unit tests and debug build for implementation changes; no APK build is required for this documentation-only roadmap.

### Privacy-safe success measures

Track only coarse, preferably opt-in or aggregated measures:

- time to first conversation;
- successful send and retry rates;
- inbox load failures and crashes;
- discovery-to-add/request completion;
- request accept/ignore/block rates;
- voluntary return after one and seven days.

Do not collect message content for product analytics, optimize for notification volume, or use time-in-app as the primary success measure.

## 8. Suggested release milestones

| Milestone | Scope | Release gate |
|---|---|---|
| **M0 — Product and safety sign-off** | Confirm navigation, AI/human boundaries, contact modes, support and moderation readiness | No unresolved policy or enforcement gap for the next release |
| **M1 — Chat-first internal build** | Chats default, All/AI/People filters, New chat chooser, stable back behavior | No data crossover; inbox and navigation tests pass |
| **M2 — Messaging reliability beta** | Message states, retries, pagination, notification controls | Offline/restart/retry tests pass; notifications are privacy-safe |
| **M3 — AI discovery beta** | Refined AI deck, filters, accessible button alternatives, direct chat start | No duplicate adds; AI identity is explicit |
| **M4 — Controlled People discovery** | Contact-policy-aware swipe outcomes, requests, block/report, abuse limits | Firestore tests and operational safety gates pass |
| **M5 — Staged public expansion** | Accessibility/localization polish and measured rollout | Support, rollback, retention, and monitoring are active |

## 9. Related project references

- [APP_OVERVIEW_FOR_USERS.md](./APP_OVERVIEW_FOR_USERS.md) — current user-facing feature and privacy summary.
- [APP_IMPROVEMENT_AND_MARKETING_PROPOSALS.md](./APP_IMPROVEMENT_AND_MARKETING_PROPOSALS.md) — product priorities and messaging guidance.
- [PEOPLE_FEATURE_ROADMAP.md](./PEOPLE_FEATURE_ROADMAP.md) — People safety, messaging, matching, rules, and release gates.
- [UI-UX-REPORT.md](./UI-UX-REPORT.md) — existing Compose UI review and accessibility recommendations.
- [app/build.gradle](./app/build.gradle) — current Android and Firebase dependency configuration.
