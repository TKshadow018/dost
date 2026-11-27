<?php

require_once __DIR__ . '/premium_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_shield_sync_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_shield_sync_respond(404, array('error' => 'Not found'));
}
$remoteAddress = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR'])
    ? $_SERVER['REMOTE_ADDR']
    : '';
if (!dost_premium_rate_limit_allows($remoteAddress, time(), 10)) {
    dost_shield_sync_respond(429, array('error' => 'Too many requests'));
}

$rawBody = file_get_contents('php://input');
$request = is_string($rawBody) && strlen($rawBody) <= 8192 ? json_decode($rawBody, true) : null;
$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (!is_array($request)
    || !isset($request['uid'], $request['org_id'])
    || !is_string($request['uid'])
    || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
    || !is_string($request['org_id'])
    || preg_match('/\A[A-Za-z0-9_-]{1,128}\z/', $request['org_id']) !== 1
    || preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1
    || strlen($matches[1]) > 8192
) {
    dost_shield_sync_respond(400, array('error' => 'Invalid request'));
}

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
    return isset($config[$configKey]) && is_string($config[$configKey]) ? trim($config[$configKey]) : '';
};
$shieldEnabled = strtolower($setting('DOST_PLAN_SHIELD_ENABLED', 'plan_shield_enabled'));
if (!in_array($shieldEnabled, array('true', 'false'), true)) {
    error_log('Dost Shield plan sync switch must be true or false.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
}
if ($shieldEnabled !== 'true') {
    dost_shield_sync_respond(503, array('error' => 'Shield plans are not enabled'));
}
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$apiKey = $setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key');
$serviceAccountPath = $setting('DOST_PREMIUM_SERVICE_ACCOUNT_PATH', 'premium_service_account_path');
if ($projectId === '' || $apiKey === '' || $serviceAccountPath === ''
    || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
    error_log('Dost Shield plan sync is not configured.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
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
    error_log('Dost Shield service account must be outside the web root.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
}
$credentialContents = file_get_contents($realPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials)
    || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email'])
    || !is_string($credentials['private_key'])
) {
    error_log('Dost Shield service account is invalid or belongs to the wrong project.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
}

$idToken = $matches[1];
$identity = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
    array(),
    array('idToken' => $idToken)
);
if ($identity === false) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
if ($identity['status'] !== 200
    || !isset($identity['body']['users'][0]['localId'])
    || !is_string($identity['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $identity['body']['users'][0]['localId'])
) {
    dost_shield_sync_respond(401, array('error' => 'Sign-in required'));
}

$serviceToken = dost_premium_service_account_token(
    $credentials,
    null,
    'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/identitytoolkit'
);
if (!is_string($serviceToken)) {
    error_log('Dost Shield service-account token exchange failed.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
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
    || !isset($adminLookup['body']['users'][0]['customAttributes'])
    || !is_string($adminLookup['body']['users'][0]['customAttributes'])
) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
$ownerClaims = json_decode($adminLookup['body']['users'][0]['customAttributes'], true);
$now = time();
$plan = dost_shield_plan_from_claims($ownerClaims, $now);
if (!is_array($plan) || $plan['activeUntil'] <= $now
    || !isset($ownerClaims['dostAgeEligible'], $ownerClaims['dostAgeEligibleUntil'], $ownerClaims['dostAgeCohort'])
    || $ownerClaims['dostAgeEligible'] !== true
    || !is_numeric($ownerClaims['dostAgeEligibleUntil'])
    || (int) $ownerClaims['dostAgeEligibleUntil'] <= $now
    || $ownerClaims['dostAgeCohort'] !== 'adult'
) {
    dost_shield_sync_respond(403, array('error' => 'An active adult Shield owner plan is required'));
}

$firestoreRoot = 'https://firestore.googleapis.com/v1/projects/' . rawurlencode($projectId)
    . '/databases/(default)/documents';
$headers = array('Authorization: Bearer ' . $serviceToken);
$remoteConfig = dost_premium_http_json('GET', $firestoreRoot . '/secureOrgConfig/superSecure', $headers);
$organization = dost_premium_http_json(
    'GET',
    $firestoreRoot . '/secureOrgs/' . rawurlencode($request['org_id']),
    $headers
);
if ($remoteConfig === false || $organization === false) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
if ($remoteConfig['status'] !== 200
    || !isset($remoteConfig['body']['fields']['enabled']['booleanValue'])
    || $remoteConfig['body']['fields']['enabled']['booleanValue'] !== true
) {
    dost_shield_sync_respond(403, array('error' => 'Shield is disabled'));
}
if ($organization['status'] !== 200
    || !isset($organization['body']['fields']['ownerUid']['stringValue'])
    || !is_string($organization['body']['fields']['ownerUid']['stringValue'])
    || !hash_equals($request['uid'], $organization['body']['fields']['ownerUid']['stringValue'])
) {
    dost_shield_sync_respond(403, array('error' => 'Only the organization owner can sync its plan'));
}

$activeUntil = gmdate('Y-m-d\TH:i:s\Z', $plan['activeUntil']);
$write = array(
    'writes' => array(array(
        'update' => array(
            'name' => $firestoreRoot . '/secureOrgs/' . rawurlencode($request['org_id']),
            'fields' => array(
                'plan' => array('mapValue' => array('fields' => array(
                    'seatLimit' => array('integerValue' => (string) $plan['seatLimit']),
                    'activeUntil' => array('timestampValue' => $activeUntil)
                )))
            )
        ),
        'updateMask' => array('fieldPaths' => array('plan')),
        'currentDocument' => array('exists' => true)
    ))
);
$writeResult = dost_premium_http_json('POST', $firestoreRoot . ':commit', $headers, $write);
if ($writeResult === false || $writeResult['status'] !== 200) {
    error_log('Dost Shield plan document update failed.');
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}

dost_shield_sync_respond(200, array(
    'ok' => true,
    'seat_limit' => $plan['seatLimit'],
    'active_until' => $plan['activeUntil']
));
