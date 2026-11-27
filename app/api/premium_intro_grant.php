<?php

require_once __DIR__ . '/premium_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_premium_intro_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_premium_intro_respond(404, array('error' => 'Not found'));
}
$remoteAddress = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR'])
    ? $_SERVER['REMOTE_ADDR']
    : '';
if (!dost_premium_rate_limit_allows($remoteAddress, time())) {
    dost_premium_intro_respond(429, array('error' => 'Too many Premium requests'));
}

$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 32768) {
    dost_premium_intro_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!is_array($request)
    || !isset($request['uid'])
    || !is_string($request['uid'])
    || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
) {
    dost_premium_intro_respond(400, array('error' => 'Invalid intro grant request'));
}

$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_premium_intro_respond(401, array('error' => 'Sign-in required'));
}
$idToken = $matches[1];

$config = array();
$configPath = __DIR__ . '/config.php';
if (is_file($configPath)) {
    $loaded = require $configPath;
    if (is_array($loaded)) {
        $config = $loaded;
    }
}
$setting = function ($environmentKey, $configKey) use ($config) {
    $value = getenv($environmentKey);
    if (is_string($value) && trim($value) !== '') {
        return trim($value);
    }
    if (isset($config[$configKey]) && is_bool($config[$configKey])) {
        return $config[$configKey] ? 'true' : 'false';
    }
    return isset($config[$configKey]) && (is_string($config[$configKey]) || is_numeric($config[$configKey]))
        ? trim((string) $config[$configKey])
        : '';
};
$enabled = strtolower($setting('DOST_INTRO_PREMIUM_ENABLED', 'intro_premium_enabled'));
if (!in_array($enabled, array('true', 'false'), true)) {
    error_log('Dost intro Premium enabled setting must be true or false.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
}
if ($enabled !== 'true') {
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not enabled'));
}
$daysSetting = $setting('DOST_INTRO_PREMIUM_DAYS', 'intro_premium_days');
$daysSetting = $daysSetting === '' ? '90' : $daysSetting;
if (preg_match('/\A[1-9][0-9]{0,3}\z/', $daysSetting) !== 1 || (int) $daysSetting > 3650) {
    error_log('Dost intro Premium days setting is invalid.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
}
$days = (int) $daysSetting;
$launchAtSetting = $setting('DOST_INTRO_PREMIUM_LAUNCH_AT', 'intro_premium_launch_at');
$launchAt = null;
if ($launchAtSetting !== '') {
    $launchAt = dost_premium_account_creation_epoch($launchAtSetting);
    if ($launchAt === false) {
        error_log('Dost intro Premium launch time is invalid.');
        dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
    }
}
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$apiKey = $setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key');
$serviceAccountPath = $setting('DOST_PREMIUM_SERVICE_ACCOUNT_PATH', 'premium_service_account_path');
if ($serviceAccountPath === '') {
    $serviceAccountPath = $setting('DOST_FIREBASE_SERVICE_ACCOUNT_PATH', 'firebase_service_account_path');
}
if ($projectId === '' || $apiKey === '' || $serviceAccountPath === ''
    || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
    error_log('Dost Premium intro grant is not configured.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
}
$realPath = realpath($serviceAccountPath);
$documentRoot = isset($_SERVER['DOCUMENT_ROOT']) && is_string($_SERVER['DOCUMENT_ROOT'])
    ? realpath($_SERVER['DOCUMENT_ROOT'])
    : false;
if (!is_string($realPath)
    || ($documentRoot !== false
        && ($realPath === $documentRoot
            || strpos($realPath, rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR) === 0))
) {
    error_log('Dost Premium intro service account must be outside the web root.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
}
$credentialContents = file_get_contents($realPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials) || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email']) || !is_string($credentials['private_key'])
) {
    error_log('Dost Premium intro service account is invalid or belongs to the wrong project.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are not configured'));
}

$firebaseLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
    array(),
    array('idToken' => $idToken)
);
if ($firebaseLookup === false) {
    error_log('Dost Premium intro Firebase identity lookup failed.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
if ($firebaseLookup['status'] !== 200 || !isset($firebaseLookup['body']['users'][0]['localId'])
    || !is_string($firebaseLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $firebaseLookup['body']['users'][0]['localId'])
) {
    dost_premium_intro_respond(401, array('error' => 'Sign-in required'));
}

$serviceToken = dost_premium_service_account_token(
    $credentials,
    null,
    'https://www.googleapis.com/auth/identitytoolkit'
);
if (!is_string($serviceToken)) {
    error_log('Dost Premium intro service-account token exchange failed.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
$claimLock = dost_firebase_claim_lock($request['uid']);
if ($claimLock === false) {
    error_log('Dost Premium intro custom-claim lock could not be acquired.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
$adminLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:lookup',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => array($request['uid']))
);
if ($adminLookup === false || $adminLookup['status'] !== 200
    || !isset($adminLookup['body']['users'][0]['localId'])
    || !is_string($adminLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $adminLookup['body']['users'][0]['localId'])
) {
    error_log('Dost Premium intro custom-claim lookup failed.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
$user = $adminLookup['body']['users'][0];
$createdAt = isset($user['createdAt']) ? dost_premium_account_creation_epoch($user['createdAt']) : false;
if (!is_int($createdAt)) {
    error_log('Dost Premium intro account creation time is missing or invalid.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
$existingAttributes = '{}';
if (array_key_exists('customAttributes', $user)) {
    if (!is_string($user['customAttributes'])) {
        error_log('Dost Premium intro custom claims are malformed.');
        dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
    }
    $existingAttributes = trim($user['customAttributes']);
}
$customClaims = json_decode($existingAttributes === '' ? '{}' : $existingAttributes, true);
if (!is_array($customClaims)) {
    error_log('Dost Premium intro custom claims are malformed.');
    dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
}
$now = time();
if (!dost_age_eligibility_claim_active($customClaims, $now)) {
    dost_premium_intro_respond(403, array('error' => 'Explore Plus is available only to age-eligible adults'));
}
if (empty($customClaims['dostPremiumIntroGranted'])) {
    $updatedClaims = dost_premium_intro_claims($customClaims, $createdAt, $days, $now, $launchAt);
    $customAttributes = is_array($updatedClaims)
        ? json_encode($updatedClaims, JSON_UNESCAPED_SLASHES)
        : false;
    if (!is_string($customAttributes) || strlen($customAttributes) > 1000) {
        error_log('Dost Premium intro custom claims could not be encoded.');
        dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
    }
    $claimUpdate = dost_age_http_json(
        'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:update',
        array('Authorization: Bearer ' . $serviceToken),
        array('localId' => $request['uid'], 'customAttributes' => $customAttributes)
    );
    if ($claimUpdate === false || $claimUpdate['status'] !== 200) {
        error_log('Dost Premium intro custom-claim update failed.');
        dost_premium_intro_respond(503, array('error' => 'Premium intro grants are unavailable'));
    }
} else {
    $updatedClaims = $customClaims;
}

dost_firebase_claim_unlock($claimLock);
dost_premium_intro_respond(200, dost_premium_intro_response($updatedClaims, time()));
