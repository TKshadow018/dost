# Dost UI/UX Improvement Report

**Date:** 2026-11-13  
**Scope:** Android app screens and shared Compose theme

## Executive summary

Dost has recently moved toward a clean, Messenger-inspired visual direction: blue primary actions, neutral surfaces, a compact chat header, rounded message bubbles, and a pill-shaped composer. The shared Material 3 theme and 48dp chat controls are a useful foundation.

The highest-value remaining opportunities are predictable Android back navigation, adaptive layouts for the swipe-friends screen, consistent dark-mode surfaces on authentication, and stronger list/accessibility semantics. The Settings dialog and mixed-language labels are also candidates for a more cohesive experience.

## Skill guidance applied

The project-local [UI UX Pro Max prompt](.github/prompts/ui-ux-pro-max.prompt.md) and its searchable data were used for this review.

- The `social messaging mobile chat` design-system search returned **Minimalism & Swiss Style** and a Messenger-blue/green palette (`#2563EB` / `#047857`). Its “Product Demo + Features” page pattern is not relevant to an in-app screen, so it was not applied.
- The product-domain search found no exact “social messaging” profile. Recommendations below use the matching style and native-app guidance rather than claiming a product-specific result.
- Compose searches highlighted stable keys for lazy lists and constraint-aware layouts. The skill’s native-app checklist also calls for 48dp Android touch targets, theme-aware light/dark colors, safe-area checks, accessible icon names, and testing at small sizes and large text scales.

## Prioritized opportunities

| Priority | Area | Current observation | Improvement option |
|---|---|---|---|
| **P1** | Back navigation | The picker handles Android back, while Chat, Knowledge, and registration primarily expose an in-screen Back action. The app uses screen state rather than a navigation back stack. | Define consistent system-back behavior at the app level. Return from Chat/Knowledge to the prior screen, preserve relevant scroll/draft state, and avoid exiting unexpectedly. See [MainActivity.kt](app/src/main/java/com/snigtus/bondhu/MainActivity.kt), [ChatScreen.kt](app/src/main/java/com/snigtus/bondhu/ChatScreen.kt), and [KnowledgeScreen.kt](app/src/main/java/com/snigtus/bondhu/KnowledgeScreen.kt). |
| **P1** | Swipe-friends layout | The picker uses a non-scrolling page column, fixed 360dp loading/empty states, and a card height clamped to 300–390dp. Header, card, and action buttons may compete for space in landscape, on short devices, or with larger text. | Use available-height constraints, allow appropriate scrolling, and provide a landscape/short-height layout. Keep Pass, Info, and Add operable without clipping. See [SystemFriendPicker.kt](app/src/main/java/com/snigtus/bondhu/SystemFriendPicker.kt). |
| **P1** | Authentication theme parity | The authentication screen uses a fixed white card and its own light canvas colors, so it can remain visually light while the rest of the app follows dark mode. | Map the auth card and text to semantic theme surfaces, or explicitly design a complete auth-specific dark palette. Verify all auth modes in both themes. See [AuthScreens.kt](app/src/main/java/com/snigtus/bondhu/AuthScreens.kt) and [Theme.kt](app/src/main/java/com/snigtus/bondhu/ui/theme/Theme.kt). |
| **P2** | Settings information architecture | One scrollable dialog contains account actions, language, time zone, AI settings, data import/export, archive deletion, Knowledge, and friend deletion. | Consider a dedicated Settings screen with grouped sections and separate destructive actions. Keep common tasks easy to scan and reduce the amount of scrolling in a modal. See [HomeScreens.kt](app/src/main/java/com/snigtus/bondhu/HomeScreens.kt). |
| **P2** | Lazy-list identity | Friend, message, and profile-fact collections use `items(...)` without stable keys. | Supply stable item keys (friend ID, message ID, and a suitable fact identity) so list state follows content as items change. This is especially useful for incoming chat messages and friend-list updates. See [HomeScreens.kt](app/src/main/java/com/snigtus/bondhu/HomeScreens.kt), [ChatScreen.kt](app/src/main/java/com/snigtus/bondhu/ChatScreen.kt), and [KnowledgeScreen.kt](app/src/main/java/com/snigtus/bondhu/KnowledgeScreen.kt). |
| **P2** | Chat input semantics and localization | The composer is a custom `BasicTextField` with placeholder text; some chat feedback and profile details remain English even when Bangla or Hindi is selected. | Add an explicit localized label/hint to the composer and localize the busy-reply status, image errors, image actions, timestamps, and profile labels. Retain the current 48dp touch targets and visible send-disabled state. See [ChatScreen.kt](app/src/main/java/com/snigtus/bondhu/ChatScreen.kt). |
| **P2** | Friend-row actions | Friend deletion is exposed on a long press in the chat list; deletion is also available in Settings, but the row action is not discoverable from its appearance. | Add a visible overflow action for deletion, with a confirmation step, while keeping the row tap dedicated to opening chat. See [HomeScreens.kt](app/src/main/java/com/snigtus/bondhu/HomeScreens.kt). |
| **P3** | Empty-state recovery | The no-friends state already includes a useful add-friend action. Knowledge’s empty state is informational only, and the picker’s “More friends coming soon” state offers no retry or next step. | Keep the strong no-friends CTA. For other empty states, explain what causes the state and offer an appropriate next action, such as opening a chat or retrying catalog loading when applicable. See [KnowledgeScreen.kt](app/src/main/java/com/snigtus/bondhu/KnowledgeScreen.kt) and [SystemFriendPicker.kt](app/src/main/java/com/snigtus/bondhu/SystemFriendPicker.kt). |
| **P3** | Icon consistency | Several controls use text glyphs (`‹`, `⋮`, `+`, `✦`) or a single-letter Google mark instead of one consistent icon family. | Standardize navigation/action icons using platform vector assets, keep touch targets at least 48dp, and provide localized accessible names for icon-only controls. Preserve official branding for Google sign-in. See [ChatScreen.kt](app/src/main/java/com/snigtus/bondhu/ChatScreen.kt), [HomeScreens.kt](app/src/main/java/com/snigtus/bondhu/HomeScreens.kt), [SystemFriendPicker.kt](app/src/main/java/com/snigtus/bondhu/SystemFriendPicker.kt), and [AuthScreens.kt](app/src/main/java/com/snigtus/bondhu/AuthScreens.kt). |

