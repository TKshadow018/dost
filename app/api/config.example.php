<?php

/**
 * Copy this file to config.php on the server only, and only if Hostinger
 * cannot provide the same values as environment variables. Do not commit
 * config.php. .htaccess denies direct HTTP access to it.
 *
 * Prefer environment variables for server values. Never put a Firebase service
 * account key, a Cloudflare TURN API token, or a coturn shared secret in the
 * repository or anywhere under the public web root. Leave the Cloudflare
 * values empty to keep using coturn.
 */
return array(
    'openrouter_api_key' => 'REPLACE_WITH_SERVER_OPENROUTER_KEY',
    'app_credential_secret' => 'REPLACE_WITH_RANDOM_SECRET_AT_LEAST_16_CHARS',
    'firebase_project_id' => 'REPLACE_WITH_FIREBASE_PROJECT_ID',
    'firebase_web_api_key' => 'REPLACE_WITH_FIREBASE_WEB_API_KEY',
    'firebase_service_account_path' => '/home/ACCOUNT/private/firebase-service-account.json',
    'cloudflare_turn_key_id' => '',
    'cloudflare_turn_api_token' => ''
);
