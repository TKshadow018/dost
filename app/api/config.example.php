<?php

/**
 * Copy this file to config.php on the server only, and only if Hostinger
 * cannot provide the same values as environment variables. Do not commit
 * config.php. .htaccess denies direct HTTP access to it.
 *
 * Prefer environment variables for server values. Never put a Firebase service
 * account key in this file or anywhere under the public web root.
 */
return array(
    'openrouter_api_key' => 'REPLACE_WITH_SERVER_OPENROUTER_KEY',
    'app_credential_secret' => 'REPLACE_WITH_RANDOM_SECRET_AT_LEAST_16_CHARS',
    'firebase_project_id' => 'REPLACE_WITH_FIREBASE_PROJECT_ID',
    'firebase_web_api_key' => 'REPLACE_WITH_FIREBASE_WEB_API_KEY',
    'firebase_service_account_path' => '/home/ACCOUNT/private/firebase-service-account.json',
    'premium_service_account_path' => '/home/ACCOUNT/private/firebase-service-account.json',
    'play_package_name' => 'com.snigtus.dost',
    'premium_enabled' => 'false',
    'explore_enabled' => 'false',
    'explore_storage_directory' => '/home/ACCOUNT/private/dost-explore',
    'intro_premium_enabled' => 'false',
    'intro_premium_days' => '90',
    'intro_premium_launch_at' => '',
    // Optional debug-build People testing; keep disabled unless you explicitly add tester UIDs.
    // 'age_eligibility_test_mode' => false,
    // 'age_eligibility_test_uids' => array()
);
