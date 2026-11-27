<?php

require_once __DIR__ . '/premium_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_premium_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_premium_respond(404, array('error' => 'Not found'));
}
$remoteAddress = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR'])
    ? $_SERVER['REMOTE_ADDR']
    : '';
if (!dost_premium_rate_limit_allows($remoteAddress, time())) {
    dost_premium_respond(429, array('error' => 'Too many verification requests'));
}

$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 32768) {
    dost_premium_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!is_array($request)
    || !isset($request['uid'], $request['product_id'], $request['purchase_token'])
    || !is_string($request['uid'])
    || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $request['uid']) !== 1
    || !is_string($request['product_id'])
    || strlen($request['product_id']) > 128
    || !is_string($request['purchase_token'])
    || $request['purchase_token'] === ''
    || strlen($request['purchase_token']) > 16384
) {
    dost_premium_respond(400, array('error' => 'Invalid purchase verification request'));
}
$purchaseType = dost_premium_product_type($request['product_id']);
if ($purchaseType === false) {
    dost_premium_respond(400, array('error' => 'Unrecognized Premium product'));
}

$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
        : '');
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_premium_respond(401, array('error' => 'Sign-in required'));
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
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$apiKey = $setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key');
$serviceAccountPath = $setting('DOST_PREMIUM_SERVICE_ACCOUNT_PATH', 'premium_service_account_path');
$packageName = $setting('DOST_PLAY_PACKAGE_NAME', 'play_package_name');
$premiumEnabled = strtolower($setting('DOST_PREMIUM_ENABLED', 'premium_enabled'));
if (!in_array($premiumEnabled, array('true', 'false'), true)) {
    error_log('Dost Premium enabled setting must be true or false.');
    dost_premium_respond(503, array('error' => 'Premium verification is not configured'));
}
if ($premiumEnabled !== 'true') {
    dost_premium_respond(503, array('error' => 'Premium purchases are not enabled'));
}
if ($projectId === '' || $apiKey === '' || $serviceAccountPath === ''
    || $packageName !== 'com.snigtus.dost'
    || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
) {
    error_log('Dost Premium verification is not configured.');
    dost_premium_respond(503, array('error' => 'Premium verification is not configured'));
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
    error_log('Dost Premium service account must be outside the web root.');
    dost_premium_respond(503, array('error' => 'Premium verification is not configured'));
}
$credentialContents = file_get_contents($realPath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials) || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email']) || !is_string($credentials['private_key'])
) {
    error_log('Dost Premium service account is invalid or belongs to the wrong project.');
    dost_premium_respond(503, array('error' => 'Premium verification is not configured'));
}

$firebaseLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
    array(),
    array('idToken' => $idToken)
);
if ($firebaseLookup === false) {
    error_log('Dost Premium Firebase identity lookup failed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
if ($firebaseLookup['status'] !== 200 || !isset($firebaseLookup['body']['users'][0]['localId'])
    || !is_string($firebaseLookup['body']['users'][0]['localId'])
    || !hash_equals($request['uid'], $firebaseLookup['body']['users'][0]['localId'])
) {
    dost_premium_respond(401, array('error' => 'Sign-in required'));
}

$serviceToken = dost_premium_service_account_token($credentials);
if (!is_string($serviceToken)) {
    error_log('Dost Premium service-account token exchange failed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
$purchasePath = $purchaseType === 'subscription'
    ? '/purchases/subscriptionsv2/tokens/'
    : '/purchases/productsv2/tokens/';
$purchaseResponse = dost_premium_http_json(
    'GET',
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
        . rawurlencode($packageName) . $purchasePath . rawurlencode($request['purchase_token']),
    array('Authorization: Bearer ' . $serviceToken)
);
if ($purchaseResponse === false) {
    error_log('Dost Premium Google Play purchase lookup failed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
if ($purchaseResponse['status'] !== 200) {
    dost_premium_respond(403, array('error' => 'Google Play did not confirm this purchase'));
}
$entitlement = dost_premium_purchase_entitlement(
    $request['product_id'],
    $purchaseType,
    $purchaseResponse['body'],
    $request['uid'],
    time()
);
if ($entitlement === false) {
    dost_premium_respond(403, array('error' => 'This purchase is not active for this account'));
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
    error_log('Dost Premium custom-claim lookup failed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
$existingAttributes = '{}';
if (array_key_exists('customAttributes', $adminLookup['body']['users'][0])) {
    if (!is_string($adminLookup['body']['users'][0]['customAttributes'])) {
        error_log('Dost Premium custom claims are malformed.');
        dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
    }
    $existingAttributes = trim($adminLookup['body']['users'][0]['customAttributes']);
}
$customClaims = json_decode($existingAttributes === '' ? '{}' : $existingAttributes, true);
if (!is_array($customClaims)) {
    error_log('Dost Premium custom claims are malformed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
$updatedClaims = dost_premium_claims($customClaims, $request['product_id'], $entitlement, time());
$customAttributes = is_array($updatedClaims)
    ? json_encode($updatedClaims, JSON_UNESCAPED_SLASHES)
    : false;
if (!is_string($customAttributes) || strlen($customAttributes) > 1000) {
    error_log('Dost Premium custom claims could not be encoded.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}
$claimUpdate = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:update',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => $request['uid'], 'customAttributes' => $customAttributes)
);
if ($claimUpdate === false || $claimUpdate['status'] !== 200) {
    error_log('Dost Premium custom-claim update failed.');
    dost_premium_respond(503, array('error' => 'Premium verification is unavailable'));
}

dost_premium_respond(200, array(
    'premium' => true,
    'lifetime' => !empty($updatedClaims['dostPremiumLifetime']),
    'premium_until' => isset($updatedClaims['dostPremiumUntil']) ? (int) $updatedClaims['dostPremiumUntil'] : null
));
