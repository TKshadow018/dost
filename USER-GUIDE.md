# Dost User Guide

Dost is an Android app for chatting with fictional AI friends and, in configured builds, with other eligible adults. AI-friend conversations and real-person conversations use separate services and data.

## Contents

- [Getting started](#getting-started)
- [Home and friends](#home-and-friends)
- [Chatting](#chatting)
- [People chats](#people-chats)
- [Friend profiles and relationship progress](#friend-profiles-and-relationship-progress)
- [Memories and Knowledge](#memories-and-knowledge)
- [Routines, check-ins, and reminders](#routines-check-ins-and-reminders)
- [Settings and your data](#settings-and-your-data)
- [Safety and privacy](#safety-and-privacy)
- [Troubleshooting](#troubleshooting)

## Getting started

1. Sign in or create an account. Depending on the installed build, you can use email and password or Google sign-in. Email verification and password reset are available for email accounts.
2. Registration asks for your name, age, and gender. Dost is for adults 18 and older. App access requires an adult Age Signals result or, only when age sharing is unavailable, adult self-attestation; minor and verification-required results are blocked.
3. Create a first custom friend, or skip that step and add friends later from Home.
4. Open a friend and start chatting. Some friends have a greeting ready when you first open a conversation.

Firebase sign-in must be configured for account features to work in a particular build. If sign-in is unavailable, contact the app provider.

## Home and friends

Chats is your conversation list. AI-friend and People conversations can appear in the same inbox, but each opens its separate chat experience and remains on its separate service.

### Find and organize friends

- Use the conversation-type filters to show AI friends or People, and the archive filter to find archived conversations.
- Search the inbox by a conversation name or recent preview. In an AI chat, use **Search conversation** to search its saved history. In a People chat, use **Search messages**; search covers loaded history, and **Load older messages** expands it to earlier pages.
- Open a friend’s options menu to add or remove them from favorites. Favorites appear first.
- Select a conversation to open it. Open an AI friend’s profile from the row menu or chat header.

### Add friends

- **Custom friend:** Enter a name, age, gender, personality, first greeting, and optionally a photo. You can also set their busy hours and time zone.
- **Meet a friend:** Browse the character catalog by swiping right to add a character or left to pass. Catalog friends have their own persona, interests, greeting, relationship premise, and routine.
- The app supports up to 3 custom friends and 7 catalog friends at a time.
- Dost's friends are fictional characters. Each has their own personality; memories are friend-specific and can be reviewed, corrected, or deleted.
- If a generated profile is still being prepared or fails, open the chat to retry or continue with the available basic profile.

Deleting an AI friend removes that friend’s local profile and related local app data. It does not delete the server conversation archive; see [Settings and your data](#settings-and-your-data).

## Chatting

### Messages and chat tools

- Send text messages or attach a photo. Chat photos are sent with the message to provide the AI response.
- Use the chat options menu to search the conversation, export the chat, open the friend profile, add a reminder, add a shared topic, play a quick game, review what the friend remembers, view session summaries, or minimize chat.
- Tap the friend’s name or photo in the header to open their profile.
- If an answer fails or you want a different response, use the assistant message action to retry or request a different reply.
- A friend may wait until their configured busy routine ends before replying. The chat indicates when a scheduled reply is waiting.

### Conversation starters and recaps

- On a new conversation, optional starter prompts are based on the friend’s interests. Tap one to place it in the composer; you can edit it before sending or hide the suggestions.
- After a session summary is available, the chat can show a **Since you last talked** recap. View the recap or dismiss it.
- Use **Session summaries** in the chat menu to review available summaries from earlier conversations.

### Shared topics and a quick game

- Save an ongoing shared topic from the chat menu. It appears as a topic chip in the conversation; tap it to place a prompt in the composer and pick up the topic again.
- Save a **shared ritual** from the chat menu to keep a repeatable activity with that friend. Tap its chip to prepare a prompt you can edit. Rituals never send messages or start on their own.
- **Play a quick game** opens a small number-guessing game. It runs locally and is not added to the conversation or saved as a personal memory.

### Save a shared moment

Press and hold a message and choose **Save as a shared moment**. The saved item appears in that friend’s profile, separately from personal memories. You can pin or remove it there. Relationship milestones and completed plans can also appear in the shared-moments timeline.

## People chats

People is available only in builds with its supporting services configured and for eligible adults. People profiles are text-only; profile photos and image messages are not supported.

- Publish a profile only when you choose. Discovery is limited to eligible adults; it does not match people by exact age band.
- Passing a profile hides it for up to 90 days. Use **Start over** when the deck is empty to clear your passed-profile history sooner.
- A profile’s contact preference determines what happens when you select it: a direct chat may open, a contact or match request may be sent, or new contact may be unavailable. With **Mutual match first**, a chat opens only after the recipient accepts the request.
- People chat search checks messages already loaded on the device. Use **Load older messages** to include earlier history; each page is loaded on demand.
- Optional People message notifications are off by default. When enabled, supported builds send generic alerts without message text or sender names. Delivery also requires Android notification permission and a configured notification service.
- Audio and video calls are separate from text chat. Calls require an internet connection and configured call services; microphone access is needed for audio and camera access for video.
- People messages currently have no in-app delete control. Do not treat **Hide profile** or **Delete profile** as deleting existing chats or requests.
- Use **Block** to stop contact through Dost and **Report** to send a concern for manual review. Reports are not an emergency service and do not automatically suspend an account.

## Friend profiles and relationship progress

A friend’s profile brings together:

- Their age, gender, personality, interests, conversation style, first greeting, and how you met.
- Their weekly routine, busy behavior, and time zone. Use **Edit schedule** to change the routine and reply behavior.
- Planned events, shared moments, proactive-message controls, and a shortcut to what the friend remembers.
- Friendship stage and score. Relationship progress is limited to conversation outcomes; repeated identical messages and retrying an answer do not repeatedly award progress.

The romantic meter and romantic prompts are available only when both you and the friend are at least 18. The romantic meter becomes available as friendship grows. Dost’s safety checks still apply.

## Memories and Knowledge

Friends may learn useful details from conversation. Memories are per friend, so one friend’s memory list is separate from another’s.

- In chat, press and hold one of your messages and choose **Remember this** to save a detail specifically for that friend. Review or shorten it before saving.
- Choose **What this friend remembers** in the chat menu to review and forget individual items.
- Open **Knowledge about me** from Settings or a friend profile to review facts across friends. You can correct or delete a fact; a correction updates the saved detail used in later conversations.
- Saving a shared moment does not add it to personal memory. The quick game also does not create chat or memory entries.

## Routines, check-ins, and reminders

### Friend-initiated messages

Friend-initiated messages are off by default. When you enable them, friends can send routine greetings and automatic conversation check-ins. Automatic messages respect the configured routine, quiet hours, spacing, and pause settings. Android notification permission is requested only after you enable friend messages; Android battery restrictions may still delay background delivery.

- In **Settings → Friend messages**, turn routine greetings and automatic check-ins on or off, choose quiet hours, set the minimum interval between proactive messages, and adjust automatic follow-up timing.
- In a friend’s profile, snooze check-ins for 24 hours, pause them until you resume, or resume a paused friend.
- Proactive-message notifications and message actions also provide snooze and pause controls.
- Turning off routine friend-initiated messages does not turn off reminders you created yourself.

### Planned events

Add a planned event from the chat menu or the friend’s profile. Choose what the friend should check in about, a future date and time, and whether to send a chat check-in. Times are entered using your selected time zone. You can edit an upcoming event, mark it done, or dismiss it. Completing a plan can add a shared moment.

Some clear English requests, such as “Remind me to ask about my interview tomorrow at 9:00,” may open a reminder for confirmation. Review the date and time before saving; you can also create the reminder directly from the editor.

## Settings and your data

Open Settings from Home to manage:

- **Language and time zone:** Choose English, Bangla, or Hindi and set your time zone. Your time zone is used for reminders and schedule displays.
- **AI provider and model:** Choose an available model and optionally provide your own OpenRouter API key. Your own key may incur charges from OpenRouter. Never put API keys or passwords in chat.
- **Friend messages:** Configure routine greetings, follow-ups, quiet hours, and message spacing.
- **Knowledge about me:** Review memory facts across friends.
- **Export encrypted backup / Import backup:** Export or restore your profile, AI friends, routines, reminders, memories, summaries, and AI chat history. Choose a passphrase of at least 12 characters and keep it separate; it cannot be recovered. Import replaces the current local data. Older unencrypted JSON exports remain importable. Backups do not include your API key or installation ID.
- **Delete server archive:** Request deletion of the indexed server conversations and attached images for this installation. Older records that were not indexed by installation ID may not be included.
- **Sign out:** Signing out does not delete local profiles or chats.
- **Manage friends:** Remove a friend from the device.

You can also export an individual AI chat from that conversation’s options menu. Removing an AI friend or signing out does not automatically delete that friend’s server archive. Deleting the server archive is a separate Settings action. People conversations are stored in Firebase and are not included in the AI backup or archive-deletion action.

## Safety and privacy

- Dost’s friends are fictional AI characters. Replies can be inaccurate; use your judgment and do not rely on them for professional advice.
- Do not share passwords, API keys, authentication codes, financial credentials, or other highly sensitive information in a conversation.
- Chat messages and attached chat photos are sent to Dost’s API and the selected AI provider to generate responses. Conversation data may also be archived on Dost’s server. AI providers have their own processing and retention policies.
- Friend-avatar photos selected from your device stay on your device. Photos attached to chat messages are sent with the message.
- Local AI profiles, memories, and chats are not automatically synchronized between devices. Use the explicit encrypted backup to move this data.
- Optional **Share anonymous event counts** is available in Settings. When enabled, Dost sends allowlisted event names, not chat text or profile details; daily totals are retained for 90 days. The network host may see your IP address as part of a request. These totals cannot measure individual or cohort retention.
- Use **Delete server archive** to request deletion of indexed server records. This does not delete copies held by OpenRouter or model providers.
- On Android 13 and later, allow notifications if you want friend replies and check-ins to appear outside the app.
- The floating chat panel requires Android’s “display over other apps” permission. Use **Minimize chat** to start it.
- Romantic features are disabled if you or the friend are under 18. Sexual content is blocked by the chat safety layer.

For the detailed data-handling policy, see [the Dost privacy policy](./app/api/privacy-policy.txt).

## Troubleshooting

- **A friend has not started a conversation:** Check the global and per-friend proactive settings, quiet hours, the friend’s routine, Android notification permission, and battery restrictions.
- **A reply is waiting:** The friend may be in a configured busy period. The chat shows when a reply is expected.
- **A reminder seems wrong:** Reopen it and confirm its date and time in your selected time zone.
- **A profile is still being created:** Open the chat to retry profile generation or continue with the basic profile.
- **AI replies are unavailable:** Check your internet connection, the selected model, and whether a valid server or personal OpenRouter key is configured.
- **You are moving to a new phone:** Export an encrypted backup on the old phone and import it on the new one. Keep the passphrase separate from the backup file. Older unencrypted JSON exports are not encrypted.
