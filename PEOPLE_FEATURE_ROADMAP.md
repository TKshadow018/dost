# Dost People: Feature and Delivery Plan

## 1. Product goal

Extend Dost from AI companion chats into a safe, useful real-person messaging and discovery product. The AI-friend experience remains a distinct product feature; real people must never be routed through the AI prompt, OpenRouter proxy, or AI conversation archive.

Use familiar interaction patterns from mainstream messaging and dating products as inspiration, but build Dost's own visual design, terminology, safety model, and feature set rather than copying another app's branding or exact interface.

One-to-one live audio and video calling are available as separate WebRTC media modes; text chat remains separate from call media. Voice notes and audio-message services remain out of scope.

## 2. Current baseline

The app already has a first, minimal real-person experience:

- Firebase Authentication accounts are used to identify people.
- A user may publish a profile containing display name, broad age band, gender, country, optional bio, and interests.
- The People screen discovers published profiles and presents a swipe deck.
- Selecting a profile opens an existing chat, starts a chat immediately when the recipient allows everyone, sends a contact request when required, or is refused when the recipient has disabled new chats.
- Profile owners can also opt into **Mutual match first**: an interest request remains pending until the recipient accepts it, and only then can the chat open. Existing contact preferences remain unchanged.
- Chat text is stored in Firestore and displayed live while the chat screen is open.
- One-to-one audio and video calls use WebRTC media with Firestore signaling, FCM incoming-call alerts, and short-lived TURN credentials from the Hostinger API. The Android implementation still requires service deployment, TURN provisioning, Firestore TTL configuration, and device testing before release.
- Users can block or report a person. Reports are restricted to Firebase project administrators and have a manual 72-hour review target, a support-email appeal path, and emergency-services guidance; there is no in-app moderation dashboard or automated report notification.
- The app is for ages 13 and older. Signup asks for birth month and year; under-13 users are blocked locally before account creation. Play Age Signals can block a shared age range below 13 but does not gate teens from the app. Explore Plus and services that bundle it remain 18+.
- Firestore People profile rules accept adult age bands and the `adult` cohort only, deny reads of legacy under-18 People profiles and chats, and require the server-issued `dostAgeCohort == adult` claim plus an unexpired eligibility claim. Before opening People, the app binds the Firebase UID and eligibility result to a Play Integrity Standard token; the Hostinger API validates it and refreshes the claim for six hours. Teens do not use People discovery or stranger chat.
- Real-person text chats support optional generic FCM message alerts, off by default, when the notification endpoint is deployed and configured. Sender-facing read receipts, typing indicators, online status, group conversations, media messages, and in-app social-chat deletion are not available. Voice notes are not planned.
- People chat history uses a bounded 500-message live listener with on-demand older pages. Search covers messages currently loaded on the device; load older pages to search further back. Passed discovery profiles persist for up to 90 days and can be cleared with **Start over**.
- Current Firestore collections, rules, and indexes are described in [FIRESTORE_SETUP.md](./FIRESTORE_SETUP.md), [firestore.rules](./firestore.rules), and [firestore.indexes.json](./firestore.indexes.json).
- Current data practices are described in [the privacy policy](./app/api/privacy-policy.txt).

## 3. Product principles and decisions

1. **Separate AI and human conversations.** Keep separate models, screens, storage paths, notifications, exports, retention, and privacy disclosures.
2. **Privacy by default.** A profile is private until published. Never publish email, exact age, exact location, Firebase UID, or contact details. Ask separately before uploading each photo or sharing optional profile fields.
3. **Age safety before growth.** The app is rated 13+; users under 13 are excluded and teens may use age-appropriate app features. People discovery/chat and Explore Plus remain adults-only. Play Age Signals is an age-band source, not a whole-app blocker; server claims and eligibility checks must protect restricted features. Do not describe Play Age Signals or a birth-month/year entry as proof of age.
4. **Control unwanted contact.** Profile preferences can now allow everyone, require requests, or disable new chats; requests support accept, ignore, block, and report. Trusted account-level rate limits and report-response operations are still required before increasing discovery traffic.
5. **Explicit retention and deletion.** Every new data type needs a stated purpose, retention period, user deletion behavior, and administrative cleanup path.
6. **Secure server-authoritative actions.** Clients must not be trusted to assert moderation state, age eligibility, message delivery, or privileged group roles.
7. **Deliver vertical slices.** Ship reliable, tested, privacy-reviewed increments; do not launch public stories or arbitrary uploads as a single large change.
8. **Avoid new storage charges for now.** Do not add Firebase Storage. Reuse and harden the existing Hostinger upload endpoint and its `baseUrl` to write images into dedicated folders under the existing uploads directory.

