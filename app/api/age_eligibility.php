<?php

require_once __DIR__ . '/age_eligibility_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_age_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_age_respond(404, array('error' => 'Not found'));
}

$remoteAddress = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR'])
    ? $_SERVER['REMOTE_ADDR']
    : '';
if (!dost_age_rate_limit_allows($remoteAddress, time())) {
    dost_age_respond(429, array('error' => 'Too many requests'));
}

$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 65536) {
    dost_age_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!is_array($request)
    || !isset($request['uid'], $request['eligible'], $request['nonce'], $request['integrity_token'])
    || !is_string($request['uid'])
    || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
    || !is_bool($request['eligible'])
    || !is_string($request['nonce'])
    || preg_match('/\A[a-f0-9]{64}\z/', $request['nonce']) !== 1
    || !is_string($request['integrity_token'])
    || strlen($request['integrity_token']) > 32768
) {
    dost_age_respond(400, array('error' => 'Invalid request'));
}

$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_age_respond(401, array('error' => 'Sign-in required'));
}
$idToken = $matches[1];

$config = array();
$configPath = __DIR__ . '/config.php';
if (is_file($configPath)) {
    $loadedConfig = require $configPath;
    if (is_array($loadedConfig)) {
        $config = $loadedConfig;
    }
}
$firebaseApiKey = getenv('DOST_FIREBASE_WEB_API_KEY');
if (!is_string($firebaseApiKey) || trim($firebaseApiKey) === '') {
    $firebaseApiKey = isset($config['firebase_web_api_key']) && is_string($config['firebase_web_api_key'])
        ? trim($config['firebase_web_api_key'])
        : '';
}
$firebaseProjectId = getenv('DOST_FIREBASE_PROJECT_ID');
if (!is_string($firebaseProjectId) || trim($firebaseProjectId) === '') {
    $firebaseProjectId = isset($config['firebase_project_id']) && is_string($config['firebase_project_id'])
        ? trim($config['firebase_project_id'])
        : '';
}
$serviceAccountPath = getenv('DOST_FIREBASE_SERVICE_ACCOUNT_PATH');
if (!is_string($serviceAccountPath) || trim($serviceAccountPath) === '') {
    $serviceAccountPath = isset($config['firebase_service_account_path']) && is_string($config['firebase_service_account_path'])
        ? trim($config['firebase_service_account_path'])
        : '';
}
if ($firebaseApiKey === '' || $firebaseProjectId === '' || $serviceAccountPath === ''
    || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
    error_log('Dost age eligibility service is not configured.');
    dost_age_respond(503, array('error' => 'Age eligibility service is not configured'));
}

$credentialContents = file_get_contents($serviceAccountPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials)
    || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $firebaseProjectId
) {
    error_log('Dost age eligibility service account is invalid or belongs to the wrong project.');
    dost_age_respond(503, array('error' => 'Age eligibility service is not configured'));
}

$userLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($firebaseApiKey),
    array(),
    array('idToken' => $idToken)
);
if ($userLookup === false) {
    error_log('Dost age eligibility Firebase identity lookup failed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
if ($userLookup['status'] !== 200 || !isset($userLookup['body']['users'][0]['localId'])
    || !is_string($userLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $userLookup['body']['users'][0]['localId'])
) {
    dost_age_respond(401, array('error' => 'Sign-in required'));
}

$serviceToken = dost_age_service_account_token($credentials);
if (!is_string($serviceToken)) {
    error_log('Dost age eligibility service account token exchange failed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
$integrityResponse = dost_age_http_json(
    'https://playintegrity.googleapis.com/v1/' . rawurlencode('com.snigtus.dost') . ':decodeIntegrityToken',
    array('Authorization: Bearer ' . $serviceToken),
    array('integrityToken' => $request['integrity_token'])
);
if ($integrityResponse === false || $integrityResponse['status'] !== 200) {
    error_log('Dost age eligibility Play Integrity decode failed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
if (!dost_age_integrity_allows(
    $integrityResponse['body'],
    $request['uid'],
    $request['eligible'],
    $request['nonce'],
    time()
)) {
    dost_age_respond(403, array('error' => 'Play integrity verification failed'));
}

$claimLock = dost_firebase_claim_lock($request['uid']);
if ($claimLock === false) {
    error_log('Dost age eligibility custom-claim lock could not be acquired.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
$adminLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($firebaseProjectId) . '/accounts:lookup',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => array($request['uid']))
);
if ($adminLookup === false || $adminLookup['status'] !== 200
    || !isset($adminLookup['body']['users'][0])
    || !is_array($adminLookup['body']['users'][0])
    || !isset($adminLookup['body']['users'][0]['localId'])
    || !is_string($adminLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $adminLookup['body']['users'][0]['localId'])
) {
    error_log('Dost age eligibility Admin SDK identity lookup failed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
$existingAttributes = '{}';
if (isset($adminLookup['body']['users'][0]['customAttributes'])) {
    if (!is_string($adminLookup['body']['users'][0]['customAttributes'])) {
        error_log('Dost age eligibility custom claims could not be read.');
        dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
    }
    $existingAttributes = trim($adminLookup['body']['users'][0]['customAttributes']);
    if ($existingAttributes === '') {
        $existingAttributes = '{}';
    }
}
$customClaims = json_decode($existingAttributes, true);
if (!is_array($customClaims)) {
    error_log('Dost age eligibility custom claims are malformed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
$expiresAt = time() + 21600;
$customClaims['dostAgeEligible'] = $request['eligible'];
$customClaims['dostAgeEligibleUntil'] = $expiresAt;
$customAttributes = json_encode($customClaims, JSON_UNESCAPED_SLASHES);
if (!is_string($customAttributes)) {
    error_log('Dost age eligibility custom claims could not be encoded.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}

$claimUpdate = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($firebaseProjectId) . '/accounts:update',
    array('Authorization: Bearer ' . $serviceToken),
    array(
        'localId' => $request['uid'],
        'customAttributes' => $customAttributes
    )
);
if ($claimUpdate === false || $claimUpdate['status'] !== 200) {
    error_log('Dost age eligibility custom claim update failed.');
    dost_age_respond(503, array('error' => 'Age eligibility service is unavailable'));
}
dost_firebase_claim_unlock($claimLock);

dost_age_respond(200, array(
    'eligible' => $request['eligible'],
    'eligible_until' => $expiresAt
));
