# Dost

## Product documentation

See the [Dost App Overview](APP_OVERVIEW_FOR_USERS.md) for a user-facing summary of current features. Product improvement and launch ideas are in [Improvement and Marketing Proposals](APP_IMPROVEMENT_AND_MARKETING_PROPOSALS.md).

For end-to-end Firebase, Hostinger backend, People calling, and APK setup, see the [Setup and Build Guide](SETUP_AND_BUILD_GUIDE.md).

Shield decisions, launch gates, and draft legal/Data safety materials are in [SUPER_SECURE_MVP.md](SUPER_SECURE_MVP.md) and [docs/super-secure](docs/super-secure/).

## Firebase Authentication setup

Firebase Authentication provides email/password accounts, email verification, password reset, and Google sign-in. Account identity is stored with Firebase; profiles, friends, and conversations remain local to the device.

1. Register an Android app with package name `com.snigtus.dost` in the Firebase console.
2. Enable **Email/Password** and **Google** under Authentication → Sign-in method.
3. Add the SHA-1 and SHA-256 fingerprints for each debug/release signing key in Firebase project settings. Google sign-in requires the OAuth web client ID generated for the Firebase project.
4. Download `google-services.json` and place it at `app/google-services.json`. It is intentionally ignored by Git. The Google Services Gradle plugin is applied only when this file exists, so debug builds and unit tests can run without Firebase configuration; authentication will show a setup message until it is supplied. Release builds fail early unless this file provides Firebase Authentication configuration for `com.snigtus.dost`.
5. Configure the Firebase email verification and password reset templates before distributing the app.

Run the debug unit tests and build with `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