## Owner-approved product defaults

These are approved targets for implementation, not claims about features already shipped:

- Keep direct chats available, but add user controls over who may contact them. Mutual matching must not become the only way to start a conversation.
- Prepare marketing materials as drafts only. Do not publish, contact creators, or spend money without separate approval.

## Product choices confirmed

- Add mutual-match as an optional contact preference while preserving direct contact and request-first behavior; existing profiles are not migrated.
- Plan both profile photos and People chat images, using authenticated Hostinger upload and retrieval. Implementation remains blocked on upload authorization, quotas, deletion, and privacy-review requirements below.
- Keep AI conversations local with encrypted export/import; do not add cloud sync.
- Defer AI voice and all People-message deletion/retention semantics. The earlier 36-month retention and 15-minute delete-for-everyone proposal is not approved for implementation.

## 4. Prioritized roadmap

### Phase 0 — Safety, policy, and release readiness

**Priority: P0; prerequisite for broad public discovery**

- [x] Decide and document the 13+ app policy, under-13 signup block, adult-only People eligibility, and Play Age Signals behavior.
- [x] Replace client-controlled age-cohort assertions with a server-issued, six-hour eligibility claim. The Hostinger endpoint verifies the Firebase identity and Play Integrity request before updating custom claims; Firestore rules enforce claim presence and expiry.
- [x] Review the discovery query and rules with Firestore Emulator tests, including missing/expired eligibility claims, adversarial cohort changes, cross-user access, blocked chats, forged participant snapshots, and sends after a block. These controls bind eligibility to a genuine Play-installed app, but do not independently verify the source of the Play Age Signals range.
- [x] Define report handling: Firebase project administrators review manually within a 72-hour target; reports do not trigger automatic suspension or profile hiding; users may appeal actions through the published support email; reports are not emergency support and immediate danger should be directed to local emergency services.
- [ ] Add spam and abuse controls: account-level send limits, new-account restrictions, discovery throttles, duplicate-chat prevention, and abuse monitoring.
- [ ] Publish clear Community Guidelines, Terms of Service, privacy disclosures, social-content retention rules, and support/contact route.
- [ ] Add an operational process for removing abusive profiles, chats, uploaded media, and accounts.
- [x] Add a build-time People feature flag that can disable discovery and human chat without affecting AI chats. It defaults to enabled and is documented in [FIRESTORE_SETUP.md](./FIRESTORE_SETUP.md).

**Exit criteria**

- Security rules pass automated emulator tests for allowed and denied access.
- Before release, the operator must configure and test the Hostinger service-account environment, link the Play Console project, enable Play Integrity, and deploy the matching Firestore rules.
- A report reaches the documented Firebase-admin review process and can be investigated.
- Age-separation claims match the actual verified controls; otherwise, the feature is not represented as an enforced safety boundary.
- Privacy, retention, blocking, and deletion behavior are documented before public rollout.

### Phase 1 — Messaging foundation

**Priority: P1; build on the current text-chat feature**

- [x] Create a dedicated People inbox with conversation previews, latest activity, unread badges, and stable ordering.
- [x] Add optional FCM push notifications for new messages, with per-device notification preferences, per-chat mute, and privacy-safe generic lock-screen text. The client and PHP endpoint are implemented; production deployment and two-account delivery still require verification.
- [x] Add sending, sent, waiting, and failed/retry states. Sender-facing read receipts are not available; do not suggest a delivery guarantee that Firestore has not confirmed.
- [x] Add unread tracking and mark-as-read behavior that works across app restarts and multiple devices.
- [x] Add pagination and bounded listener windows for long conversations; do not load an unlimited message history into one listener.
- [x] Handle offline sending and reconnect/retry without duplicate messages, using stable client-generated message IDs.
- [ ] Add typing indicators and online/last-seen status only as opt-in settings, with an explicit "hide my status" option.
- [x] Add People conversation text search over loaded history, on-demand older-page loading, and clear navigation between People chats and AI chats.
- [x] Ask before enabling People message notifications and keep them off by default; Android notification permission is also required.

**Exit criteria**

- Messages, unread counts, and notifications remain consistent after process death, offline use, and sign-in on a second device.
- Push payloads do not contain message text unless the user explicitly enables previews.
- Sending retries do not duplicate messages; users can identify and retry failed sends.

### Phase 2 — Everyday one-to-one messaging

**Priority: P1/P2; stage features behind migrations and rules**

