<?php

require_once __DIR__ . '/premium_support.php';

header('Content-Type: application/json; charset=utf-8');

function dost_rtdn_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_rtdn_respond(404, array('error' => 'Not found'));
}
$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 32768) {
    dost_rtdn_respond(400, array('error' => 'Invalid notification'));
}
$request = json_decode($rawBody, true);
if (!is_array($request) || !isset($request['message']['data']) || !is_string($request['message']['data'])) {
    dost_rtdn_respond(400, array('error' => 'Invalid notification'));
}
$authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
    ? trim($_SERVER['HTTP_AUTHORIZATION'])
    : '';
if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1 || strlen($matches[1]) > 8192) {
    dost_rtdn_respond(401, array('error' => 'Unauthorized notification'));
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
$pushAudience = $setting('DOST_PLAY_RTDN_AUDIENCE', 'play_rtdn_audience');
$pushEmail = $setting('DOST_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL', 'play_rtdn_service_account_email');
if ($pushAudience === '' || $pushEmail === '') {
    error_log('Dost Play RTDN push identity is not configured.');
    dost_rtdn_respond(503, array('error' => 'Notification endpoint is not configured'));
}
$tokenInfo = dost_premium_http_json(
    'GET',
    'https://oauth2.googleapis.com/tokeninfo?id_token=' . rawurlencode($matches[1])
);
if ($tokenInfo === false || $tokenInfo['status'] !== 200
    || !dost_premium_rtdn_token_info_is_valid($tokenInfo['body'], $pushAudience, $pushEmail)
) {
    dost_rtdn_respond(401, array('error' => 'Unauthorized notification'));
}
$encodedEvent = $request['message']['data'];
$eventJson = base64_decode($encodedEvent, true);
$event = is_string($eventJson) ? json_decode($eventJson, true) : null;
$packageName = $setting('DOST_PLAY_PACKAGE_NAME', 'play_package_name');
if (!is_array($event) || !isset($event['packageName'])
    || !is_string($event['packageName']) || $event['packageName'] !== $packageName
) {
    dost_rtdn_respond(400, array('error' => 'Invalid Play notification'));
}
if (isset($event['testNotification'])) {
    dost_rtdn_respond(200, array('ok' => true));
}
$eventTimeMillis = isset($event['eventTimeMillis']) && is_numeric($event['eventTimeMillis'])
    ? (int) $event['eventTimeMillis']
    : 0;
$eventTime = $eventTimeMillis > 0 ? (int) floor($eventTimeMillis / 1000) : null;
$projectId = $setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id');
$serviceAccountPath = $setting('DOST_PREMIUM_SERVICE_ACCOUNT_PATH', 'premium_service_account_path');
$purchaseMapDirectory = $setting('DOST_PREMIUM_PURCHASE_MAP_DIR', 'premium_purchase_map_dir');
if ($purchaseMapDirectory === '') {
    $purchaseMapDirectory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR)
        . DIRECTORY_SEPARATOR . 'dost-premium-purchase-map';
}
$documentRoot = isset($_SERVER['DOCUMENT_ROOT']) && is_string($_SERVER['DOCUMENT_ROOT'])
    ? realpath($_SERVER['DOCUMENT_ROOT'])
    : false;
$realServicePath = $serviceAccountPath !== '' ? realpath($serviceAccountPath) : false;
$realMapPath = realpath($purchaseMapDirectory);
if ($projectId === '' || $packageName !== 'com.snigtus.dost'
    || !is_string($realServicePath) || !is_readable($realServicePath)
    || !is_string($realMapPath) || !is_readable($realMapPath)
    || ($documentRoot !== false
        && (strpos($realServicePath, rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR) === 0
            || strpos($realMapPath, rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR) === 0))
) {
    error_log('Dost Play RTDN is not configured.');
    dost_rtdn_respond(503, array('error' => 'Notification endpoint is not configured'));
}
$credentialContents = file_get_contents($realServicePath);
$credentials = is_string($credentialContents) ? json_decode($credentialContents, true) : null;
if (!is_array($credentials) || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
    || $credentials['project_id'] !== $projectId
    || !is_string($credentials['client_email']) || !is_string($credentials['private_key'])
) {
    error_log('Dost Play RTDN service account is invalid.');
    dost_rtdn_respond(503, array('error' => 'Notification endpoint is not configured'));
}
$notification = false;
$purchaseToken = '';
$productId = '';
$purchaseType = '';
$forceRevoke = false;
if (isset($event['subscriptionNotification']) && is_array($event['subscriptionNotification'])) {
    $notification = $event['subscriptionNotification'];
    $purchaseType = 'subscription';
    $productId = isset($notification['subscriptionId']) ? $notification['subscriptionId'] : '';
    $purchaseToken = isset($notification['purchaseToken']) ? $notification['purchaseToken'] : '';
    $forceRevoke = isset($notification['notificationType'])
        && in_array((int) $notification['notificationType'], array(12, 13), true);
} elseif (isset($event['oneTimeProductNotification']) && is_array($event['oneTimeProductNotification'])) {
    $notification = $event['oneTimeProductNotification'];
    $purchaseType = 'inapp';
    $productId = isset($notification['sku']) ? $notification['sku'] : '';
    $purchaseToken = isset($notification['purchaseToken']) ? $notification['purchaseToken'] : '';
    $forceRevoke = isset($notification['notificationType'])
        && (int) $notification['notificationType'] === 2;
} elseif (isset($event['voidedPurchaseNotification']) && is_array($event['voidedPurchaseNotification'])) {
    $notification = $event['voidedPurchaseNotification'];
    $productId = isset($notification['productId']) ? $notification['productId'] : '';
    $purchaseToken = isset($notification['purchaseToken']) ? $notification['purchaseToken'] : '';
    $purchaseType = dost_premium_product_type($productId);
    $forceRevoke = true;
} else {
    dost_rtdn_respond(200, array('ok' => true));
}
if (!is_string($productId) || !is_array($product = dost_premium_product($productId))
    || !is_string($purchaseToken) || $purchaseToken === '' || strlen($purchaseToken) > 16384
) {
    dost_rtdn_respond(400, array('error' => 'Invalid Play notification'));
}
$owner = dost_premium_purchase_owner($purchaseToken, $realMapPath);
if ($owner === false || $owner['product_id'] !== $productId) {
    error_log('Dost Play RTDN has no matching verified purchase owner.');
    dost_rtdn_respond(200, array('ok' => true));
}
$forceRevoke = $forceRevoke || !empty($owner['refund_required']);
$serviceToken = dost_premium_service_account_token($credentials);
if (!is_string($serviceToken)) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
$purchasePath = $purchaseType === 'subscription'
    ? '/purchases/subscriptionsv2/tokens/'
    : '/purchases/productsv2/tokens/';
