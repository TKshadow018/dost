# Dost Backend, Firebase, and APK Setup

This guide covers a fresh Dost setup: Firebase, the Hostinger PHP API, People calling infrastructure, and local debug or signed release APKs. Follow the sections in order. Keep the detailed [Firebase/Firestore guide](FIRESTORE_SETUP.md) and [API deployment guide](app/api/README.md) nearby; this document is the end-to-end checklist, not a replacement for their detailed requirements.

## 1. Prerequisites

- A Firebase / Google Cloud project and access to Google Play Console.
- A Hostinger PHP site with HTTPS, PHP cURL and OpenSSL extensions, and outbound HTTPS access.
- An OpenRouter account if you want to use a server-managed AI key.
- Node.js and the Firebase CLI for deploying and testing Firestore rules.
- A Java JDK and Android SDK installed. The project is configured for Android SDK platform 37.0 and Java 11 source compatibility; use the JDK configured for your Gradle environment.
- A coturn-compatible TURN server for reliable People audio/video calls.

The Android package and Firebase Android app ID must be `com.snigtus.dost`. The app currently sends API requests to `bisque-bear-900175.hostingersite.com`. If you deploy to a different hostname, update the API URL constants in the Android clients and rebuild the APK.

## 2. Configure Firebase and Play Console

### Register the Android app

1. Create or select the Firebase project that will be used by the app.
2. In Firebase project settings, add an Android app with package name `com.snigtus.dost`.
3. Enable **Email/Password** and **Google** sign-in in **Authentication → Sign-in method**. Configure email verification and password reset templates.
4. Download the app's `google-services.json` and place it at `app/google-services.json`. This file is ignored by Git and must not be committed.
5. In Firebase project settings, add the SHA-1 and SHA-256 fingerprints for the debug and release signing certificates. Run `.\gradlew.bat signingReport` to see the debug fingerprints. After creating the release keystore in section 7, use `keytool -list -v -keystore app\dost-upload.jks -alias dost-upload` for its fingerprints. Google sign-in also requires the Firebase-generated Web OAuth client.

Debug builds can compile without `google-services.json`, but Firebase authentication and People features will not work. Release builds require valid Firebase configuration for this package.

### Enable People and age eligibility

1. Create a Cloud Firestore database in the same project and region that the app will use.
2. Link this Google Cloud project to `com.snigtus.dost` in Play Console. The app is for ages 13+; People discovery/chat and Explore Plus remain adults-only and require server-verified eligibility. Do not configure **Restrict Minor Access** for the whole app.
3. Enable the Play Integrity API, Identity Toolkit API, Cloud Firestore API, and Firebase Cloud Messaging API in the linked Google Cloud project.
4. Deploy the checked-in Firestore rules and indexes from the repository root:

   ```powershell
   firebase login
   firebase deploy --only firestore --project YOUR_FIREBASE_PROJECT_ID
   ```

   Do not replace these rules with open test rules. People access requires the server-issued `dostAgeCohort == adult` claim and short-lived `dostAgeEligible` claim.
5. Enable Firestore TTL for each call collection group. Replace `YOUR_FIREBASE_PROJECT_ID` in these commands:

   ```powershell
   gcloud firestore fields ttls update expiresAt --collection-group=calls --enable-ttl --project=YOUR_FIREBASE_PROJECT_ID
   gcloud firestore fields ttls update expiresAt --collection-group=callerCandidates --enable-ttl --project=YOUR_FIREBASE_PROJECT_ID
   gcloud firestore fields ttls update expiresAt --collection-group=calleeCandidates --enable-ttl --project=YOUR_FIREBASE_PROJECT_ID
   ```

   TTL deletion is asynchronous. Rules stop authorizing expired calls even if cleanup has not run yet.
6. Run the Firestore emulator tests before deploying rule changes:

   ```powershell
   Push-Location tests\firestore
   npm install
   npm run test:rules
   Pop-Location
   ```

### Owner checklist: Play audience, rating, and child safety

The repository does not change Play Console settings or publish policy pages. Complete these items before release:

