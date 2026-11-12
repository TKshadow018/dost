# Dost AI API (Hostinger)

Upload `ai.php`, `archive_store.php`, and `.htaccess` to `public_html/api/` on `bisque-bear-900175.hostingersite.com`. `ai.php` loads `archive_store.php` from the same directory; uploading `ai.php` alone will fail. Keep the existing `default.php` image-upload endpoint; this API uses a separate URL:

`https://bisque-bear-900175.hostingersite.com/api/ai.php`

The server needs PHP with the cURL extension and outbound HTTPS access.

## System friend catalog

Upload `friends.php` and the complete `system_friends/` directory to `public_html/api/` on Hostinger. Keep `system_friends/.htaccess` in place so profile JSON files and photo files cannot be fetched directly. Add one UTF-8 `.json` file per friend directly inside `public_html/api/system_friends/`; the filename (without `.json`) is its stable catalog ID and must contain only letters, numbers, `_`, or `-`. The catalog endpoint scans this directory on each request, so new profile JSON files appear without changing or redeploying the API.

Each profile must include non-empty `name`, `age`, and `gender`. Optional string fields are `personality`, `interests`, `memories`, `conversationStyle`, `family`, `familyMembers`, `familyActivities`, `financialCondition`, `address`, `height`, `weight`, `facialFeatures`, `bodyFeatures`, `busyReason`, and `timeZoneId`. Optional schedule fields are `busyStartHour` (0–23) and `busyDurationHours` (4–8). The repository's `system_friends/sample-friend.json` shows the format. To add a photo, put a JPEG, PNG, or WebP file (up to 5 MB) in `system_friends/photos/` and set the profile's `photo` field to either its filename (`asha.jpg`) or `photos/asha.jpg`. Leave it empty for an initials avatar.

`GET https://bisque-bear-900175.hostingersite.com/api/friends.php` lists profiles for the app; `GET .../friends.php?id=<catalog-id>` returns one complete profile. The `photo_url` in profile responses points back to the API, which validates and streams the associated image. A user can add up to seven catalog friends, in addition to up to three custom friends. The app stores an individual local copy with a unique ID and caches its photo for offline use; deleting it from the app does not remove the server catalog profile.

## Conversation archive

Every user and assistant chat message is saved as JSON Lines on the server. Each entry records the installation UUID (`installation_id`, lowercase) and the selected model ID; API keys are never included in logs. The app queues events with WorkManager and retries when offline; chat requests also archive their history before reaching OpenRouter and store the assistant response before returning it. Records are organized under `username_ip/friend_name/session_id/YYYY-MM-DD_HH-MM-SS.jsonl`; path labels are sanitized, the IP comes from the server's `REMOTE_ADDR`, and the filename uses the earliest event timestamp in that session (UTC). Each session file is deduplicated by message ID. The same session path is appended once to `_by_install/<installation-uuid>.txt` under the archive root so a later deletion request can find that install without scanning every file. See `deletion-lookup.txt`. The logs are not exposed by an HTTP read endpoint. Existing flat hash-named logs are left in place; they cannot be reliably reorganized because their records do not contain the names needed for the folder structure. Lines written before this change have no `installation_id`.

Chat accepts one compressed JPEG image per message (up to 300 KB from the app). The API saves it in the same session folder as `<message-id>.jpg` (or the detected PNG/WebP extension) and records the relative filename on that JSONL entry. Images are sent to OpenRouter as multimodal data URLs; choose a vision-capable model. The image files are private and are not embedded as base64 in the JSONL log.

Archives are stored outside the web root. When `ai.php` lives in `public_html/api/`, the default directory is the sibling of that web root: `<parent of public_html>/dost-private-data/conversation-archive`. For `/home/ACCOUNT/public_html/api/ai.php` that is `/home/ACCOUNT/dost-private-data/conversation-archive`. For `/home/ACCOUNT/domains/example.com/public_html/api/ai.php` that is `/home/ACCOUNT/domains/example.com/dost-private-data/conversation-archive`. `.htaccess` is not the control that protects this directory. The API creates it with `0700` directories and `0600` files. PHP's `open_basedir` must include that parent directory, which Hostinger usually does for the account home. If it does not, add the private directory in hPanel, or set `DOST_CONVERSATION_STORAGE_DIR` to another absolute writable path that is not inside `public_html`. A storage path inside the web root is rejected and chat archival returns HTTP 503 instead of writing there.

