# Dost UX Enhancement Plan

**Status:** In progress; core inbox, accessibility, People-contact, and message-recovery work is implemented, while on-device usability checks, People service deployment, and policy publication remain open.  
**Purpose:** Improve clarity, confidence, and ease of use beyond the chat-first foundation without adding engagement pressure or weakening privacy and safety.  
**Relationship to existing plans:** This is a follow-on to [CHAT_FIRST_PRODUCT_ROADMAP.md](./CHAT_FIRST_PRODUCT_ROADMAP.md), not a replacement for its People release gates or technical boundaries.

## 1. Product experience goals

Users should be able to:

1. Understand whether they are interacting with a fictional AI character or a real person.
2. Start or resume the right conversation without learning the app's internal structure.
3. Tell what happened to a message and what action is available when delivery is delayed or fails.
4. Find and control personalization, notification, privacy, and safety settings.
5. Use core flows comfortably with TalkBack, large text, different screen sizes, and the supported app languages.

Optimize for confidence and successful task completion, not time spent in the app, swipe volume, or notification opens.

## 2. Design guardrails

- Continue the repository's UI-UX-Pro-Max direction: calm minimalism, content-first screens, a consistent 4/8dp spacing rhythm, and the existing semantic blue/green theme.
- Prefer short, contextual explanations over a mandatory tour or repeated confirmation dialogs.
- Keep AI and People identities, data paths, and safety controls distinct even when their conversations share the inbox.
- Make important outcomes visible in text and accessibility semantics; do not communicate status with color or motion alone.
- Do not request notification permission, contacts, or other optional access before the user reaches a feature that needs it.
- Do not describe age eligibility as identity verification, message delivery as guaranteed, or AI memory deletion as conversation-history deletion.
- Reuse Compose, Material 3, existing storage, and existing services. Add no new SDK or backend solely for visual polish.

## 3. Current foundation to preserve

The existing [UI-UX-REPORT.md](./UI-UX-REPORT.md) records that its initial interface recommendations have been implemented: system-back behavior, adaptive friend-picker layouts, themed authentication, grouped Settings, stable list keys, localized chat feedback, accessible vector actions, and a Knowledge empty-state route. This plan does not reopen those implementation tasks.

The remaining opportunity is to make the broader journeys easier to understand, validate them on real devices, and improve trust and recovery around features that have important privacy or safety consequences.

The source audit also confirmed that People chat history is already paged behind a bounded live listener and that optional, generic People message-notification plumbing exists. The remaining notification work is deployment and two-account verification, not building a second notification system. People discovery now retains passed profiles for up to 90 days with an explicit restart action, and People-chat search covers loaded messages while older pages are loaded on demand.

## 4. Prioritized workstreams

### Phase 0 — Observe real user journeys

**Priority:** P0  
**Outcome:** Improvements target demonstrated friction rather than adding screens based on assumption.

#### Work

- [x] Map the first-session journey from sign-in/language selection to the first AI conversation and, for eligible adults, the first People discovery action.
- [x] Map recurring tasks: resume a conversation, find a past message, change chat settings, recover a failed send, review AI memory, respond to a request, and block or report a person.
- [ ] Run short, moderated usability sessions with adult volunteers using test accounts and fictional content. Include users who rely on TalkBack or larger text where available.
- [ ] Record task completion, hesitation, backtracking, and misunderstood labels; do not record message content or personal profile details.
- [ ] Separate usability observations from product decisions that require an explicit policy or safety review.

#### Task baseline

Use fictional content and test accounts. Record task outcomes and interaction friction only; do not capture message text or profile details.

