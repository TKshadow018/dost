# Dost Development Roadmap

## Project decision

**Build Dost into a capable AI friendship app—not a generic chat client.** The product direction is fixed: users create or meet distinct AI friends, build a relationship over time, the friends remember relevant conversation, have their own routines and busy periods, and can initiate messages.

This roadmap improves how reliably and enjoyably those capabilities work. It does not remove, hide, or redefine them. Every feature below should make at least one core capability more expressive, useful, or easy to use.

## Non-negotiable product capabilities

Every release must preserve and improve these five behaviors:

1. **Distinct personas:** every friend has a consistent identity, voice, interests, and life details.
2. **Long-term memory:** friends can recall useful information from earlier conversations.
3. **A life and schedule:** friends have routines and can be busy, with replies reflecting that.
4. **Proactive conversation:** friends can start or resume conversations without waiting for the user to open chat.
5. **Relationship growth:** repeated, meaningful conversations change how familiar and close the friend feels.

The app should remain welcoming and straightforward for someone who only wants to chat, while making these capabilities visible to people who want a deeper experience.

## Current implementation baseline

- `Friend` already contains persona details, a greeting, a relationship premise, busy hours, a reason for being busy, and a time zone. System-friend profiles are loaded from the server catalog.
- The chat prompt includes persona, relationship state, relevant user memories, and selected previous-session summaries.
- User memories are stored per friend. The app can edit or delete facts in Knowledge; a turn currently retrieves up to six relevant facts.
- WorkManager schedules busy replies, morning greetings, session summaries, and some follow-ups.
- Friendship and romance scores exist; score changes are clamped, regeneration does not apply the change twice, and romance is age-gated.
- Chat, friends, and profile memories are stored on-device; the server also archives conversation messages and images.
- The recent UI pass created dedicated Settings and Knowledge screens, an independent friend picker, and a Messenger-style chat. Build details are in [UI-UX-REPORT.md](UI-UX-REPORT.md).

Primary implementation areas are [AppModels.kt](app/src/main/java/com/snigtus/bondhu/AppModels.kt), [HomeScreens.kt](app/src/main/java/com/snigtus/bondhu/HomeScreens.kt), [SystemFriendPicker.kt](app/src/main/java/com/snigtus/bondhu/SystemFriendPicker.kt), [ChatScreen.kt](app/src/main/java/com/snigtus/bondhu/ChatScreen.kt), [KnowledgeScreen.kt](app/src/main/java/com/snigtus/bondhu/KnowledgeScreen.kt), [ConversationManager.kt](app/src/main/java/com/snigtus/bondhu/ConversationManager.kt), [ConversationWorkScheduler.kt](app/src/main/java/com/snigtus/bondhu/ConversationWorkScheduler.kt), [DostStore.kt](app/src/main/java/com/snigtus/bondhu/DostStore.kt), and [OpenRouterClient.kt](app/src/main/java/com/snigtus/bondhu/OpenRouterClient.kt).

## Release priorities

| Priority | Feature | User outcome | Size |
|---|---|---|---|
| P0 | Fix baseline and message reliability | Chat, scheduling, and builds are dependable enough for feature work. | M |
| P1 | Friend profile and character customization | Users can understand, choose, and shape a distinct friend. | L |
| P1 | Memory Center and in-chat memory actions | Users can inspect, correct, and build useful continuity. | L |
| P1 | Friend activity and proactive-message controls | Initiated conversations are dependable, understandable, and configurable. | L |
| P2 | Relationship stages and shared moments | Relationship growth is visible and connected to conversation history. | M |
| P2 | Conversation tools and follow-up plans | Users and friends can carry topics and agreed plans forward. | M |
| P2 | Home and friend-management improvements | Users can quickly see which friend to talk to and what is happening. | M |
| P3 | Character catalog operations and evaluation | New characters can be added without reducing persona quality. | M |

Priorities are ordered for delivery. Character, memory, scheduling, proactive messaging, and relationship development remain the core; P2/P3 describe sequencing, not optional product direction.

---

## P0 — Make the existing product dependable

### 0.1 Restore a clean test baseline

**Developer work**

