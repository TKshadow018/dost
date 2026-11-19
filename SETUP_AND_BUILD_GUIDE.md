# Dost Backend, Firebase, and APK Setup

This guide covers a fresh Dost setup: Firebase, the Hostinger PHP API, People calling infrastructure, and local debug or signed release APKs. Follow the sections in order. Keep the detailed [Firebase/Firestore guide](FIRESTORE_SETUP.md) and [API deployment guide](app/api/README.md) nearby; this document is the end-to-end checklist, not a replacement for their detailed requirements.

## 1. Prerequisites

- A Firebase / Google Cloud project and access to Google Play Console.
- A Hostinger PHP site with HTTPS, PHP cURL and OpenSSL extensions, and outbound HTTPS access.
- An OpenRouter account if you want to use a server-managed AI key.
- Node.js and the Firebase CLI for deploying and testing Firestore rules.
- A Java JDK and Android SDK installed. The project is configured for Android SDK platform 37.0 and Java 11 source compatibility; use the JDK configured for your Gradle environment.
- Cloudflare Realtime TURN, or a coturn-compatible TURN server, for reliable People audio/video calls.

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
2. Link this Google Cloud project to `com.snigtus.dost` in Play Console. Configure **Restrict Minor Access** for the app; People is adult-only.
3. Enable the Play Integrity API, Identity Toolkit API, Cloud Firestore API, and Firebase Cloud Messaging API in the linked Google Cloud project.
4. Deploy the checked-in Firestore rules and indexes from the repository root:

   ```powershell
   firebase login
   firebase deploy --only firestore --project YOUR_FIREBASE_PROJECT_ID
   ```

   Do not replace these rules with open test rules. People access requires the server-issued, short-lived `dostAgeEligible` claim.
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

The app's Play Age Signals gate and Play Console restriction are separate from the server claim. A sideloaded debug build normally cannot satisfy Play licensing checks; verify age eligibility with a Play-installed build and a registered Play license tester.

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
| Cloudflare TURN key ID | `DOST_CLOUDFLARE_TURN_KEY_ID` | From Cloudflare dashboard → Realtime → TURN, after creating a TURN app. Preferred TURN source. |
| Cloudflare TURN API token | `DOST_CLOUDFLARE_TURN_API_TOKEN` | Bearer token for that TURN key. Server only; never put it in the app, logs, or source control. |
| TURN server URLs | `DOST_TURN_URLS` | Coturn fallback only, used when both Cloudflare values are empty. Comma-separated `turn:` / `turns:` URLs. |
| TURN shared secret | `DOST_TURN_SHARED_SECRET` | Coturn fallback only. Match coturn's `use-auth-secret` secret. Never include it in the app or source control. |
| Archive storage | `DOST_CONVERSATION_STORAGE_DIR` | Optional absolute writable path outside the web root; use it if the documented default is unavailable. |

Use separate, randomly generated values for the app credential and TURN shared secret. Keep each service-account key outside the web root and source control. Do not grant broad project roles such as Owner or Editor to these service accounts.

For the complete endpoint permissions, variable fallbacks, and coturn requirements, follow the **Trusted People age eligibility** and **People audio and video calls** sections of [app/api/README.md](app/api/README.md).

### Provision TURN and retention

For People calls, create a TURN app in the Cloudflare dashboard under **Realtime → TURN** and set `DOST_CLOUDFLARE_TURN_KEY_ID` and `DOST_CLOUDFLARE_TURN_API_TOKEN` to that app's key ID and API token. The API then requests one-hour ICE servers from Cloudflare. Leave both values empty to keep the coturn fallback: configure coturn separately using the same `DOST_TURN_SHARED_SECRET` and `use-auth-secret`, expose the configured listener and relay ports through the server firewall, and use a valid TLS certificate for `turns:` URLs. The app can signal a call without TURN, but calls may fail on networks that block direct peer connections.

Configure a daily Hostinger cron job to run `cleanup_logs.php` with PHP CLI. Confirm it runs successfully before relying on archive-retention cleanup. Firestore TTL separately removes call signaling and ICE-candidate documents; the cron job does not replace Firestore TTL.

### Run backend checks

From `app/api/` on a PHP development environment, lint the deployed PHP files and run the focused tests:

```powershell
php.exe -l age_eligibility.php
php.exe -l age_eligibility_support.php
php.exe -l people_call_support.php
php.exe -l people_call_push.php
php.exe -l people_call_ice_servers.php
php.exe test_age_eligibility.php
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

Install this APK on a test device to check ordinary sign-in, permissions, chat, and calling flows. Debug builds are signed with the Android debug key and are not a substitute for testing Play Integrity from a Play-installed build.

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
- [ ] Firestore rules and indexes are deployed; adult eligibility works through the Play-installed flow.
- [ ] TTL is enabled for `calls`, `callerCandidates`, and `calleeCandidates`.
- [ ] Hostinger endpoints, helper files, service accounts, PHP extensions, and environment values are configured.
- [ ] The shared app credential matches between Hostinger and the release build; no service-account or TURN secret is in the APK.
- [ ] Cloudflare Realtime TURN credentials are set, or the coturn fallback (TLS, firewall access, and TURN URLs) is verified.
- [ ] The archive cleanup cron is configured and checked.
- [ ] Android unit tests, Firestore emulator rules tests, and PHP tests pass.
- [ ] Two separate devices have been used to test incoming FCM alerts in the background and audio/video calls on real networks.
- [ ] The published privacy policy and Play Console Data safety declarations match the deployed behavior.

For detailed privacy and moderation rules, see [FIRESTORE_SETUP.md](FIRESTORE_SETUP.md), [app/api/README.md](app/api/README.md), and [the privacy policy](app/api/privacy-policy.txt).