| Journey | Starting point | Expected outcome | Success measure |
|---|---|---|---|
| First AI conversation | Signed-in account at language selection or Discover | Choose an AI friend and send the first message | User identifies the friend as fictional and reaches a conversation without entering People |
| First People action | Adult-eligible account at Discover | Read the profile's contact preference, then start a direct chat, send a request, or pass | User predicts the action's consequence before selecting it |
| Resume a conversation | Chats list | Reopen the intended AI or People conversation | Correct conversation opens and its identity is understood |
| Find an earlier message | Existing conversation | Locate a known test phrase in the history | User finds the phrase without confusing AI and People histories |
| Change chat language | Open AI conversation | Change the global AI reply language | User understands the setting applies across AI conversations |
| Recover an AI reply failure | AI conversation with a simulated reply failure | Retry the failed reply | Original user message remains and no duplicate reply is created |
| Recover a People send | People conversation with connectivity interrupted | See the on-device waiting state, reconnect, and confirm delivery | Message is delivered once and the waiting state resolves |
| Review AI memory | AI conversation | Open Knowledge, edit or remove a test fact, and return | User distinguishes saved memory from conversation history |
| Respond to a People request | Requests list | Accept or ignore a test request | Resulting chat or closed-request state is clear |
| Use a safety control | People profile or conversation | Block or report a test account | User finds the control, understands its effect, and sees the result |

#### Exit criteria

- Each core task has a defined starting point, expected outcome, and success measure.
- The team can identify the highest-friction steps without collecting private conversation content.

### Phase 1 — First-session orientation and first conversation

**Priority:** P0  
**Outcome:** A new user understands the two kinds of friends and can start with an appropriate next step.

#### Work

- [x] Add brief, dismissible Discover guidance explaining that AI friends are fictional, People are other users, and their conversations use separate systems.
- [x] Keep the initial choice simple: start with an AI friend or explore People when the account is eligible. Do not combine both profile types into one deck.
- [x] Explain the consequence of a People action before the user commits: direct chat, contact request, or unavailable for new chats.
- [x] Keep the guidance optional and reopenable from Discover so users can skip it and return later.
- [x] Review language selection and first-run defaults so English, Bangla, and Hindi choices are visible and reversible. First-run setup, sign-in, and Settings provide reversible language choices.
- [x] Ensure discovery, notification, and profile-permission prompts are contextual; never make optional permissions a prerequisite for AI chat.

#### Exit criteria

- In usability testing, users can correctly distinguish AI from People and predict what each primary action will do.
- A new user can reach a first AI chat without being forced through People discovery or optional permission prompts.
- The first-session path remains usable after sign-out, account switching, process recreation, and language changes.

### Phase 2 — People trust, requests, and safety controls

**Priority:** P0 for any increase in People exposure  
**Outcome:** Users understand contact state and can take protective actions from the places they need them.

#### Work

- [x] Present a concise explanation of the recipient's contact preference at discovery and before sending a request; avoid repeating lengthy policy text after the user understands it.
- [x] Add a clearly described optional **Mutual match first** contact preference while retaining direct-contact and request-first options.
- [x] Make pending, accepted, ignored, and unavailable states distinguishable with a short explanation and a relevant next action.
- [x] Provide clearly separated **Block** and **Report** actions from the People profile and conversation. Explain the effect of blocking, what a report includes, and how to get help. The UI is built and the Firestore rule is deployed; publish the revised privacy policy before a production app release.
- [x] Confirm consequential actions without making routine actions burdensome; keep block/report available through accessible controls, not gestures alone.
- [x] Design safe failure states for requests, permission errors, rate limits, offline actions, and profiles that no longer accept contact. People errors now use localized, non-technical guidance; stale profiles return an unavailable state, and failed report submissions keep the reason available for retry.
- [x] Validate that UI copy matches Firestore rules and trusted-service behavior; do not imply that a profile, age signal, or report verifies identity or guarantees safety. The Firestore emulator suite passes, including direct-contact, request-only, and no-new-chat enforcement; live two-account validation remains a release gate.

#### Exit criteria