- Triage the known failure `ExampleUnitTest.generatedProfileReplacesDefaultsBeforeChatCanStart` before adding more behavior.
- Separate the cause of that failure from product changes; repair the fixture or implementation rather than weakening the assertion.
- Add focused tests around the existing high-value logic before extending it: `replyAvailableAt`, `nextMorningGreetingAt`, relationship deltas, prompt memory retrieval, and friend deletion.
- Keep the required debug APK build as a release check. For Kotlin changes, run the debug unit tests before assembling.

**Done when**

- The existing unit suite passes or has a documented, isolated baseline exception with an owner.
- Schedule and relationship tests cover boundary conditions, not just happy paths.
- New features can be verified without relying on manual chat testing alone.

### 0.2 Make send/retry states obvious

**Developer work**

- Give each outgoing message an explicit state: composing, queued, sending, sent, or failed.
- Persist a failed user message and offer a localized **Retry** action that resends that message instead of asking the user to copy it.
- Prevent a WorkManager retry or network retry from creating a second visible message or applying relationship points twice.
- Show a concise offline state and retain the draft while connectivity returns.
- Keep the existing “reply differently” action for assistant responses; distinguish it from retrying a failed request.

**Likely touchpoints:** `ChatMessage`, `ConversationManager`, `OutboundSendQueue`, `DostStore`, `ChatScreen`, and the AI request/error handling.

**Done when**

- A failed user send can be retried once or canceled with its text/image intact.
- A retry has one stable message ID and does not duplicate the transcript, archive entry, notification, or relationship update.
- A process restart does not silently lose a queued or failed message.

---

## P1 — Make each friend feel like a specific person

### 1.1 Build a proper Friend Profile page

**User outcome:** before and after adding a friend, the user understands who this friend is and can adjust how they interact.

**Developer work**

1. Add a dedicated profile/detail route accessible from the friend picker, Home row, and chat header.
2. Organize information into clear sections:
   - **Who they are:** name, avatar, short bio, personality, interests, conversation style.
   - **How you met:** relationship premise and opening greeting.
   - **Their routine:** busy reason, days, hours, and time zone.
   - **Your relationship:** current friendship stage and shared moments.
   - **What they remember:** shortcut into that friend’s Memory Center.
3. Allow editing user-owned custom friend fields after creation. For catalog friends, offer local personalization such as nickname, photo, preferred tone, and schedule without silently changing the canonical catalog profile.
4. Show a short sample message or “meet them” preview in the picker before Add. Use the character’s actual voice rules, not a generic sample.
5. Add a consistent profile completeness/validation rule so prompts do not receive contradictory or blank profile fragments.

**Data/technical approach**

- Keep stable server `catalogId` separate from the local friend ID.
- Treat the local copy as the user’s friend instance; profile overrides belong to that instance.
- Version profile JSON additively so old local friends still load with defaults.
- Keep character data concise and behavior-focused. Do not add a field unless it changes chat, schedule, or relationship behavior.

**Likely touchpoints:** `Friend`, friend serialization in `DostStore`, catalog JSON/API in `app/api/friends.php`, `SystemFriendPicker`, Home rows, chat profile entry point, and `OpenRouterClient` prompt assembly.

**Done when**

- A user can preview a friend, add them, and later change their local schedule or presentation.
- The profile shown in UI is the profile used for prompting.
- Existing friends and imported backups still load without data loss.
- Two catalog friends have distinguishable sample responses in a reviewed test set.

### 1.2 Improve first conversation and character voice

**Developer work**

- Replace generic first-chat prompts with three optional, friend-specific conversation starters, derived from that friend’s interests and relationship premise.
- Let the user dismiss starters; do not make starter selection required.
- Make the initial greeting, later replies, scheduled greetings, and busy-state replies share the same voice specification.
- Add character voice guidance to profile data: sentence length, warmth, humor level, vocabulary, and a small set of do/don’t examples.
- Add a “This doesn’t sound like them” feedback action that records a character-quality signal without sending private chat text to analytics.
- Add prompt regression fixtures for each shipped catalog character and check that identity, interests, and tone remain consistent across ordinary, scheduled, and follow-up turns.

**Done when**

