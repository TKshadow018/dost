# Dost AI API (Hostinger)

Upload `ai.php` and `.htaccess` to `public_html/api/` on `bisque-bear-900175.hostingersite.com`. Keep the existing `default.php` image-upload endpoint; this API uses a separate URL:

`https://bisque-bear-900175.hostingersite.com/api/ai.php`

The server needs PHP with the cURL extension and outbound HTTPS access.

## Conversation archive

Every user and assistant chat message is saved as JSON Lines on the server. Each entry records the selected model ID; API keys are never included in logs. The app queues events with WorkManager and retries when offline; chat requests also archive their history before reaching OpenRouter and store the assistant response before returning it. Records are organized under `username_ip/friend_name/session_id/YYYY-MM-DD_HH-MM-SS.jsonl`; path labels are sanitized, the IP comes from the server's `REMOTE_ADDR`, and the filename uses the earliest event timestamp in that session (UTC). Each session file is deduplicated by message ID. The logs are not exposed by an HTTP read endpoint. Existing flat hash-named logs are left in place; they cannot be reliably reorganized because their records do not contain the names needed for the new folder structure.

Chat accepts one compressed JPEG image per message (up to 300 KB from the app). The API saves it in the same session folder as `<message-id>.jpg` (or the detected PNG/WebP extension) and records the relative filename on that JSONL entry. Images are sent to OpenRouter as multimodal data URLs; choose a vision-capable model. The image files are private and are not embedded as base64 in the JSONL log.

By default, files are written to `public_html/log/`, alongside this API directory. Before accepting chat requests, create that folder on Hostinger and upload `app/api/log/.htaccess` as `public_html/log/.htaccess`; it disables directory listings and denies HTTP access to the chat logs. The API creates directories with `0700` and files with `0600` permissions, and PHP must be allowed to write there. Because this location is inside the web root, verify that requests for a log file are denied. You can instead set `DOST_CONVERSATION_STORAGE_DIR` to a private writable directory outside the web root.

## Archive retention

Dost's policy retains each server conversation archive for no more than 3,650 days (10 years) after its last activity. Upload `cleanup_logs.php` beside `ai.php` and configure a daily Hostinger cron job to run it with PHP CLI, for example `php /home/ACCOUNT/public_html/api/cleanup_logs.php` (replace the path with the actual Hostinger path). The script is CLI-only, uses the same `DOST_CONVERSATION_STORAGE_DIR` setting as the API, removes expired session folders and attached images, and also expires legacy root-level JSONL archives. Each run writes `.cleanup-heartbeat.json` (last run time and deletion counts) inside the storage directory; check its `last_run` timestamp periodically to confirm the cron job is still alive. Confirm the cron job runs successfully and configure any hosting-provider backups with a compatible retention period; the script does not purge provider-managed backups.

Copy the plain-text contents of `privacy-policy.txt` into a public Google Sites page, publish that page, and link it from the Play Store listing and the app. Publish the policy only after the cleanup job is deployed and scheduled. Google Play requires disclosure of the developer's retention and deletion practices but does not prescribe a universal numeric maximum; 3,650 days (10 years) is Dost's chosen cap, not a Google-mandated period. The policy must continue to match the deployed API, cron job, provider practices, and Play Console Data safety declarations.

## Configure the OpenRouter key

Prefer setting the PHP/server environment variable `OPENROUTER_API_KEY`. If Hostinger does not expose environment variables to PHP, copy `config.example.php` to `config.php` on the server only and replace its placeholder with the key. Do not commit or distribute `config.php`. The included `.htaccess` denies direct HTTP access to it.

With no `X-OpenRouter-Key` request header, the endpoint uses the server key and only permits the app's listed `:free` models. If the app sends a user's own key in that HTTPS header, any syntactically valid OpenRouter `provider/model` ID is accepted and billed to that user's OpenRouter account. The endpoint does not store the custom key. AI requests are rate-limited per client IP to 30 per minute and message-log requests to 120 per minute. The URL is public; the rate limits are abuse guards, not proof that requests come from the app. Review usage and rotate the server key if it is exposed.

## Verify after upload

Open the URL in a browser and expect HTTP 405 with a JSON `POST requests only` message. A successful AI request requires a POST JSON body from the app and a valid server-side key.

From a machine with PHP 8.5 or newer CLI, run `php test_api.php` to check that the deployed endpoint returns the expected response. To check another deployment, pass its URL: `php test_api.php https://example.com/api/ai.php`. This smoke test does not send an AI request or require an API key.