- In a two-account test, users can identify the request state, understand why a new contact is unavailable, and find block/report without assistance.
- Blocked users cannot re-contact through an alternate request or chat path.
- Report submission is not presented as an emergency service or as an automatic account suspension.
- The People safety and moderation release gates in [PEOPLE_FEATURE_ROADMAP.md](./PEOPLE_FEATURE_ROADMAP.md) remain satisfied before broader exposure.

### Phase 3 — AI transparency and memory control

**Priority:** P1  
**Outcome:** Users can understand and adjust personalization without confusing saved memory with chat history.

#### Work

- [x] Make saved-memory management directly reachable from the conversation, filtered to the current AI friend.
- [x] Add an in-chat route to the global AI chat-language picker; keep per-friend proactive-reply controls available through Friend profile.
- [x] Explain in Knowledge what is saved for an AI friend and how it can affect future replies.
- [x] Make review, correction, and deletion of saved facts easy to find, with a clear result after each change.
- [x] Distinguish local personalization data from server-side conversation archives and explain their separate deletion controls.
- [x] Use a consistent, non-alarming disclosure that AI friends are fictional and replies may be inaccurate in Discover, the AI picker, and friend details.
- [x] Make empty, stale, and unavailable AI catalog states distinguishable, with retry or back options that do not pressure the user to add a friend.

#### Exit criteria

- A user can locate and change a saved fact for one AI friend without changing another friend's memory.
- Confirmation copy accurately describes which data is removed and which chat records remain.
- AI identity and personalization controls remain clear in English, Bangla, and Hindi.

### Phase 4 — Everyday messaging confidence

**Priority:** P1  
**Outcome:** The app explains message progress and notification behavior in a calm, recoverable way.

#### Work

- [x] Review sending, waiting, sent, and failed states in both AI and People chats. Keep each service's different delivery guarantees explicit.
- [x] In People chat, identify waiting messages as saved on the device and politely announce the latest outgoing delivery state to screen readers.
- [x] Keep AI reply failures visible in the conversation with a direct retry action, so dismissing an alert cannot hide recovery.
- [x] Make retry actions easy to locate and verify that retrying does not duplicate a message; preserve the user's text when a send fails where the current data flow permits it.
- [x] Design a low-disruption offline state that explains what remains available, what is waiting, and when retry is possible.
- [x] Keep notification previews privacy-safe by default and make opt-in, per-chat mute, and system permission states understandable from Settings and the relevant conversation.
- [x] Explain when an operating-system permission is denied and provide a settings route only when the user asks to enable the affected feature.
- [x] Review message timestamps, send errors, and status wording for consistent localization and screen-reader announcements. People timestamps and delivery states are localized, send failures use localized recovery guidance, and the latest outgoing state is announced politely.

#### Exit criteria

- Users can distinguish a queued message from a sent message without relying on color.
- Reconnect and retry tests show no lost or duplicated message and give an understandable result.
- Muted chats and denied notification permission do not silently override the user's choice or block normal chatting.

### Phase 5 — Accessibility and adaptive visual quality

**Priority:** P1  
**Outcome:** Core tasks remain usable across assistive settings, languages, and device constraints.

#### Work

- [x] Make the floating chat bubble accessible as a click target and localize its chat title, friendship score, and unread-count digits.
- [ ] Test the core journeys at compact phone widths, short-height layouts, landscape, large system fonts, and keyboard-visible states.
- [ ] Audit TalkBack order, focus after dialogs and navigation, selected states, unread announcements, message status, and request outcomes.
- [ ] Verify every swipe action has a button alternative and that gestures do not conflict with Android back navigation or scrolling.
- [ ] Validate light and dark themes for chat bubbles, disabled actions, dialogs, status indicators, authentication, and safety flows.
- [ ] Check English, Bangla, and Hindi for truncation, wrapping, mixed-script text, date/time readability, and action-button fit.
- [ ] Measure contrast for text and meaningful controls, and keep interactive targets at least 48dp.
- [ ] Respect reduced-motion settings and ensure transitions never delay send, retry, back, or safety actions.

