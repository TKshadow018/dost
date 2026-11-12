<?php

/**
 * Copy this file to config.php on the server only, and only if Hostinger
 * cannot provide the same values as environment variables. Do not commit
 * config.php. .htaccess denies direct HTTP access to it.
 *
 * Prefer OPENROUTER_API_KEY and DOST_APP_CREDENTIAL_SECRET in the environment.
 * Both secrets below must be replaced. The credential secret must be at least
 * 16 characters and must be the same value passed to the release build as
 * DOST_APP_CREDENTIAL_SECRET. The app reads it from BuildConfig.
 */
return array(
    'openrouter_api_key' => 'REPLACE_WITH_SERVER_OPENROUTER_KEY',
    'app_credential_secret' => 'REPLACE_WITH_RANDOM_SECRET_AT_LEAST_16_CHARS'
);