- The first response references the selected character naturally without asking for sensitive information by default.
- Scheduled messages sound like the same character as normal replies.
- Voice differences are observable in evaluation examples, not just profile descriptions.

---

## P1 — Make memory a visible, useful feature

### 2.1 Create a per-friend Memory Center

**User outcome:** users can answer “What does this friend remember about me?” and fix mistakes without searching through Settings.

**Developer work**

- Add a memory entry point in chat and the Friend Profile page.
- Show facts grouped into a few user-readable categories (preferences, people, plans, routines, other), with last updated/source context where available.
- Provide **Edit**, **Forget**, and **Remember this** actions.
- Add an in-chat **Remember this** action on a user message so a user can explicitly save an important plan or preference for that friend.
- Add **Don’t remember this** on an assistant claim or saved fact; remove the fact and prevent it from being reintroduced from the same source.
- Keep memories scoped per friend and clearly label that scope.
- Make the current Knowledge screen a global index of those per-friend memories rather than the only place to manage them.

### 2.2 Upgrade the memory record and migration

**Developer work**

- Evolve `UserMemory(category, fact, replaces)` to include a stable ID, source message/session ID when available, created/updated timestamp, and a source type (model-extracted or user-saved).
- Add a versioned migration that reads current preference-backed facts and creates IDs/timestamps while preserving their text and category.
- Ensure update/delete operations also refresh structured profiles and do not leave deleted facts in retrieval results.
- Keep memory summaries and session summaries consistent after a correction or deletion. If an old summary repeats a deleted fact, either redact/regenerate it or filter it from model context.
- Add bounded deduplication and conflict handling: an explicit correction replaces the old fact; a newer statement does not automatically override an older fact unless the intent is clear.
- Preserve uncertainty and negation. Store “may move next year” as uncertain, not as “is moving.”

### 2.3 Improve retrieval and evaluation

- Keep retrieval limited to relevant facts; do not increase the current six-fact budget until an evaluation set demonstrates a benefit.
- Build a small gold set with user questions and expected memories. Score whether relevant facts are retrieved, irrelevant facts are excluded, and corrected/deleted facts stay excluded.
- Evaluate the combined effect of raw memories, structured profile, and session summaries to avoid duplicate prompt context.
- Add a “Why did you remember that?” explanation using source metadata; do not expose hidden chain-of-thought or private model reasoning.
- Test facts about other people separately from facts about the user.

**Likely touchpoints:** `UserMemory` and retrieval functions in `AppModels.kt`, memory persistence in `DostStore`, prompt assembly in `OpenRouterClient`, the chat message menu in `ChatScreen`, and `KnowledgeScreen`.

**Done when**

- A user can save, edit, or forget a fact in at most a few taps.
- A deleted or corrected fact is absent from the next model prompt and does not reappear from a summary.
- Existing memory data migrates automatically and remains editable.
- Automated retrieval tests measure precision and omission on reviewed examples.

---

## P1 — Give friends more meaningful lives and proactive behavior

### 3.1 Expand the schedule model

**User outcome:** “They’re busy” means something coherent and configurable, rather than a single fixed block every day.

**Developer work**

- Add days-of-week support and separate weekday/weekend routines.
- Add optional routine blocks (work/school, commute, sleep, free time) with a reason label and friend time zone.
- Add a simple schedule editor to Friend Profile. Keep defaults easy to accept; advanced routine controls can be collapsed.
- Allow the user to choose how busy time affects behavior:
  - delay responses until availability;
  - reply immediately but mention being busy;
  - use a shorter/longer delay.
- Show the expected reply time in the chat and let the user override the delay for a specific message.
- Add holidays/time-zone/DST handling tests and store a stable time zone ID, not only a fixed UTC offset.

**Done when**

- Schedule calculations handle cross-midnight blocks, day boundaries, DST changes, and updated time zones.
- The chat explains delayed replies and the user can see or change the schedule.
- A schedule setting changes both the UI status and actual scheduled worker behavior.

### 3.2 Build a proactive-message manager (keep initiation on)

**Decision:** proactive messages remain a core Dost feature. Add controls and better scheduling; do not replace character-initiated greetings with only user-requested reminders.

**Developer work**