#### Exit criteria

- No primary action or safety control is clipped, gesture-only, or missing an accessible name/state.
- Core flows complete with TalkBack and large text without loss of meaning or access to actions.
- No unsupported language or device configuration produces a blank, stuck, or success-shaped error state.

### Phase 6 — Learn and iterate responsibly

**Priority:** P2  
**Outcome:** Product decisions use evidence while preserving privacy and user choice.

#### Work

- [ ] Re-run the same task-based usability checks after each major UX phase and compare completion, errors, and user understanding with the baseline.
- [ ] Use only existing opt-in, allowlisted, aggregate analytics for product measures; do not collect message text, profile fields, contact identifiers, or per-user behavioral histories.
- [ ] Track coarse measures such as successful first-chat completion, send/retry recovery, discovery-to-request completion, and reported navigation confusion.
- [ ] Review support reports and reliability before changing discovery exposure or adding engagement features.
- [ ] Remove metrics that do not lead to a user-benefiting decision; do not optimize for time in app, swipe count, or notification volume.

#### Exit criteria

- Each tracked measure has a documented purpose, privacy basis, retention period, and owner.
- Rollout decisions include qualitative feedback and safety/reliability evidence, not engagement counts alone.

### Phase 7 — Trust-first Premium tier

**Priority:** P1 after purchase, entitlement, and abuse controls are designed  
**Outcome:** Premium adds convenience and personalization without making free users less safe or less able to communicate.

#### Current baseline

- The app has no Google Play Billing integration or premium entitlement.
- AI-friend limits are 3 custom friends and 7 catalog friends. Encrypted backup import also caps friend records at 10.
- People discovery does not currently have an explicit like quota. Positive contact actions can start a chat or create a contact request, depending on the recipient's policy.
- People discovery is currently alphabetical and capped at 100 profiles per query; there is no premium ranking or boost service.

#### Confirmed Premium benefits

- **Expanded AI circle:** remove the product caps on custom and catalog AI friends. Keep duplicate-catalog prevention and add storage, background-work, export, and backup safeguards before calling the limit unlimited.
- **Unlimited People connections and likes:** do not impose a Premium-only friend-count cap on real People connections. Premium users receive unlimited People likes; free users receive 5 positive People discovery actions per rolling 24-hour window. Enforce the quota and Premium exemption in a trusted service, not a local counter.
- **Higher profile visibility:** one explicit, user-triggered 30-minute boost per rolling 24-hour window while Premium is active. It must not bypass adult eligibility, profile visibility, contact policy, blocks, or safety rules, and must not demote free profiles below the current baseline.
- **Additional selected benefits:** chat appearance themes and expanded AI-friend customization. Keep safety features, core chat, privacy controls, and user data export available to everyone. AI voice remains deferred until provider, cost, and data-retention decisions are made.

#### Billing and entitlement requirements

- Offer monthly and annual subscriptions plus a one-time lifetime purchase, using localized regional prices configured in Play Console. Proposed product IDs: `dost_premium_monthly`, `dost_premium_annual`, and `dost_premium_lifetime`.
- Use Google Play Billing for Play-distributed builds, with clear price, billing period, renewal/cancellation terms, purchase restore, pending-payment, grace-period, refund, and expired states.
- Verify purchases through a trusted server entitlement path before enabling server-sensitive benefits. Never unlock unlimited People actions or discovery priority from a client-only Boolean.
- Apply entitlement checks at every relevant boundary: AI-friend creation, People like quotas, and trusted discovery ranking. Entitlement expiration must return accounts to the free allowance without deleting existing friends or chats.
- Show an accessible, localized Premium page with a useful free-tier comparison and no blocking paywall on safety, settings, or existing conversations.

#### Implementation work remaining