$purchaseResponse = dost_premium_http_json(
    'GET',
    'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'
        . rawurlencode($packageName) . $purchasePath . rawurlencode($purchaseToken),
    array('Authorization: Bearer ' . $serviceToken)
);
if ($purchaseResponse === false || $purchaseResponse['status'] !== 200) {
    dost_rtdn_respond(503, array('error' => 'Play purchase lookup is unavailable'));
}
$externalIds = isset($purchaseResponse['body']['externalAccountIdentifiers'])
    && is_array($purchaseResponse['body']['externalAccountIdentifiers'])
    ? $purchaseResponse['body']['externalAccountIdentifiers']
    : array();
$externalAccountId = isset($externalIds['obfuscatedExternalAccountId'])
    ? $externalIds['obfuscatedExternalAccountId']
    : (isset($purchaseResponse['body']['obfuscatedExternalAccountId'])
        ? $purchaseResponse['body']['obfuscatedExternalAccountId'] : '');
if (!is_string($externalAccountId)
    || !hash_equals(dost_premium_obfuscated_account_id($owner['uid']), $externalAccountId)
) {
    error_log('Dost Play RTDN account binding did not match the stored purchase owner.');
    dost_rtdn_respond(200, array('ok' => true));
}
$entitlement = dost_premium_purchase_entitlement(
    $productId,
    $purchaseType,
    $purchaseResponse['body'],
    $owner['uid'],
    time()
);
$serviceToken = dost_premium_service_account_token(
    $credentials,
    null,
    'https://www.googleapis.com/auth/identitytoolkit'
);
if (!is_string($serviceToken)) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
$claimLock = dost_firebase_claim_lock($owner['uid']);
if ($claimLock === false) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
$adminLookup = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:lookup',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => array($owner['uid']))
);
if ($adminLookup === false || $adminLookup['status'] !== 200
    || !isset($adminLookup['body']['users'][0]['localId'])
    || !hash_equals($owner['uid'], $adminLookup['body']['users'][0]['localId'])
) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
$user = $adminLookup['body']['users'][0];
$claims = isset($user['customAttributes']) && is_string($user['customAttributes'])
    ? json_decode($user['customAttributes'], true)
    : array();
if (!is_array($claims)) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
if ($entitlement !== false && !$forceRevoke) {
    $updatedClaims = dost_premium_claims(
        $claims,
        $productId,
        $entitlement,
        time(),
        $purchaseToken,
        $eventTime,
        false
    );
} else {
    $updatedClaims = dost_premium_revoke_claims($claims, $productId, time(), $purchaseToken, $eventTime);
}
$customAttributes = is_array($updatedClaims)
    ? json_encode($updatedClaims, JSON_UNESCAPED_SLASHES)
    : false;
if (!is_string($customAttributes) || strlen($customAttributes) > 1000) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
$claimUpdate = dost_age_http_json(
    'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:update',
    array('Authorization: Bearer ' . $serviceToken),
    array('localId' => $owner['uid'], 'customAttributes' => $customAttributes)
);
if ($claimUpdate === false || $claimUpdate['status'] !== 200) {
    dost_rtdn_respond(503, array('error' => 'Notification processing is unavailable'));
}
dost_firebase_claim_unlock($claimLock);
if (in_array($product['plan'], array('shield_team', 'shield_business'), true)) {
    $firestoreToken = dost_premium_service_account_token(
        $credentials,
        null,
        'https://www.googleapis.com/auth/datastore'
    );
    if (!is_string($firestoreToken)
        || !dost_premium_sync_shield_orgs($projectId, $firestoreToken, $owner['uid'], $updatedClaims, time())
    ) {
        dost_rtdn_respond(503, array('error' => 'Shield plan synchronization is unavailable'));
    }
}
dost_rtdn_respond(200, array('ok' => true));