An older deployment may still have archives in `public_html/log/`. Upload `archive_store.php` and `migrate_conversation_archive.php` with `ai.php`. From SSH, dry-run then apply:

`php /home/ACCOUNT/public_html/api/migrate_conversation_archive.php`

`php /home/ACCOUNT/public_html/api/migrate_conversation_archive.php --apply`

The script moves each entry except `.htaccess` into the private directory and refuses to run when the destination is inside the web root. `app/api/log/.htaccess` remains only as a deny rule for a leftover `public_html/log/` directory. After the move, delete that leftover directory if the only file left is `.htaccess`. Do not point `DOST_CONVERSATION_STORAGE_DIR` at `public_html/log`.

## Diagnose AI request failures

The API appends server-side request failures to `ai-errors.jsonl` in the same private storage directory as the conversation archives. Each JSON line includes a UTC timestamp and a failure event; provider failures also include the model, whether the server or client key was used, HTTP status, and (when OpenRouter supplies it) its error message/code and provider name. Connection failures include the cURL error and request duration. API keys, prompts, and chat message contents are not written to this diagnostic file. The file is created with `0600` permissions outside the web root. If PHP cannot write this file, it falls back to PHP's configured server error log. Only requests that reach `ai.php` can be logged here; if no corresponding entry appears, check the app's network connection and hosting access/PHP logs.

## Archive retention

Dost's policy retains each server conversation archive for no more than 3,650 days (10 years) after its last activity. Upload `cleanup_logs.php` beside `ai.php` and configure a daily Hostinger cron job to run it with PHP CLI, for example `php /home/ACCOUNT/public_html/api/cleanup_logs.php` (replace the path with the actual Hostinger path). The script is CLI-only, uses the same private directory as the API, removes expired session folders and attached images, and also expires legacy root-level JSONL archives. If `public_html/log/` still exists and is not the active directory, the same 3,650-day pass runs against it so files left there during migration do not skip retention. That pass is not a reason to keep archives on the web. Each run writes `.cleanup-heartbeat.json` (last run time, the private storage path, and deletion counts) inside the private directory; check its `last_run` timestamp periodically to confirm the cron job is still alive. Confirm the cron job runs successfully and configure any hosting-provider backups with a compatible retention period; the script does not purge provider-managed backups. A deletion request for one install is described in `deletion-lookup.txt`. There is still no delete-archive API.

Copy the plain-text contents of `privacy-policy.txt` into a public Google Sites page, publish that page, and link it from the Play Store listing and the app. Publish the policy only after the cleanup job is deployed and scheduled. Google Play requires disclosure of the developer's retention and deletion practices but does not prescribe a universal numeric maximum; 3,650 days (10 years) is Dost's chosen cap, not a Google-mandated period. The policy must continue to match the deployed API, cron job, provider practices, and Play Console Data safety declarations.

## Configure the OpenRouter key

Prefer setting the PHP/server environment variable `OPENROUTER_API_KEY`. If Hostinger does not expose environment variables to PHP, copy `config.example.php` to `config.php` on the server only and replace its placeholder with the key. Do not commit or distribute `config.php`. The included `.htaccess` denies direct HTTP access to it.

With no `X-OpenRouter-Key` request header, the endpoint uses the server key and only permits the app's listed `:free` models. If the app sends a user's own key in that HTTPS header, any syntactically valid OpenRouter `provider/model` ID is accepted and billed to that user's OpenRouter account. The endpoint does not store the custom key. AI requests are rate-limited per client IP to 30 per minute and message-log requests to 120 per minute. The URL is public; the rate limits are abuse guards, not proof that requests come from the app. Review usage and rotate the server key if it is exposed.

## Verify after upload

Open the URL in a browser and expect HTTP 405 with a JSON `POST requests only` message. A successful AI request requires a POST JSON body from the app and a valid server-side key.

From a machine with PHP 8.5 or newer CLI, run `php test_api.php` to check that the deployed endpoint returns the expected response. To check another deployment, pass its URL: `php test_api.php https://example.com/api/ai.php`. This smoke test does not send an AI request or require an API key.