- Add the three Play Console products and configure the Android Publisher service-account permissions outside the repository.
- Complete subscription renewal, refund, and revocation handling; the current verification path grants claims from a verified purchase but does not yet consume Real-time Developer Notifications.
- Enforce the free 5-like rolling 24-hour quota and Premium exemption at a trusted boundary; do not rely on a client-only counter.
- Add trusted 30-minute boost storage, once-per-rolling-24-hour cooldown, and a discovery ranking path that respects eligibility, privacy, contact policy, blocks, and safety.
- Add chat themes and expanded AI-friend customization.
- Revisit the 1,000-friend encrypted backup/import safety cap and aggregate file-size limits; retain bounded parsing and malformed-file rejection.
- Add a technical fair-use guardrail for AI profile generation, background jobs, and storage while keeping the Premium product limit removed.

The repository now includes a Play Billing purchase/restore screen, Google Play purchase-token verification endpoint, Firebase Premium custom claims, and entitlement-gated AI companion caps. Do not activate Play Console products for production until the remaining benefits above and subscription renewal/revocation handling are complete and tested.

#### Exit criteria

- Premium purchases and entitlements are verified, restorable, and tested through purchase, renewal, cancellation, refund, expiry, offline, and account-switch states.
- Free quotas cannot be bypassed by reinstalling, changing devices, or editing local preferences.
- Profile ranking remains adult-eligible, privacy-safe, block-aware, and transparent; no safety control is paywalled.
- Existing user data, chats, and friends remain available if a subscription expires.

## 5. Release sequence

| Milestone | Scope | Gate |
|---|---|---|
| **UX-0 — Baseline** | Observe and measure core tasks | Usability findings and task definitions are documented |
| **UX-1 — First-session clarity** | Explain AI/People distinction and first actions | Users understand the consequences and can start an AI chat |
| **UX-2 — People trust** | Request explanations, block/report discovery, safe failures | Two-account scenarios pass and People operational gates are ready |
| **UX-3 — AI control** | Discoverable memory and personalization controls | Users can inspect and change memory without confusing it with archives |
| **UX-4 — Messaging confidence** | Delivery, retry, offline, and notification explanations | Recovery tests pass without duplicates or misleading delivery claims |
| **UX-5 — Inclusive polish** | Accessibility, localization, and adaptive layouts | Core task matrix passes on supported configurations |
| **UX-6 — Measured rollout** | Re-test, review support signals, adjust carefully | Privacy-safe evidence supports the next rollout decision |
| **UX-7 — Premium** | Play Billing, trusted entitlement, AI-friend capacity, People likes, optional visibility boost | Purchase lifecycle, fairness, and server-enforcement tests pass |

Phases UX-2 and UX-3 may proceed independently after UX-0. Do not increase People discovery traffic until UX-2 and the existing People safety gates are complete. UX-6 is an ongoing review cycle rather than a one-time feature release.

## 6. Out of scope for this UX plan

- Read receipts, typing indicators, online/last-seen status, or engagement pressure.
- Nearby sorting, exact distance, identity badges, or combining AI and real-person profiles.
- Message media, edits, or deletion semantics before the separate data/retention decisions in the chat-first roadmap.
- A new analytics pipeline, chat SDK, or storage service.
- Copying another messaging or dating app's brand, screen layout, or interaction details.

## 7. Related references

- [CHAT_FIRST_PRODUCT_ROADMAP.md](./CHAT_FIRST_PRODUCT_ROADMAP.md) — product direction, existing implementation, release gates, and technical boundaries.
- [UI-UX-REPORT.md](./UI-UX-REPORT.md) — Compose review, UI-UX-Pro-Max guidance, and prior recommendations.
- [PEOPLE_FEATURE_ROADMAP.md](./PEOPLE_FEATURE_ROADMAP.md) — People safety, moderation, and release prerequisites.
- [APP_OVERVIEW_FOR_USERS.md](./APP_OVERVIEW_FOR_USERS.md) — current user-facing functionality and privacy disclosures.
