# Dost

## Firebase Authentication setup

Firebase Authentication provides email/password accounts, email verification, password reset, and Google sign-in. Account identity is stored with Firebase; profiles, friends, and conversations remain local to the device.

1. Register an Android app with package name `com.snigtus.dost` in the Firebase console.
2. Enable **Email/Password** and **Google** under Authentication → Sign-in method.
3. Add the SHA-1 and SHA-256 fingerprints for each debug/release signing key in Firebase project settings. Google sign-in requires the OAuth web client ID generated for the Firebase project.
4. Download `google-services.json` and place it at `app/google-services.json`. It is intentionally ignored by Git. The Google Services Gradle plugin is applied only when this file exists, so builds and unit tests can run without Firebase configuration; authentication will show a setup message until it is supplied.
5. Configure the Firebase email verification and password reset templates before distributing the app.

Run the debug unit tests and build with `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