- Replace implicit timer-only decisions with a typed `ProactiveMessage`/trigger model:
  - morning greeting;
  - follow-up to an earlier conversation;
  - user-requested reminder/check-in;
  - friend-specific life event or plan follow-up.
- Store the trigger reason, friend ID, session ID, due time, and idempotency key with the scheduled item.
- Centralize eligibility checks before scheduling and again immediately before sending: friend still exists, session is current, user has not paused the friend, quiet hours have ended, and no newer user message makes the follow-up stale.
- Add Settings controls for proactive-message timing and cadence, plus per-friend pause/snooze. Keep the feature discoverable and enabled according to a sensible default; users who want less activity can tune it without losing the underlying feature.
- Add actions to notifications and the message itself: **Open chat**, **Snooze**, and **Pause this friend’s check-ins**.
- Explain the difference between “busy, reply later” and “friend started a new conversation.”
- Maintain a global scheduler so three friends do not all message at the same instant.
- Keep user-requested reminders precise relative to the request; treat routine greetings as flexible background work, since Android may delay them.
- Audit the current one-minute default check-in. Keep quick follow-up when explicitly requested, but make automatic follow-up timing a deliberate friend/app setting.
- Persist completion state so WorkManager retries cannot create duplicate messages or notifications.

**Likely touchpoints:** `ConversationWorkScheduler`, `ConversationManager`, `DostStore`, `ReplyNotifier`, `ChatScreen`, `SettingsScreen`, and notification actions/manifest configuration.

**Done when**

- A friend can initiate a greeting while the app is closed and the event is visible in that friend’s conversation.
- Every proactive message has a reason and a manageable setting.
- Editing a schedule, sending a newer message, pausing a friend, or deleting a friend safely updates/cancels pending work.
- Worker retry, app restart, and duplicate enqueue tests prove a message appears at most once.

### 3.3 Add life events and callbacks

**Feature:** let a friend remember and return to a future event, not only a stable biography fact.

**Developer work**

- Add a structured “plan/event” item: description, friend owner, date/time if known, confidence, and follow-up preference.
- Support explicit user phrases such as “ask me how the interview went tomorrow” as a reminder request. Confirm the parsed date/time in UI when ambiguous.
- Add an upcoming/overdue section in Friend Profile and a lightweight chat recap after the event.
- Keep the user in control: edit, dismiss, or mark an event complete.
- Do not infer a deadline or promise a follow-up when the user has not requested one.

**Done when**

- A user can ask for a later check-in, see when it is scheduled, change it, and cancel it.
- Ambiguous dates prompt a confirmation rather than silently choosing the wrong day.
- The friend can refer to the event naturally, and does not repeatedly ask after it is marked complete.

---

## P2 — Make relationship growth tangible

### 4.1 Add relationship stages and shared moments

**User outcome:** the user can feel that a friend has become more familiar without needing to interpret an unexplained number.

**Developer work**

- Keep friendship and age-gated romantic progression. Add a human-readable stage label derived from the existing friendship state, with clear thresholds and migration-safe defaults.
- Add a **Shared moments** timeline per friend: meaningful user-approved memories, first conversations, completed plans, and relationship milestones.
- Let the user pin or remove a moment. Do not silently convert every chat turn into a milestone.
- Give a short factual explanation when a stage changes, e.g. “You’ve talked about music a few times,” not “the model thinks you are close.”
- Keep the existing score in the Friend Profile as optional detail; do not make the number the main chat focus.
- Add anti-farming checks: empty messages, retries, generated follow-ups, and repeated identical turns must not create artificial progress.
- Keep boundary-setting, corrections, and declining romantic conversation from causing unfair relationship penalties.
- Preserve all existing age gates and ensure no stage unlock can bypass the server-side safety layer.

**Likely touchpoints:** relationship data in `Friend`, score functions and prompts in `AppModels.kt`, update application in `ConversationManager`, chat header/meters, Friend Profile, and safety tests.

**Done when**

- Every stage maps deterministically to stored state and survives restart/import.
- Relationship changes are applied once per real conversation outcome.
- A user can review and remove shared moments without erasing the conversation itself.
- Age/romance tests continue to pass at every stage.

### 4.2 Add friendship activities

