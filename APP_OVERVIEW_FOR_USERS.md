# Dost: App Overview

*A plain-language overview of the app's current features. Updated November 2026.*

## What is Dost?

Dost is an Android app for ongoing conversations with fictional AI friends. Each friend has a distinct personality, and you can choose details such as their interests and conversation style. Dost also includes optional text chats with other people in eligible builds.

AI-friend conversations and real-person conversations are separate. A real person is never an AI character, and real-person messages are not sent to Dost's AI chat service.

## What you can do

### Make a circle of AI friends

- Create a custom friend or choose one from the character catalog.
- Give each friend their own personality, interests, greeting, and routine.
- See a friend's configured busy schedule. This is a schedule, not live online status.
- Open a friend's profile to review their details and relationship progress.

### Have conversations that can continue over time

- Chat with each friend separately.
- Choose whether to save a useful detail as a memory for that friend. Review, correct, or remove saved memories.
- Review conversation recaps and summaries, and return to shared topics, rituals, or moments.
- Create reminders and planned check-ins.
- Friend-initiated greetings and check-ins are off by default. You control whether to enable them and can pause them.

AI replies can be inaccurate. AI friends are fictional characters, not real people or professional advisers.

### Choose your languages

The app's interface language and the language AI friends use are separate settings. English, Bangla, and Hindi are supported for these choices. You can also set your time zone for schedules and reminders.

### Play a quick game

The Games page has nine games that run in the app: Guess the Number, Rock Paper Scissors, Tic-Tac-Toe, Memory Match, Word Scramble, Quick Math, Dice Duel, Coin Toss, and Color Match. Epic Fury Wrestling opens as an online game in a web view.

### Meet people, where available

In builds with the People service configured, eligible adults can publish an optional profile, browse other published profiles, and start a one-to-one text chat. Chats update live while users are connected, and the inbox tracks unread messages. Blocking and reporting are available.

Selecting a profile currently starts a direct chat; a mutual match or accepted message request is not required first. People chat is text-only: voice and video calls, voice notes, and image messages are not included.

## Privacy and account notes

- AI messages are sent to Dost's API and the selected AI provider to produce a reply. See the [privacy policy](./app/api/privacy-policy.txt) for details.
- Real-person chats are stored in Firebase and can be read by both participants. They are not end-to-end encrypted, and the app does not currently provide in-app deletion of those messages.
- The People service requires adult eligibility and may not be available in every build until its supporting services are configured.
- An optional passphrase-encrypted backup can move local AI-chat data to another device. Keep the passphrase safe; it cannot be recovered.
- Some features require sign-in and an internet connection. Human-chat push notifications and sender-facing read receipts are not currently available.

For details about data handling, read the [Dost privacy policy](./app/api/privacy-policy.txt). Feature availability can vary by app build and service configuration.