- [ ] Add message replies/quotes and emoji reactions.
- [ ] Add edit and delete-for-me behavior, then consider delete-for-everyone with visible tombstones and a defined time window.
- [ ] Add image sharing through the existing Hostinger upload service in `app/api/default.php`. Use its multipart `image` and `folder` fields to create/use dedicated `people-profiles` and `people-chat` folders under `/uploads/`; return URLs using the endpoint's configured `baseUrl`, and store only the resulting media reference and metadata in Firestore.
- [ ] Before production use, harden the upload path: verify the Firebase ID token server-side; authorize profile ownership or conversation membership; allow only the intended folder names; generate filenames on the server; verify actual image content; enforce image dimensions, file-size and per-account quotas; prevent path traversal and directory listing; and provide authorized retrieval and deletion.
- [ ] Treat `baseUrl` URLs as public unless the serving endpoint checks authorization. Do not put private chat images at unrestricted public URLs; implement authenticated media delivery or explicitly approve and disclose the residual public-link risk before release.
- [ ] Add upload progress, retry behavior, local image compression, and deletion of image files when the owning profile/message/account is deleted. Do not add Firebase Storage.
- [ ] Add contact cards or location sharing only as a later opt-in feature; never collect or show exact location by default.
- [ ] Add conversation mute, archive, pin, and user-controlled notification settings.
- [ ] Add a clear account/data export and social-conversation deletion process.
- [ ] Do not add arbitrary file attachments until malware handling, MIME validation, storage limits, and reporting are in place.

**Exit criteria**

- Images use Hostinger-hosted folders and `baseUrl`, not Firebase Storage; the endpoint validates ownership, image content, and size, prevents path traversal, and supports documented deletion.
- Chat media is not treated as private merely because its URL is hard to guess; authorization is enforced when media is retrieved or any residual public-link risk is explicitly disclosed.
- Message mutation behavior is explicit to both participants and covered by rules/tests.
- The privacy policy accurately describes what content is stored, who can see it, and for how long.

### Phase 3 — Profiles, discovery, and matching

**Priority: P1/P2; refine the existing swipe experience**

- [ ] Improve profile creation with previews, photo crop/reorder, optional prompts, interest tags, and clear visibility controls. Profile photos and chat images are selected for future implementation but remain release-gated on secure upload/retrieval, deletion, quotas, and policy updates.
- [ ] Upload optional profile photos to the Hostinger `people-profiles` folder using the existing endpoint and `baseUrl`; do not provision Firebase Storage.
- [ ] Add a profile completeness preview and let users hide or delete a profile without deleting their Firebase account.
- [x] Persist pass history for up to 90 days across sessions and provide a clear **Start over** action.
- [ ] Add user-controlled discovery preferences such as language and broad interests; avoid sensitive or discriminatory targeting.
- [ ] Consider an allowlist mode for people explicitly selected by the user after the request and block flows are stable.
- [x] Let users control who may contact them: everyone in their eligible group, requests first, or nobody. Existing profile documents without a preference retain the previous everyone-can-start behavior.
- [x] Add an incoming message-request inbox with accept, ignore, block, and report actions. Accepted requests create an immutable, conversation-keyed approval record; a handled request cannot be recreated for the same pair. Trusted account-level rate limits remain outstanding.
- [x] Add optional match-first contact preference. An interest request cannot open a chat until the recipient accepts it; direct and request-first modes remain available without migration.
- [ ] Add profile verification only if a real, privacy-preserving verification process and appeal path exist. Do not imply that a badge proves identity or safety beyond the checks performed.
- [ ] Avoid exact location, distance sorting, or "nearby" discovery until a separate privacy and safety review approves it.

**Exit criteria**

- Users can understand and change profile visibility and contact permissions.
- Rejected, blocked, and rate-limited requests cannot be silently retried by creating duplicate records.
- Discovery is paginated, deterministic enough for a good user experience, and protected against scraping.


### Phase 4 — Scale, trust, and account lifecycle

**Priority: continuous**

- [ ] Add moderator tooling, report queues, audit logs, user appeals, and emergency disable controls.
- [ ] Add profile/message/account deletion workflows, with clear behavior for copies retained by other participants.
- [ ] Define retention and automated cleanup for profiles, chats, message media, reports, blocks, and notification tokens.
- [ ] Add Firebase App Check, server-side rate limiting, anomaly alerts, quota/cost budgets, and abuse dashboards.
- [ ] Add accessibility and localization coverage for screen readers, text scaling, RTL/Bangla/Hindi layouts, and low-bandwidth devices.
- [ ] Use staged releases, crash monitoring, performance metrics, and opt-in research before increasing discovery traffic.
- [ ] Revisit encrypted transport/storage and evaluate end-to-end encryption as a separate architecture project; do not imply end-to-end encryption unless implemented and independently reviewed.

