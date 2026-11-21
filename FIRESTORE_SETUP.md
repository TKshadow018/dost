# Real-person profiles and chats

The People feature uses the Firebase project already configured for Dost Authentication. It is separate from the AI chat and does not send real-person messages to Dost's PHP API or OpenRouter.

## Build-time feature flag

People is enabled by default. To disable discovery and human chat for a build without affecting AI chats, set `DOST_PEOPLE_ENABLED=false` as an environment variable or pass `-PDOST_PEOPLE_ENABLED=false` to Gradle. Set it to `true` or omit it to enable People. Any other value fails the build. This is a build-time switch, not a remote kill switch; disabling an already-installed app for all users requires distributing an updated build.

## Enable and deploy

1. In Google Play Console, configure **Restrict Minor Access** for the entire app before publishing. Real-person matchmaking is a core app function, so the Play-distributed app is adult-only. The app calls the Play Age Signals API before initializing any app screens; if a shared range is unavailable, it may continue only after an explicit local 18+ self-attestation. A minor range or verification-required status blocks access.
2. The Age Signals API is beta and its result is not independent proof of age in every region. If a signal is not shared or the API is unavailable, the app offers a local self-attestation that the user is 18 or older; this is not independently verified. A minor range or required-but-incomplete verification still blocks access. Play Console's restriction is a required release setting, not something this repository can configure.
3. Use a Play-installed build to test Age Signals. Sideloaded builds generally return `APP_NOT_OWNED` unless the Google account is registered as a Play license tester.
4. Link the same Google Cloud project to `com.snigtus.dost` in Play Console and enable the Play Integrity API and Identity Toolkit API. Configure a service account with only `firebaseauth.users.get` and `firebaseauth.users.update`; keep its JSON key outside the web root and source control. Configure the Hostinger PHP environment variables listed in [the API setup guide](./app/api/README.md).
5. Upload `age_eligibility.php` and `age_eligibility_support.php` with the rest of the API, then test the attestation endpoint from a Play-installed build and a Play license tester. Firestore People access will fail closed until the server can issue an unexpired eligibility claim.
6. In the Firebase Console for the same project as `app/google-services.json`, create a Cloud Firestore database.
7. From the repository root, install/use the Firebase CLI, select that project, and deploy the checked-in access rules and required indexes:

   ```text
   firebase use <your-project-id>
   firebase deploy --only firestore
   ```

8. Confirm Firebase Authentication is enabled for the sign-in methods configured for Dost. Firestore access requires a signed-in Firebase account with the server-issued claim.
9. Test adult access, a minor age range, declined age sharing with self-attestation, declined sharing without self-attestation, verification-required status, and API failure with and without self-attestation before enabling the feature for users.

Do not open Firestore to unauthenticated clients. The application will surface permission or configuration errors if the database or rules are not deployed.

## Data and access

- Profiles are private until the user publishes them. The public listing contains display name, an age band (not exact age), gender, country, optional bio, and interests. Email is never written to Firestore by this feature.
- Only adult age bands (`18–24`, `25–34`, and `35+`) and the `adult` cohort may be published. Every People read and write also requires the short-lived `dostAgeEligible` claim, which only the Hostinger endpoint can set after verifying the Firebase ID token and a Play Integrity Standard token. The claim expires after six hours. The rules validate the profile data shape and claim expiry; they cannot independently validate Google Play's Age Signals source, so the Play Console restriction and app gate remain required for release.
- Profile owners can allow chats from any eligible adult, require a contact request, require a mutual match, or turn off new chats. In match-first mode, an incoming request expresses interest and the recipient must accept it before a chat opens. Discovery uses the shared adult cohort, not exact age-band matching. Existing chats remain available unless a participant blocks the other person. Requests can be accepted, ignored, blocked, or reported; an accepted request creates the one-to-one conversation.
- Senders can read the status of their own outgoing requests. A pending request in either direction prevents a duplicate reciprocal request; status changes remain controlled by the recipient.
- Blocking prevents new messages in that conversation and hides that person from the blocker's discovery and inbox. Reports contain the reporter and target Firebase UIDs, a public-profile, conversation, or pending-request reference, reason, and timestamp. Only Firebase project administrators can review reports in Firebase Console; ordinary app clients cannot read them.
- Accepting a request also creates a chat-specific approval record keyed to the conversation ID. Firestore rules use it to authorize the chat; only the approving recipient can create it, and clients cannot edit or delete it.
- Hiding or deleting a profile removes it from discovery, but existing conversation snapshots and messages remain available to conversation participants. There is no in-app deletion control for social chat history, contact requests, approval records, or submitted reports; project administrators manage retention and deletion.
- Audio/video call signaling is stored at `peopleChats/{chatId}/calls/{callId}` and in its `callerCandidates` and `calleeCandidates` subcollections. New call documents contain `mediaType: "audio"` or `"video"`; documents without the field are treated as legacy audio calls. Rules restrict call and candidate access to the matching participants and allowed state transitions, and do not allow the media type to change after creation. `peopleCallTokens/{uid}/devices/{tokenId}` stores private FCM device tokens for incoming-call alerts.