Start with simple, chat-native activities that strengthen persona and memory:

- **Conversation starters** based on the friend’s interests and prior topics.
- **Tell me about your day** prompts that vary by persona and local time.
- **Shared topic threads** such as a book, music, hobby, or ongoing goal.
- **Small games** (two truths and a lie, trivia, word games) that do not require new backend infrastructure.
- **Story/scene mode** clearly labeled as fictional play, separate from ordinary autobiographical memory.

For each activity, add a clear start/end action and decide whether its content should become durable memory. Default role-play content should not automatically become personal fact memory.

---

## P2 — Improve everyday usability around the core features

### 5.1 Make Home a useful friend hub

- Show last message, unread count, friendship stage, and current availability for each friend.
- Add filters for **All**, **Unread**, and **Available**; preserve the current simple chat list as the default.
- Add pin/favorite ordering and a quick way to start a new conversation with an existing friend.
- Show a small “up next” indicator for an opted-in greeting/reminder, without turning Home into a dashboard.
- Keep add-friend and friend-picker entry points obvious.
- Add search over friend names and conversation snippets if the current message search is not enough for users with many friends.

**Done when:** Home answers “who can I talk to, what happened last, and is anything waiting?” at a glance.

### 5.2 Make chat easier to resume

- Add a **Since you last talked** recap from the existing session summary, with an action to view or dismiss it.
- Add a conversation timeline entry for proactive messages and completed reminders.
- Add draft preservation, failed-send retry, attachment state, and an explicit queued status.
- Add a chat details sheet with persona, memory, schedule, relationship, and notification controls.
- Add a lightweight message action menu for pinning a useful moment, saving a reminder, copying, and deleting a local message where supported.
- Keep the composer one-handed and accessible; do not make memory or schedule controls crowd the typing area.

### 5.3 Improve friend creation and editing

- Use a guided custom-friend form with a minimal required path and optional advanced persona/schedule fields.
- Add edit and duplicate actions for custom profiles.
- Add an avatar/photo picker, profile preview, opening greeting preview, and validation before saving.
- Keep existing system friends editable only through local overrides; offer **Reset to catalog profile**.
- Clearly show when profile generation is pending/failed and provide retry without losing form data.

**Done when:** users can create, preview, modify, and recover a custom friend without restarting onboarding.

---

## P3 — Operate the character catalog as a quality system

- Add a versioned schema validator for each `system_friends/*.json` profile.
- Validate required identity fields, age, time zone, schedule range, relationship premise, distinct voice examples, and prohibited contradictions before catalog publication.
- Add a content review checklist for cultural specificity, stereotype risk, respectful boundaries, and age-appropriate characterization.
- Add a catalog health report: missing photos, duplicate voice profiles, blank greetings, schedule defaults, and outdated schema versions.
- Add automated prompt smoke tests per character using a small fixed set of conversations.
- Keep the catalog profile API and app parser backward-compatible during rollouts.

**Done when:** a catalog update cannot silently break profile parsing or ship a duplicate/generic character without a review signal.

---

## Data/API changes to plan together

| Data area | Change | Migration requirement |
|---|---|---|
| Friend profile | Add profile version and local override fields; add days-of-week/routine data. | Existing `Friend` JSON loads with today’s defaults and retains local IDs. |
| Proactive messages | Store typed trigger, due time, source session, status, and idempotency key. | Existing WorkManager names are canceled or migrated safely; no duplicate legacy sends. |
| Memory | Add ID, timestamps, source type, and source message reference. | Existing `category` and `fact` values remain intact and editable. |
| Events/reminders | Add structured due date, completion state, and user-confirmed intent. | Existing follow-up schedule jobs continue or are explicitly migrated. |
| Relationship | Add stage/milestone records while keeping current score fields. | Old scores map deterministically to a stage; no reset on upgrade. |

Keep all backend request changes additive. Validate client/server behavior together, especially when a new app version talks to an older deployed API.

## Delivery order and release gates

### Release 1 — Dependable conversations and memory entry points

1. Fix the failing test baseline and add core schedule/relationship tests.
2. Add outgoing send states and retry.
3. Add Friend Profile route and in-chat memory entry point.
4. Add explicit “Remember this” and “Forget this” actions.
5. Add telemetry events without collecting message content.