## 5. Target data and service boundaries

Keep the initial Firebase design, but evolve it with explicit ownership and retention:

- `publicProfiles/{uid}` — minimal, explicitly published profile; no email or exact age.
- `peopleChats/{chatId}` — participants, age eligibility snapshot/reference, preferences, and last-message metadata.
- `peopleChats/{chatId}/messages/{messageId}` — bounded message records with sender, server timestamp, type, and validated content reference.
- `peopleContactRequests/{recipientUid}/incoming/{senderUid}` — one pending request per sender/recipient pair, with a profile snapshot, status, and response metadata; Firestore rules gate chat creation on recipient acceptance when requests are required.
- `peopleContactApprovals/{chatId}` — immutable recipient authorization created when a contact request is accepted; the rules require it to match the chat's ID and participants when a request-controlled chat is created.
- `peopleBlocks/{uid}/blockedUsers/{otherUid}` — block state enforced for discovery, requests, messages, and media access.
- `peopleReports/{reportId}` — restricted report intake with a moderator-only access path and retention policy.
- Existing Hostinger upload service (`app/api/default.php`) — profile and chat images stored in dedicated `people-profiles` and `people-chat` folders under `/uploads/`, with returned URLs based on `baseUrl`; do not provision Firebase Storage. Upload and retrieval must enforce Firebase identity and ownership/conversation authorization, apply size/type/quota limits, and support deletion.
- Cloud Functions or a trusted backend — age-cohort derivation/eligibility, notification dispatch, send throttles, report workflows, cleanup, and actions that must not rely on client honesty.
- Firebase Cloud Messaging — notification delivery only; minimize sensitive content in payloads and honor per-user notification settings.

Any collection/schema change should include matching Firestore rules, indexes, emulator tests, migration/backfill behavior, and privacy documentation in the same change.

## 6. Cross-cutting quality plan

### Security and abuse tests

- Verify unauthenticated users cannot read profiles, chats, reports, or media.
- Verify one user cannot forge the other user's profile snapshot, sender UID, read state, role, or report identity.
- Verify legacy under-18 profile and chat data cannot be read, and adult-only profile rules reject under-18 age bands.
- Verify blocks prevent further contact in all relevant paths and do not rely only on a UI filter.
- Verify request/message rate limits and repeated-report protections.
- Verify deleted media cannot be fetched through stale or guessed paths and chat images cannot be read by non-participants.

### Product and reliability tests

- Test two accounts on separate devices, same account on two devices, offline/online transitions, duplicate sends, app restarts, and push notification permission denied.
- Test accessibility, keyboard/IME behavior, large text, narrow screens, and Bangla/Hindi localization.
- Test account switching and sign-out so one user's social data is never displayed to another user on the same device.
- Test data export, deletion, retention cleanup, and profile visibility transitions.

### Release gates

- Unit tests for models, validation, age eligibility, and state transitions.
- Firestore Emulator rules tests for both allowed and denied operations.
- Integration tests for real-time listeners, notification delivery, offline retry, and deletion.
- Security/privacy review for each new data type and permission.
- Update [the privacy policy](./app/api/privacy-policy.txt), [Firestore setup guide](./FIRESTORE_SETUP.md), and user-facing help before releasing behavior that changes data collection.
- Build and install a fresh debug APK; verify a signed release build separately when signing credentials are configured.

## 7. Recommended implementation order

1. Complete Phase 0 safety, age-eligibility, moderation, and privacy decisions.
2. Fix the messaging foundation: inbox, notifications, unread state, pagination, and reliable retries.
3. Add message requests and contact controls before expanding one-way discovery.
4. Add text-chat quality features, then Hostinger-backed profile and chat image sharing using `baseUrl`; do not add Firebase Storage.
5. Improve profile/discovery and add mutual-match controls.
6. Add groups only after roles and moderation tooling.
7. Add statuses only after audience and media-retention controls.
This order prioritizes a trustworthy conversation experience over a large feature checklist. Audio and video calling are separate, optional one-to-one features; voice notes remain out of scope. The current AI companion features remain Dost's differentiator and should continue to receive dedicated product and reliability work alongside People.

Before a Play release, set target age groups to 13–15, 16–17, and 18+, exclude under-13 audiences, and turn off whole-app Restrict Minor Access. Test Age Signals with a Play-installed build or registered license tester. Complete the owner checklist in [SETUP_AND_BUILD_GUIDE.md](./SETUP_AND_BUILD_GUIDE.md); these Play Console changes are not made by this repository.
