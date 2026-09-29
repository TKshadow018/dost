# Dost — Agent Instructions

## After every change

Every time an AI agent makes code changes to this project, it must finish by generating a fresh debug APK and confirming it builds. Run from the workspace root (`d:\Professional\dost`):

```
JAVA_HOME="C:\Program Files\Java\jdk-25.0.4.1" ./gradlew.bat :app:assembleDebug
```

On success, the APK is at `app/build/outputs/apk/debug/app-debug.apk`. Confirm the file exists and report its size. Do not end the turn until the build succeeds or a real blocker is reported.

## Validation before building

- PHP changes: lint every edited file with `php.exe -l <file>`.
- Kotlin changes: run `JAVA_HOME="C:\Program Files\Java\jdk-25.0.4.1" ./gradlew.bat :app:testDebugUnitTest` before assembling the APK.

## Conventions

- Package / applicationId: `com.snigtus.dost`. The source folder is `app/src/main/java/com/snigtus/bondhu` (legacy name); keep new files under it.
- Chat messages are stored as per-friend JSON files under `filesDir/messages/` (not SharedPreferences). Keep them out of backups — see `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`.
- Free models: `OpenRouterClient.freeModels` (app) and `$allowedModels` in `app/api/ai.php` (server) must stay in sync. Paid models are only forwarded when the user supplies their own `X-OpenRouter-Key`.
- Release builds require the `DOST_KEYSTORE_PASSWORD` and `DOST_KEY_PASSWORD` environment variables; do not attempt `assembleRelease` without them.
- Deploy server changes by uploading `app/api/ai.php` and `app/api/cleanup_logs.php` to Hostinger; schedule the cleanup cron before relying on the retention policy.
