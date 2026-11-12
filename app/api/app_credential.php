<?php

/**
 * Gate for requests that would spend the server OpenRouter key.
 *
 * The app proves it knows DOST_APP_CREDENTIAL_SECRET by sending
 * X-Dost-App-Credential: v1.<unix-seconds>.<hex hmac-sha256>.
 * The HMAC payload is "v1\n<unix-seconds>\n<installation id>" using the
 * trimmed X-Dost-Install-ID value, or an empty installation id when that
 * header is absent. The timestamp must be within fifteen minutes of the server clock.
 *
 * A caller's own X-OpenRouter-Key does not spend the server key, so it skips
 * this gate. DOST_ALLOW_LEGACY_SERVER_KEY=1 also skips it so already-installed
 * builds can keep using the server key until a build that sends the header ships.
 * That flag re-opens unauthenticated spend and is off unless the operator sets it.
 */

function dost_legacy_server_key_allowed()
{
    $flag = getenv('DOST_ALLOW_LEGACY_SERVER_KEY');
    if (!is_string($flag)) {
        return false;
    }
    return in_array(strtolower(trim($flag)), array('1', 'true', 'yes', 'on'), true);
}

function dost_app_credential_secret($config)
{
    $fromEnv = getenv('DOST_APP_CREDENTIAL_SECRET');
    if (is_string($fromEnv) && trim($fromEnv) !== '') {
        $secret = trim($fromEnv);
    } elseif (is_array($config) && isset($config['app_credential_secret']) && is_string($config['app_credential_secret'])) {
        $secret = trim($config['app_credential_secret']);
    } else {
        $secret = '';
    }
    if (strlen($secret) < 16) {
        return '';
    }
    return $secret;
}

function dost_app_credential_status($presented, $installationId, $now, $config)
{
    $secret = dost_app_credential_secret($config);
    if ($secret === '') {
        return 'not_configured';
    }
    if (!is_string($presented) || preg_match('/\Av1\.([0-9]{1,12})\.([0-9a-f]{64})\z/', $presented, $matches) !== 1) {
        return 'missing';
    }
    $timestamp = (int) $matches[1];
    if ($timestamp < 1 || abs($now - $timestamp) > 900) {
        return 'expired';
    }
    $install = is_string($installationId) ? trim($installationId) : '';
    $expected = hash_hmac('sha256', "v1\n" . $timestamp . "\n" . $install, $secret);
    if (!is_string($expected) || !hash_equals($expected, $matches[2])) {
        return 'invalid';
    }
    return '';
}

function dost_server_key_decision($usesClientKey, $presented, $installationId, $now, $config)
{
    if ($usesClientKey || dost_legacy_server_key_allowed()) {
        return null;
    }
    $status = dost_app_credential_status($presented, $installationId, $now, $config);
    if ($status === '') {
        return null;
    }
    if ($status === 'not_configured') {
        return array(
            'status' => 503,
            'error' => 'AI service is not configured on the server'
        );
    }
    return array(
        'status' => 401,
        'error' => 'App credential required'
    );
}
