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
if (!dost_premium_rate_limit_allows($remoteAddress, time())) {
    dost_shield_sync_respond(429, array('error' => 'Too many requests'));
}
$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 8192) {
    dost_shield_sync_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!is_array($request) || !isset($request['uid'], $request['org_id'])
    || !is_string($request['uid']) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
    || !is_string($request['org_id']) || preg_match('/\A[A-Za-z0-9_-]{1,128}\z/', $request['org_id']) !== 1
) {
    dost_shield_sync_respond(400, array('error' => 'Invalid organization plan request'));
}
$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_shield_sync_respond(401, array('error' => 'Sign-in required'));
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
    return isset($config[$configKey]) && (is_string($config[$configKey]) || is_numeric($config[$configKey]))
        ? trim((string) $config[$configKey])
        : '';
};
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$apiKey = $setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key');
$serviceAccountPath = $setting('DOST_PREMIUM_SERVICE_ACCOUNT_PATH', 'premium_service_account_path');
$packageName = $setting('DOST_PLAY_PACKAGE_NAME', 'play_package_name');
if ($projectId === '' || $apiKey === '' || $serviceAccountPath === ''
    || $packageName !== 'com.snigtus.dost' || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
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
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
}
$credentialContents = file_get_contents($realPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials) || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email']) || !is_string($credentials['private_key'])
) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is not configured'));
}
$firebaseLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
    array(),
    array('idToken' => $matches[1])
);
if ($firebaseLookup === false) {
    dost_shield_sync_respond(503, array('error' => 'Firebase identity lookup is unavailable'));
}
if ($firebaseLookup['status'] !== 200 || !isset($firebaseLookup['body']['users'][0]['localId'])
    || !is_string($firebaseLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $firebaseLookup['body']['users'][0]['localId'])
) {
    dost_shield_sync_respond(401, array('error' => 'Sign-in required'));
}
$serviceToken = dost_premium_service_account_token(
    $credentials,
    null,
    'https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/datastore'
);
if (!is_string($serviceToken)) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
$claimLock = dost_firebase_claim_lock($request['uid']);
if ($claimLock === false) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
$adminLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:lookup',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => array($request['uid']))
);
if ($adminLookup === false || $adminLookup['status'] !== 200
    || !isset($adminLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $adminLookup['body']['users'][0]['localId'])
) {
    dost_shield_sync_respond(503, array('error' => 'Shield plan sync is unavailable'));
}
$user = $adminLookup['body']['users'][0];
$attributes = isset($user['customAttributes']) && is_string($user['customAttributes'])
    ? $user['customAttributes']
    : '{}';
$claims = json_decode($attributes === '' ? '{}' : $attributes, true);
$now = time();
$plan = dost_premium_shield_plan_fields(is_array($claims) ? $claims : array(), $now);
if ($plan === false) {
    dost_firebase_claim_unlock($claimLock);
    dost_shield_sync_respond(403, array('error' => 'An active Shield owner plan is required'));
}
$orgUrl = 'https://firestore.googleapis.com/v1/projects/' . rawurlencode($projectId)
    . '/databases/(default)/documents/secureOrgs/' . rawurlencode($request['org_id']);
$organization = dost_premium_http_json('GET', $orgUrl, array('Authorization: Bearer ' . $serviceToken));
if ($organization === false || $organization['status'] !== 200
    || !isset($organization['body']['fields']['ownerUid']['stringValue'])
    || !is_string($organization['body']['fields']['ownerUid']['stringValue'])
) {
    dost_firebase_claim_unlock($claimLock);
    dost_shield_sync_respond(404, array('error' => 'Organization not found'));
}
if (!hash_equals($request['uid'], $organization['body']['fields']['ownerUid']['stringValue'])) {
    dost_firebase_claim_unlock($claimLock);
    dost_shield_sync_respond(403, array('error' => 'Only the organization owner can sync its plan'));
}
$payload = array('fields' => array(
    'plan' => array('mapValue' => array('fields' => array(
        'seatLimit' => array('integerValue' => (string) $plan['seatLimit']),
        'activeUntil' => array('timestampValue' => $plan['activeUntil'])
    )))
));
$updated = dost_premium_http_json(
    'PATCH',
    $orgUrl . '?updateMask.fieldPaths=plan',
    array('Authorization: Bearer ' . $serviceToken),
    $payload
);
if ($updated === false || !in_array($updated['status'], array(200, 201), true)) {
    dost_firebase_claim_unlock($claimLock);
    dost_shield_sync_respond(503, array('error' => 'Shield plan could not be updated'));
}
dost_firebase_claim_unlock($claimLock);
dost_shield_sync_respond(200, array('ok' => true, 'plan' => $plan));