- [ ] In Play Console **Target audience and content**, select ages **13–15**, **16–17**, and **18+**. Do not select under 13; this is not a Designed for Families app.
- [ ] Turn **off** whole-app **Restrict Minor Access**. Keep adult-only People and Explore Plus enforcement in server claims and Firestore/PHP.
- [ ] Complete the IARC content-rating questionnaire for **user interaction**, **sharing user-provided information**, and **AI-generated content**; save the submitted answers and rating.
- [ ] Publish the app's Child Safety Standards / CSAE page on Hostinger. Add the final public URL here, link it in-app and in Play Console, and keep it current: **Owner to provide URL: ____________________**.
- [ ] Verify in-app report and block controls on Circle 1:1 and groups, Explore AI chats, Shield, and Cipher. Reports are priority-labelled for review, including reports from or about teens; test that blocks take effect immediately in Firestore rules.
- [ ] Document the CSAM escalation and reporting process, including preservation, platform escalation, and legally required reporting. Do not ask users to upload or send suspected CSAM to support.
- [ ] Designate and monitor a child-safety contact for Play Console and the published CSAE page: **Owner to provide contact: ____________________**.
- [ ] Confirm reports from or about minors are prioritized for review, and test that blocks take effect immediately in the server rules.

These are owner actions; this PR does not modify Play Console or production configuration. Paid billing and age-sensitive features must remain disabled by default until the owner explicitly approves enabling them.

Play Age Signals supplies an age band and does not block teens from the app. A sideloaded debug build normally cannot satisfy Play licensing checks; verify adult-only People eligibility with a Play-installed build and a registered Play license tester. The Hostinger `age_cohort.php` endpoint assigns immutable server claims from the birth month/year entered before signup.

For QA on a debug or sideloaded build, the server-only tester allowlist is disabled by default. Set `DOST_AGE_ELIGIBILITY_TEST_MODE=true` (or `age_eligibility_test_mode => true` in server-only `config.php`) and set `DOST_AGE_ELIGIBILITY_TEST_UIDS` to a comma-separated list of Firebase UIDs (or `age_eligibility_test_uids` to an array). Only the exact value `true` enables test mode, and an empty allowlist enables nobody. For allowlisted UIDs only, the server may accept `UNRECOGNIZED_VERSION` and `UNLICENSED`; Firebase token/UID verification, the Google Integrity decode call, package name, request hash and nonce, fresh timestamp, and `MEETS_DEVICE_INTEGRITY` remain required. Device integrity is not relaxed. Successful checks still issue the normal six-hour claim. Rejections are logged by failed check without tokens or raw payloads, and only allowlisted testers receive a non-sensitive reason code; production callers keep the generic error. Never add a client-side debug bypass.

