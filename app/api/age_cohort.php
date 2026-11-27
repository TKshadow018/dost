<?php

require_once __DIR__ . '/age_eligibility_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_age_cohort_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_age_cohort_respond(404, array('error' => 'Not found'));
}
$remoteAddress = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR'])
    ? $_SERVER['REMOTE_ADDR']
    : '';
if (!dost_age_rate_limit_allows($remoteAddress, time())) {
    dost_age_cohort_respond(429, array('error' => 'Too many requests'));
}
$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 65536) {
    dost_age_cohort_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!is_array($request)
    || !isset($request['uid'], $request['birth_month'], $request['birth_year'])
    || !is_string($request['uid'])
    || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
    || !is_int($request['birth_month'])
    || !is_int($request['birth_year'])
) {
    dost_age_cohort_respond(400, array('error' => 'Invalid request'));
}
$age = dost_age_from_birth_month_year(
    $request['birth_month'],
    $request['birth_year'],
    (int) gmdate('n'),
    (int) gmdate('Y')
);
if ($age === false || $age < 13) {
    dost_age_cohort_respond(403, array('error' => 'Dost is for ages 13 and older'));
}
$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_age_cohort_respond(401, array('error' => 'Sign-in required'));
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
    return isset($config[$configKey]) && is_string($config[$configKey]) ? trim($config[$configKey]) : '';
};
$apiKey = $setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key');
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$serviceAccountPath = $setting('DOST_FIREBASE_SERVICE_ACCOUNT_PATH', 'firebase_service_account_path');
if ($apiKey === '' || $projectId === '' || $serviceAccountPath === ''
    || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
    error_log('Dost age-cohort service is not configured.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is not configured'));
}
$resolvedPath = realpath($serviceAccountPath);
$documentRoot = isset($_SERVER['DOCUMENT_ROOT']) && is_string($_SERVER['DOCUMENT_ROOT'])
    ? realpath($_SERVER['DOCUMENT_ROOT'])
    : false;
if (!is_string($resolvedPath)
    || ($documentRoot !== false
        && ($resolvedPath === $documentRoot
            || strpos($resolvedPath, rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR) === 0))
) {
    error_log('Dost age-cohort service account must be outside the web root.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is not configured'));
}
$credentialContents = file_get_contents($resolvedPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials)
    || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email']) || !is_string($credentials['private_key'])
) {
    error_log('Dost age-cohort service account is invalid or belongs to the wrong project.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is not configured'));
}
$userLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
    array(),
    array('idToken' => $idToken)
);
if ($userLookup === false) {
    error_log('Dost age-cohort Firebase identity lookup failed.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
}
if ($userLookup['status'] !== 200 || !isset($userLookup['body']['users'][0]['localId'])
    || !is_string($userLookup['body']['users'][0]['localId'])
    || !dost_age_authenticated_uid_matches($request['uid'], $userLookup['body']['users'][0]['localId'])
) {
    dost_age_cohort_respond(401, array('error' => 'Sign-in required'));
}
$serviceToken = dost_age_service_account_token($credentials);
if (!is_string($serviceToken)) {
    error_log('Dost age-cohort service-account token exchange failed.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
}
$claimLock = dost_firebase_claim_lock($request['uid']);
if ($claimLock === false) {
    error_log('Dost age-cohort custom-claim lock could not be acquired.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
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
    dost_firebase_claim_unlock($claimLock);
    error_log('Dost age-cohort custom-claim lookup failed.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
}
$customClaims = array();
if (array_key_exists('customAttributes', $adminLookup['body']['users'][0])) {
    if (!is_string($adminLookup['body']['users'][0]['customAttributes'])) {
        dost_firebase_claim_unlock($claimLock);
        error_log('Dost age-cohort custom claims are malformed.');
        dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
    }
    $customClaims = json_decode($adminLookup['body']['users'][0]['customAttributes'], true);
    if (!is_array($customClaims)) {
        dost_firebase_claim_unlock($claimLock);
        error_log('Dost age-cohort custom claims are malformed.');
        dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
    }
}
$updatedClaims = dost_age_cohort_claims(
    $customClaims,
    $request['birth_month'],
    $request['birth_year'],
    (int) gmdate('n'),
    (int) gmdate('Y')
);
$customAttributes = is_array($updatedClaims)
    ? json_encode($updatedClaims, JSON_UNESCAPED_SLASHES)
    : false;
if (!is_string($customAttributes) || strlen($customAttributes) > 1000) {
    dost_firebase_claim_unlock($claimLock);
    dost_age_cohort_respond(403, array('error' => 'The stored birth date cannot be changed'));
}
$claimUpdate = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:update',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => $request['uid'], 'customAttributes' => $customAttributes)
);
dost_firebase_claim_unlock($claimLock);
if ($claimUpdate === false || $claimUpdate['status'] !== 200) {
    error_log('Dost age-cohort custom claim update failed.');
    dost_age_cohort_respond(503, array('error' => 'Age-cohort service is unavailable'));
}
dost_age_cohort_respond(200, array('cohort' => $updatedClaims['dostAgeCohort']));