## Call expiry

Every call and ICE-candidate document includes an `expiresAt` timestamp. The client sets it to 55 minutes after call creation, which is also the maximum call duration. Configure Firestore TTL for all three collection groups before enabling calling; TTL deletion is asynchronous and may occur after the timestamp, while Firestore rules stop authorizing expired calls.

From an authenticated Google Cloud CLI session, replace `PROJECT_ID` and enable TTL:

```text
gcloud firestore fields ttls update expiresAt --collection-group=calls --enable-ttl --project=PROJECT_ID
gcloud firestore fields ttls update expiresAt --collection-group=callerCandidates --enable-ttl --project=PROJECT_ID
gcloud firestore fields ttls update expiresAt --collection-group=calleeCandidates --enable-ttl --project=PROJECT_ID
```

Verify all three policies are enabled in the Firestore TTL page or with `gcloud firestore fields ttls list --project=PROJECT_ID`. Call-token documents are not covered by these TTL policies; the FCM sender removes tokens when Firebase reports them as unregistered.

The Firestore rules restrict profile fields to adult cohorts, participant access, message sizes, contact-request creation and acceptance, accepted-contact approval records, blocking, report creation, and an unexpired server-issued age-eligibility claim. Existing profile documents without a contact preference retain the previous everyone-can-start behavior until the owner saves a new preference. Any change to the client data model or claim format must be accompanied by matching rule updates and rule testing.

## Report review and response

- Restrict Firebase Console and project IAM access to the project administrators responsible for reports. Firestore rules deny report reads and queries from app clients, including the reporter and the reported person.
- Project administrators manually review new reports, with a target of 72 hours after submission. There is no in-app moderator dashboard or automated notification, so an administrator must check the `peopleReports` collection regularly enough to meet that target.
- Review the submitted profile, conversation, or request context before taking any action. Submitting a report does not automatically hide a profile or suspend an account. Any action is a manual administrator decision.
- Users may appeal an account or profile action by contacting the support email in the privacy policy. Administrators review appeals manually.
- Reports are not monitored as an emergency service. The app directs users to contact local emergency services when someone is in immediate danger.
- Reports have no automatic retention or deletion period. Administrators must periodically review and remove records when they are no longer needed, subject to applicable obligations.

## Run Firestore rules tests

The emulator tests use the demo project ID `demo-dost` and never connect to or write to the production Firebase project. They cover audio/video media types, legacy audio calls, call participant access, media-type immutability, allowed call-status transitions, ICE-candidate direction, block enforcement, and private notification-token writes in addition to profiles, chats, contact requests, and reports. Install Node.js and the Firebase CLI, then from the repository root install the test-only dependencies and run:

```text
cd tests/firestore
npm install
npm run test:rules
```

The test command starts the Firestore Emulator, loads the repository's `firestore.rules`, runs allowed and denied access tests, then stops the emulator. Tests cover private-profile access, missing and expired age claims, adult-only profiles and discovery, immutable age bands, chat membership, legacy minor-data denial, forged profile snapshots, adult chat creation, incoming and sender-scoped outgoing contact-request access, reverse-request deduplication and acceptance, contact preferences, sender attribution, message limits, blocks, and report ownership.