Play Console [Play Integrity API test responses](https://developer.android.com/google/play/integrity/setup#test) and license testers can exercise verdict combinations on Play-installed builds (for example, Internal testing). They do not reliably make a debug-key sideloaded APK `PLAY_RECOGNIZED`, because the signing certificate differs from Play App Signing. Use the server allowlist for People testing on debug APKs, and a Play-installed build for the production Integrity path.

## 3. Deploy the Hostinger backend

### Upload runtime files

Create or use `public_html/api/` on the configured HTTPS host. Synchronize the current runtime PHP endpoints and their helper files from `app/api/`, along with `.htaccess` and the `system_friends/` directory. The API has shared helpers, so uploading only an endpoint is insufficient. Keep the existing `default.php` endpoint if the site uses it.

Do not upload `test_*.php` as public endpoints. Never put service-account JSON files, private archives, TURN secrets, or an unprotected `config.php` in the web root. The checked-in `.htaccess` must remain in place. If Hostinger cannot provide PHP environment variables, copy `config.example.php` to `config.php` on the server only; never commit or distribute that file.

### Configure server values

Set these values in Hostinger's PHP environment or the server-only `config.php` fallback where supported:

| Purpose | Environment variable | Notes |
|---|---|---|
| AI server key | `OPENROUTER_API_KEY` | Optional if users provide their own OpenRouter key. |
| App credential | `DOST_APP_CREDENTIAL_SECRET` | Use the same value when building the app. At least 16 characters; it is embedded in the APK and is only a basic gate, not strong authentication. |
| Firebase project | `DOST_FIREBASE_PROJECT_ID` | Must match `google-services.json` and the service accounts. |
| Firebase Web API key | `DOST_FIREBASE_WEB_API_KEY` | Used by the server's Firebase REST requests. It is not a service-account credential. |
| Age-eligibility service account | `DOST_FIREBASE_SERVICE_ACCOUNT_PATH` | Absolute path to a key file outside `public_html`; grant only `firebaseauth.users.get` and `firebaseauth.users.update`. |
| People-call service account | `DOST_CALL_SERVICE_ACCOUNT_PATH` | Absolute path outside `public_html`; grant only the permissions documented in the [API guide](app/api/README.md#people-audio-and-video-calls). |
| TURN server URLs | `DOST_TURN_URLS` | Comma-separated `turn:` / `turns:` URLs reachable by the app. |
| TURN shared secret | `DOST_TURN_SHARED_SECRET` | Match coturn's `use-auth-secret` secret. Never include it in the app or source control. |
| Archive storage | `DOST_CONVERSATION_STORAGE_DIR` | Optional absolute writable path outside the web root; use it if the documented default is unavailable. |

Use separate, randomly generated values for the app credential and TURN shared secret. Keep each service-account key outside the web root and source control. Do not grant broad project roles such as Owner or Editor to these service accounts.

For the complete endpoint permissions, variable fallbacks, and coturn requirements, follow the **Trusted People age eligibility**, **Premium**, and **People audio and video calls** sections of [app/api/README.md](app/api/README.md).

### Provision TURN and retention

Configure coturn separately using the same `DOST_TURN_SHARED_SECRET` and `use-auth-secret`; expose the configured listener and relay ports through the server firewall. Use a valid TLS certificate for `turns:` URLs. The app can signal a call without TURN, but calls may fail on networks that block direct peer connections.

Configure a daily Hostinger cron job to run `cleanup_logs.php` with PHP CLI. Confirm it runs successfully before relying on archive-retention cleanup. Firestore TTL separately removes call signaling and ICE-candidate documents; the cron job does not replace Firestore TTL.

### Run backend checks

From `app/api/` on a PHP development environment, lint the deployed PHP files and run the focused tests:

```powershell
php.exe -l age_eligibility.php
php.exe -l age_eligibility_support.php
php.exe -l age_cohort.php
php.exe -l ai.php
php.exe -l premium_verify.php
php.exe -l premium_intro_grant.php
php.exe -l people_call_support.php
php.exe -l people_call_push.php
php.exe -l people_call_ice_servers.php
php.exe -l circle_support.php
php.exe -l circle_handle_lookup.php
php.exe -l circle_invite.php
php.exe test_age_eligibility.php
php.exe test_circle_contacts.php
php.exe test_premium_support.php
php.exe test_romance_safety.php
php.exe test_people_call_support.php
```

Also run `php.exe test_app_credential.php` when changing the shared app-credential configuration. These tests do not provision TURN, send an FCM push, or prove that two real devices can connect.

## 4. Build and verify a debug APK

Open PowerShell at the repository root. Set the JDK path for the current shell, then run unit tests and assemble the debug APK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Confirm that the file exists and check its size:

```powershell
Get-Item app\build\outputs\apk\debug\app-debug.apk | Select-Object FullName, Length
```

Install this APK on a test device to check ordinary sign-in, permissions, chat, and calling flows. Debug builds are signed with the Android debug key and are not a substitute for testing Play Integrity from a Play-installed build. To test People on this APK, configure the server-only tester allowlist described above; the app still calls the server and never skips Integrity. Play Console test responses are for Play-installed builds and do not reliably grant `PLAY_RECOGNIZED` to debug-key sideloads.

## 5. Create a signed release APK

### Create and register the upload key

Create the keystore once, store a protected backup outside the repository, and do not lose it:

```powershell
keytool -genkeypair -v -keystore app\dost-upload.jks -alias dost-upload -keyalg RSA -keysize 2048 -validity 10000
```

The project expects the keystore at `app/dost-upload.jks` with alias `dost-upload`. Add its SHA-1 and SHA-256 fingerprints to Firebase project settings, then download the matching `google-services.json` into `app/`.

### Build

Set the signing passwords in the current PowerShell session, set the same app credential value configured on Hostinger, and build:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
$env:DOST_KEYSTORE_PASSWORD = 'YOUR_KEYSTORE_PASSWORD'
$env:DOST_KEY_PASSWORD = 'YOUR_KEY_PASSWORD'
$env:DOST_APP_CREDENTIAL_SECRET = 'THE_SAME_VALUE_CONFIGURED_ON_HOSTINGER'
.\gradlew.bat :app:assembleRelease
```

The signed APK is written to:

```text
app\build\outputs\apk\release\app-release.apk
```

Do not put passwords or keys in Gradle files, source control, or this guide. Release builds fail without `app/google-services.json` and the two signing password environment variables. `DOST_APP_CREDENTIAL_SECRET` must match the server value for authenticated server-key/archive operations; because it is compiled into the app, it is not a secure client secret.

For Google Play distribution, configure Play App Signing and follow Play Console's current packaging requirements. Increase `versionCode` in `app/build.gradle` for each new release. Uploading an APK directly does not make it a Play-installed build.

## 6. Pre-release checklist

- [ ] Firebase Auth methods, email templates, and debug/release SHA fingerprints are configured.
- [ ] `app/google-services.json` matches `com.snigtus.dost` and the intended Firebase project.
- [ ] Firestore rules and indexes are deployed; the neutral under-13 age screen, 13–17 cohort, teen-safe AI policy, and server-verified 18+ Explore Plus eligibility are implemented and tested.
- [ ] TTL is enabled for `calls`, `callerCandidates`, and `calleeCandidates`.
- [ ] Hostinger endpoints, helper files, service accounts, PHP extensions, and environment values are configured.
- [ ] The shared app credential matches between Hostinger and the release build; no service-account or TURN secret is in the APK.
- [ ] coturn, TLS, firewall access, and TURN URLs are verified.
- [ ] The archive cleanup cron is configured and checked.
- [ ] Android unit tests, Firestore emulator rules tests, and PHP tests pass.
- [ ] Two separate devices have been used to test incoming FCM alerts in the background and audio/video calls on real networks.
- [ ] The published privacy policy and Play Console Data safety declarations match the deployed behavior.
- [ ] Update the IARC questionnaire for user interaction, shared information, and AI-generated content.
- [ ] Set the Play target audience to 13–15, 16–17, and 18+ (not under 13; not Designed for Families); switch **Restrict Minor Access** off for the whole app.
- [ ] Verify in-app report/block flows and the priority-review process are operational before release.
- [ ] Publish the Child Safety Standards / CSAE page, record its URL, and link it in-app and in Play Console; document the CSAM reporting process and provide the designated child-safety contact.
- [ ] Ask counsel to decide regional signup restrictions or parental-consent handling under GDPR Article 8 (13–16 depending on country, including 16 in Germany and the Netherlands), the UK Age Appropriate Design Code, and Japan's APPI rules for minors.
- [ ] Keep owner/counsel implementation decisions out of the public policy. Publish the privacy policy only after confirming its audience and data-practice statements match the shipped age controls and mode availability.
- [ ] Before enabling Shield, confirm privacy, customer terms, DPA, Data safety, retention/deletion operations, and any export or customer-hosted migration commitments.
- [ ] Before enabling Cipher, verify the end-to-end encryption design and confirm metadata categories, providers, retention, and deletion are described accurately.
- [ ] Ask counsel to review local laws for AI companion services and minors, including AI disclosure and self-harm-response requirements.
- [ ] Complete an encryption export classification review for US EAR mass-market treatment and Japan FEFTA before release; obtain owner/counsel approval and retain the determination.
- [ ] Review store listing text, screenshots, and marketing: do not say “military grade,” “unbreakable,” or “spy”; do not describe Shield as end-to-end encrypted.
- [ ] Keep billing and age-restricted paid modes feature-flagged off by default until the owner approves release and server-side entitlements/age checks are verified.

For detailed privacy and moderation rules, see [FIRESTORE_SETUP.md](FIRESTORE_SETUP.md), [app/api/README.md](app/api/README.md), and [the privacy policy](app/api/privacy-policy.txt).