**Gate:** existing local users upgrade without data loss; failed sends are recoverable; memory edit/delete affects future prompts.

### Release 2 — Friend routines and proactive manager

1. Expand schedule editing and display.
2. Add typed proactive triggers and persisted delivery state.
3. Add global/per-friend controls, pause/snooze, and explanation labels.
4. Keep daily greetings and friend initiation available; refine timing and prevent duplicates.
5. Add user-requested event reminders and completion state.

**Gate:** schedule, pause, edit, delete, restart, and retry cases are covered by tests; proactive delivery appears once and can be managed.

### Release 3 — Relationship and conversation features

1. Add relationship stage labels and shared-moment timeline.
2. Add conversation recap and friend-specific starters.
3. Add initial activities (one simple game and one ongoing shared topic).
4. Add catalog voice/consistency validation.

**Gate:** progression is consistent, activity content does not pollute memory, and characters remain distinct in prompt tests.

### Release 4 — Polish and scale

1. Improve Home filters, favorites, and friend status.
2. Expand catalog with quality gates.
3. Optimize large memory histories and prompt budgets using evaluation results.
4. Complete accessibility, localization, and real-device background scheduling validation.

**Gate:** usability tests show users can discover the core capabilities without instruction; no regression in existing profile, backup, age, or data-deletion flows.

## Developer test plan

### Unit tests

- Persona serialization, local override/reset, and catalog migration.
- Memory extraction merge, correction, deduplication, and deletion from all prompt contexts.
- Memory retrieval ranking and prompt-budget limits.
- Busy-time calculations across midnight, time zones, weekends, and DST.
- Proactive eligibility, cadence, stale-session suppression, and idempotency.
- Relationship thresholds, regeneration, repeated messages, and age gating.

### Worker/integration tests

- Reboot and process recreation with queued work.
- Offline enqueue, retry, and duplicate suppression.
- User sends a new message while a follow-up is pending.
- Friend muted/deleted or schedule edited while work is pending.
- Notification permission granted, denied, and revoked.
- Import/export with new schema fields and older backups.

### Manual usability checks

- Add a catalog friend and a custom friend; compare their voice and profile.
- Start a chat, save a memory, correct it, then verify the next chat uses the correction.
- Close the app and receive a character-initiated message; open it and inspect its reason.
- Change a friend’s busy hours and confirm both status and delayed reply update.
- Use a requested reminder and cancel it.
- Review relationship growth and remove a shared moment.
- Test light/dark mode, Bangla/Hindi/English, large text, narrow phone, landscape, offline, and battery-restricted Android.

## Product success measures

Instrument only the events needed to improve the app; do not log chat text for product analytics.

- **Character:** friend preview → add conversion; first chat completion; character-distinction feedback.
- **Memory:** relevant-memory evaluation accuracy; user corrections; successful forget; fact reappearance after deletion (target zero).
- **Initiative:** proactive messages delivered once; opens/replies; snoozes/mutes; user feedback on relevance and timing.
- **Schedule:** delayed replies delivered successfully; timing mismatch reports; schedule changes followed by successful delivery.
- **Relationship:** stage progression, user understanding of stages, and whether milestones feel accurate.
- **Usability:** failed-send recovery, time to reach memory/profile controls, crash-free sessions, and onboarding completion.

Do not treat total messages, notification opens, or time in app as standalone success. Pair activity metrics with correction, mute, retry, and user-reported quality signals.

## First engineering sprint

Start with this concrete, shippable slice:

1. Repair the known `generatedProfileReplacesDefaultsBeforeChatCanStart` test failure.
2. Add tests for morning greeting timing, busy reply timing, and stale follow-up cancellation.
3. Add a **Friend details** route accessible from chat and Home, initially showing persona, schedule, relationship, and a memory shortcut.
4. Add **Remember this** to a user-message action menu and **Forget this** to an individual memory.
5. Add persistent failed-send retry.
6. Add migration tests for existing friends/memories before changing stored schemas.

This sprint strengthens persona, memory, routine, relationship, and conversation reliability together, while leaving all core selling points intact.