## Strengths to preserve

- The chat list and chat screen prioritize conversations instead of dashboard-style cards.
- The no-friends state gives a clear first action; profile creation errors expose retry/continue paths.
- Chat actions, image attachment, search, export, and friend details remain available without crowding the composer.
- The shared theme now provides coordinated light/dark palettes. The primary blue/white and green/white text pairs previously measured above the skill’s 4.5:1 contrast target.

## Recommended next pass

1. Fix and test system-back behavior.
2. Make the swipe-friends page resilient to landscape, short viewports, and large text.
3. Finish dark-mode parity for authentication and audit hard-coded light surfaces.
4. Add stable lazy-list keys and explicit composer semantics/localized status text.
5. Revisit Settings and row actions as a separate information-architecture pass.

## Validation notes

This report is based on source inspection and the skill’s data searches; it is not a screenshot-based visual audit. No Android emulator/device was available (`adb` was unavailable), so the small-phone, landscape, and largest-font checks remain open. Before considering the UI pass complete, verify light and dark themes, keyboard-visible composer behavior, screen-reader focus/order, and layouts at a narrow phone width and in landscape.

## Implementation status

The recommendations in this report have been applied:

- App-level system-back handling now returns to the preceding app screen; home and Knowledge list positions and per-friend chat drafts are retained while navigating.
- The friends picker scrolls on constrained screens, scales its card height, and offers retry, manage-friends, or restart actions for its empty states.
- Authentication surfaces use Material theme colors, including dark mode.
- Settings is a dedicated grouped screen; archive deletion and friend deletion require confirmation.
- Friend, chat-message, profile-fact, and Knowledge list entries use stable keys.
- Chat input semantics, image actions/errors, busy-reply feedback, timestamps, and friend-profile labels are localized.
- Navigation and common chat actions use vector icons with accessible names; the Google sign-in mark remains branded.
- Knowledge’s empty state can open a conversation with a friend.

Automated validation: the debug APK builds successfully. The Kotlin unit suite ran 94 tests and still reports the previously observed failure in `ExampleUnitTest.generatedProfileReplacesDefaultsBeforeChatCanStart` (`ExampleUnitTest.kt:82`). Device/screenshot validation remains outstanding because no emulator/device is available.
