<?php

require_once __DIR__ . '/age_eligibility_support.php';

function dost_premium_product($productId)
{
    $products = array(
        'dost_explore_plus' => array('plan' => 'explore_plus', 'type' => 'subscription', 'adult' => true),
        'dost_explore_plus_lifetime' => array('plan' => 'explore_plus', 'type' => 'inapp', 'adult' => true),
        'dost_cipher' => array('plan' => 'cipher', 'type' => 'subscription', 'adult' => false),
        'dost_shield_team' => array('plan' => 'shield_team', 'type' => 'subscription', 'adult' => false),
        'dost_shield_business' => array('plan' => 'shield_business', 'type' => 'subscription', 'adult' => false),
        'dost_plus' => array('plan' => 'plus_bundle', 'type' => 'subscription', 'adult' => true)
    );
    return isset($products[$productId]) ? $products[$productId] : false;
}

function dost_premium_product_type($productId)
{
    $product = dost_premium_product($productId);
    return is_array($product) ? $product['type'] : false;
}

function dost_premium_plan_enabled($plan, $config)
{
    $keys = array(
        'explore_plus' => 'plan_explore_plus_enabled',
        'cipher' => 'plan_cipher_enabled',
        'shield_team' => 'plan_shield_enabled',
        'shield_business' => 'plan_shield_enabled',
        'plus_bundle' => 'plan_explore_plus_enabled'
    );
    if (!is_array($config) || !isset($keys[$plan])) {
        return false;
    }
    $value = isset($config[$keys[$plan]]) ? $config[$keys[$plan]] : false;
    $enabled = $value === true || (is_string($value) && strtolower(trim($value)) === 'true');
    if ($plan === 'plus_bundle') {
        $cipher = isset($config['plan_cipher_enabled']) ? $config['plan_cipher_enabled'] : false;
        $enabled = $enabled && ($cipher === true
            || (is_string($cipher) && strtolower(trim($cipher)) === 'true'));
    }
    return $enabled;
}

function dost_premium_has_adult_eligibility($claims, $now)
{
    if (!is_array($claims) || !is_int($now)
        || (isset($claims['dostAgeCohort']) && $claims['dostAgeCohort'] !== 'adult')
    ) {
        return false;
    }
    return !empty($claims['dostAgeEligible'])
        && isset($claims['dostAgeEligibleUntil'])
        && is_numeric($claims['dostAgeEligibleUntil'])
        && (int) $claims['dostAgeEligibleUntil'] > $now;
}

function dost_premium_obfuscated_account_id($uid)
{
    if (!is_string($uid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1) {
        return '';
    }
    return hash('sha256', "dost-premium-v1\n" . $uid);
}

function dost_premium_purchase_entitlement($productId, $purchaseType, $purchase, $uid, $now)
{
    if (!is_string($productId) || !is_string($purchaseType) || !is_array($purchase)
        || !is_int($now) || dost_premium_product_type($productId) !== $purchaseType
    ) {
        return false;
    }
    $product = dost_premium_product($productId);
    $expectedAccountId = dost_premium_obfuscated_account_id($uid);
    $externalIds = isset($purchase['externalAccountIdentifiers']) && is_array($purchase['externalAccountIdentifiers'])
        ? $purchase['externalAccountIdentifiers']
        : array();
    $accountId = isset($externalIds['obfuscatedExternalAccountId'])
        ? $externalIds['obfuscatedExternalAccountId']
        : (isset($purchase['obfuscatedExternalAccountId']) ? $purchase['obfuscatedExternalAccountId'] : '');
    if ($expectedAccountId === '' || !is_string($accountId) || !hash_equals($expectedAccountId, $accountId)) {
        return false;
    }

    if ($purchaseType === 'subscription') {
        $allowedStates = array(
            'SUBSCRIPTION_STATE_ACTIVE',
            'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
            'SUBSCRIPTION_STATE_CANCELED'
        );
        if (!isset($purchase['subscriptionState'], $purchase['lineItems'])
            || !in_array($purchase['subscriptionState'], $allowedStates, true)
            || !is_array($purchase['lineItems'])
        ) {
            return false;
        }
        foreach ($purchase['lineItems'] as $lineItem) {
            if (!is_array($lineItem) || !isset($lineItem['productId'], $lineItem['expiryTime'])
                || $lineItem['productId'] !== $productId || !is_string($lineItem['expiryTime'])
                || !isset($lineItem['offerDetails']) || !is_array($lineItem['offerDetails'])
                || !isset($lineItem['offerDetails']['basePlanId'])
                || !in_array($lineItem['offerDetails']['basePlanId'], array('monthly', 'annual'), true)
            ) {
                continue;
            }
            $expiresAt = strtotime($lineItem['expiryTime']);
            if ($expiresAt !== false && $expiresAt > $now) {
                return array(
                    'lifetime' => false,
                    'expires_at' => $expiresAt,
                    'plan' => $product['plan'],
                    'product_id' => $productId,
                    'base_plan_id' => $lineItem['offerDetails']['basePlanId']
                );
            }
        }
        return false;
    }

    $purchaseState = isset($purchase['purchaseStateContext']['purchaseState'])
        ? $purchase['purchaseStateContext']['purchaseState']
        : '';
    $lineItems = isset($purchase['productLineItem']) && is_array($purchase['productLineItem'])
        ? $purchase['productLineItem']
        : array();
    if ($purchaseState !== 'PURCHASED') {
        return false;
    }
    foreach ($lineItems as $lineItem) {
        if (is_array($lineItem) && isset($lineItem['productId']) && $lineItem['productId'] === $productId) {
            return array(
                'lifetime' => true,
                'expires_at' => null,
                'plan' => $product['plan'],
                'product_id' => $productId,
                'base_plan_id' => null
            );
        }
    }
    return false;
}

function dost_premium_claims(
    $existing,
    $productId,
    $entitlement,
    $now,
    $purchaseToken = null,
    $eventTime = null,
    $requireAdult = true
)
{
    $product = dost_premium_product($productId);
    if (!is_array($existing) || !is_string($productId) || !is_array($entitlement) || !is_int($now)
        || !is_array($product) || !isset($entitlement['plan'])
        || $entitlement['plan'] !== $product['plan']
        || (!empty($product['adult']) && $requireAdult
            && !dost_premium_has_adult_eligibility($existing, $now))
    ) {
        return false;
    }
    $newLifetime = !empty($entitlement['lifetime']);
    $newExpiry = $newLifetime ? 0 : (isset($entitlement['expires_at']) ? (int) $entitlement['expires_at'] : 0);
    if (!$newLifetime && ($newExpiry <= 0 || $newExpiry <= $now)) {
        return false;
    }
    $plans = $product['plan'] === 'plus_bundle'
        ? array('explore_plus', 'cipher')
        : array($product['plan']);
    foreach ($plans as $plan) {
        $claimToken = $plan === 'explore_plus' ? 'dostExplorePlusToken'
            : ($plan === 'cipher' ? 'dostCipherToken' : 'dostShieldToken');
        $claimUpdated = $plan === 'explore_plus' ? 'dostExplorePlusUpdatedAt'
            : ($plan === 'cipher' ? 'dostCipherUpdatedAt' : 'dostShieldUpdatedAt');
        if ($eventTime !== null && is_int($eventTime) && is_string($purchaseToken) && $purchaseToken !== ''
            && isset($existing[$claimToken])
            && (!is_string($existing[$claimToken])
                || !hash_equals($existing[$claimToken], hash('sha256', $purchaseToken)))
            && isset($existing[$claimUpdated]) && is_numeric($existing[$claimUpdated])
            && (int) $existing[$claimUpdated] > $eventTime
        ) {
            continue;
        }
        if ($plan === 'explore_plus') {
            $oldExpiry = isset($existing['dostExplorePlusUntil']) && is_numeric($existing['dostExplorePlusUntil'])
                ? (int) $existing['dostExplorePlusUntil']
                : (isset($existing['dostPremiumUntil']) && is_numeric($existing['dostPremiumUntil'])
                    ? (int) $existing['dostPremiumUntil'] : 0);
            $lifetime = !empty($existing['dostExplorePlusLifetime']) || $newLifetime;
            $existing['dostExplorePlusLifetime'] = $lifetime;
            $existing['dostExplorePlusUntil'] = $lifetime ? 0 : max($oldExpiry, $newExpiry);
            $existing['dostPremiumLifetime'] = $lifetime;
            $existing['dostPremiumUntil'] = $existing['dostExplorePlusUntil'];
            if (empty($existing['dostExplorePlusLifetime']) || $newLifetime) {
                $existing['dostExplorePlusProductId'] = $productId;
            }
            if ((!$lifetime || $newLifetime) && is_string($purchaseToken) && $purchaseToken !== '') {
                $existing[$claimToken] = hash('sha256', $purchaseToken);
                $existing['dostExplorePlusUpdatedAt'] = $eventTime !== null && is_int($eventTime)
                    ? $eventTime : $now;
            }
        } elseif ($plan === 'cipher') {
            $oldExpiry = isset($existing['dostCipherUntil']) && is_numeric($existing['dostCipherUntil'])
                ? (int) $existing['dostCipherUntil'] : 0;
            $existing['dostCipherUntil'] = max($oldExpiry, $newExpiry);
            $existing['dostCipherProductId'] = $productId;
            if (is_string($purchaseToken) && $purchaseToken !== '') {
                $existing[$claimToken] = hash('sha256', $purchaseToken);
                $existing['dostCipherUpdatedAt'] = $eventTime !== null && is_int($eventTime)
                    ? $eventTime : $now;
            }
        } else {
            $existing['dostShieldUntil'] = $newExpiry;
            $existing['dostShieldSeats'] = $plan === 'shield_team' ? 10 : 25;
            $existing['dostShieldProductId'] = $productId;
            if (is_string($purchaseToken) && $purchaseToken !== '') {
                $existing[$claimToken] = hash('sha256', $purchaseToken);
                $existing['dostShieldUpdatedAt'] = $eventTime !== null && is_int($eventTime)
                    ? $eventTime : $now;
            }
        }
    }
    $existing['dostPremiumProductId'] = $productId;
    $existing['dostPremiumSource'] = 'play';
    return $existing;
}

function dost_premium_rtdn_token_info_is_valid($tokenInfo, $audience, $serviceAccountEmail)
{
    return is_array($tokenInfo) && is_string($audience) && $audience !== ''
        && is_string($serviceAccountEmail) && $serviceAccountEmail !== ''
        && isset(
            $tokenInfo['aud'],
            $tokenInfo['email'],
            $tokenInfo['email_verified'],
            $tokenInfo['iss'],
            $tokenInfo['expires_in']
        )
        && is_string($tokenInfo['aud']) && hash_equals($audience, $tokenInfo['aud'])
        && is_string($tokenInfo['email']) && hash_equals($serviceAccountEmail, $tokenInfo['email'])
        && in_array($tokenInfo['email_verified'], array(true, 'true'), true)
        && in_array($tokenInfo['iss'], array('accounts.google.com', 'https://accounts.google.com'), true)
        && is_numeric($tokenInfo['expires_in']) && (int) $tokenInfo['expires_in'] > 0;
}

function dost_premium_shield_plan_fields($claims, $now)
{
    if (!is_array($claims) || !is_int($now)
        || !isset($claims['dostShieldUntil'], $claims['dostShieldSeats'])
        || !is_numeric($claims['dostShieldUntil']) || !is_numeric($claims['dostShieldSeats'])
        || (int) $claims['dostShieldUntil'] <= $now
    ) {
        return false;
    }
    $seats = (int) $claims['dostShieldSeats'];
    if (!in_array($seats, array(10, 25), true)) {
        return false;
    }
    return array(
        'seatLimit' => $seats,
        'activeUntil' => gmdate('Y-m-d\TH:i:s\Z', (int) $claims['dostShieldUntil'])
    );
}

function dost_premium_sync_shield_orgs($projectId, $serviceToken, $uid, $claims, $now)
{
    if (!is_string($projectId) || !is_string($serviceToken) || !is_string($uid)
        || !is_array($claims) || !is_int($now)
    ) {
        return false;
    }
    $query = array('structuredQuery' => array(
        'from' => array(array('collectionId' => 'secureOrgs')),
        'where' => array('fieldFilter' => array(
            'field' => array('fieldPath' => 'ownerUid'),
            'op' => 'EQUAL',
            'value' => array('stringValue' => $uid)
        )),
        'limit' => 100
    ));
    $response = dost_premium_http_json(
        'POST',
        'https://firestore.googleapis.com/v1/projects/' . rawurlencode($projectId)
            . '/databases/(default)/documents:runQuery',
        array('Authorization: Bearer ' . $serviceToken),
        $query
    );
    if ($response === false || $response['status'] !== 200 || !is_array($response['body'])) {
        return false;
    }
    $activePlan = dost_premium_shield_plan_fields($claims, $now);
    foreach ($response['body'] as $result) {
        if (!isset($result['document']['name'], $result['document']['fields']['ownerUid']['stringValue'])
            || !is_string($result['document']['name'])
            || !is_string($result['document']['fields']['ownerUid']['stringValue'])
            || !hash_equals($uid, $result['document']['fields']['ownerUid']['stringValue'])
            || preg_match('/\Aprojects\/[A-Za-z0-9_-]+\/databases\/\(default\)\/documents\/secureOrgs\/([A-Za-z0-9_-]{1,128})\z/', $result['document']['name'], $matches) !== 1
        ) {
            continue;
        }
        $oldPlan = isset($result['document']['fields']['plan']['mapValue']['fields'])
            ? $result['document']['fields']['plan']['mapValue']['fields']
            : array();
        $seatLimit = $activePlan !== false
            ? $activePlan['seatLimit']
            : (isset($oldPlan['seatLimit']['integerValue'])
                && in_array((int) $oldPlan['seatLimit']['integerValue'], array(10, 25), true)
                    ? (int) $oldPlan['seatLimit']['integerValue'] : 25);
        $activeUntil = $activePlan !== false
            ? $activePlan['activeUntil']
            : gmdate('Y-m-d\TH:i:s\Z', $now);
        $url = 'https://firestore.googleapis.com/v1/projects/' . rawurlencode($projectId)
            . '/databases/(default)/documents/secureOrgs/' . rawurlencode($matches[1])
            . '?updateMask.fieldPaths=plan';
        $updated = dost_premium_http_json(
            'PATCH',
            $url,
            array('Authorization: Bearer ' . $serviceToken),
            array('fields' => array('plan' => array('mapValue' => array('fields' => array(
                'seatLimit' => array('integerValue' => (string) $seatLimit),
                'activeUntil' => array('timestampValue' => $activeUntil)
            )))))
        );
        if ($updated === false || !in_array($updated['status'], array(200, 201), true)) {
            return false;
        }
    }
    return true;
}

function dost_premium_store_purchase_owner($token, $uid, $productId, $directory, $refundRequired = false)
{
    if (!is_string($token) || $token === '' || !is_string($uid)
        || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1
        || !is_array(dost_premium_product($productId)) || !is_string($directory) || $directory === ''
    ) {
        return false;
    }
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    @chmod($directory, 0700);
    $path = rtrim($directory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
        . hash('sha256', $token) . '.json';
    $encoded = json_encode(array(
        'uid' => $uid,
        'product_id' => $productId,
        'refund_required' => $refundRequired === true
    ));
    if (!is_string($encoded)) {
        return false;
    }
    $written = @file_put_contents($path, $encoded, LOCK_EX);
    @chmod($path, 0600);
    return $written === strlen($encoded);
}

function dost_premium_purchase_owner($token, $directory)
{
    if (!is_string($token) || $token === '' || !is_string($directory) || $directory === '') {
        return false;
    }
    $path = rtrim($directory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
        . hash('sha256', $token) . '.json';
    $handle = @fopen($path, 'r');
    if ($handle === false || !flock($handle, LOCK_SH)) {
        if (is_resource($handle)) {
            fclose($handle);
        }
        return false;
    }
    $contents = stream_get_contents($handle);
    flock($handle, LOCK_UN);
    fclose($handle);
    $owner = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($owner) || !isset($owner['uid'], $owner['product_id'])
        || !is_string($owner['uid']) || !is_string($owner['product_id'])
        || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $owner['uid']) !== 1
        || !is_array(dost_premium_product($owner['product_id']))
    ) {
        return false;
    }
    return $owner;
}

function dost_premium_revoke_claims($existing, $productId, $now, $purchaseToken = null, $eventTime = null)
{
    $product = dost_premium_product($productId);
    if (!is_array($existing) || !is_array($product) || !is_int($now)) {
        return false;
    }
    $tokenHash = is_string($purchaseToken) && $purchaseToken !== '' ? hash('sha256', $purchaseToken) : null;
    $plans = $product['plan'] === 'plus_bundle'
        ? array('explore_plus', 'cipher')
        : array($product['plan']);
    foreach ($plans as $plan) {
        $claimProductId = $plan === 'explore_plus' ? 'dostExplorePlusProductId'
            : ($plan === 'cipher' ? 'dostCipherProductId' : 'dostShieldProductId');
        $claimToken = $plan === 'explore_plus' ? 'dostExplorePlusToken'
            : ($plan === 'cipher' ? 'dostCipherToken' : 'dostShieldToken');
        $claimUpdated = $plan === 'explore_plus' ? 'dostExplorePlusUpdatedAt'
            : ($plan === 'cipher' ? 'dostCipherUpdatedAt' : 'dostShieldUpdatedAt');
        if (!isset($existing[$claimProductId]) || $existing[$claimProductId] !== $productId
            || ($tokenHash !== null
                && (!isset($existing[$claimToken]) || !is_string($existing[$claimToken])
                    || !hash_equals($existing[$claimToken], $tokenHash)))
            || ($eventTime !== null && is_int($eventTime)
                && isset($existing[$claimUpdated]) && is_numeric($existing[$claimUpdated])
                && $eventTime < (int) $existing[$claimUpdated])
        ) {
            continue;
        }
        if ($plan === 'explore_plus') {
            $existing['dostExplorePlusLifetime'] = false;
            $existing['dostExplorePlusUntil'] = $now;
            $existing['dostPremiumLifetime'] = false;
            $existing['dostPremiumUntil'] = $now;
        } elseif ($plan === 'cipher') {
            $existing['dostCipherUntil'] = $now;
        } else {
            $existing['dostShieldUntil'] = $now;
            $existing['dostShieldSeats'] = 0;
        }
        $existing[$claimUpdated] = $eventTime !== null && is_int($eventTime) ? $eventTime : $now;
    }
    return $existing;
}

function dost_premium_account_creation_epoch($createdAt)
{
    if (is_int($createdAt) || is_float($createdAt) || (is_string($createdAt) && is_numeric($createdAt))) {
        $timestamp = (int) $createdAt;
        if ($timestamp > 100000000000) {
            $timestamp = (int) floor($timestamp / 1000);
        }
        return $timestamp > 0 ? $timestamp : false;
    }
    if (!is_string($createdAt) || trim($createdAt) === '') {
        return false;
    }
    $timestamp = strtotime($createdAt);
    return $timestamp === false ? false : $timestamp;
}

function dost_premium_intro_claims($existing, $createdAt, $days, $now, $launchAt = null)
{
    if (!is_array($existing) || !is_int($createdAt) || $createdAt <= 0
        || !is_int($days) || $days < 1 || $days > 3650
        || !is_int($now) || ($launchAt !== null && !is_int($launchAt))
        || !dost_premium_has_adult_eligibility($existing, $now)
    ) {
        return false;
    }
    if (!empty($existing['dostPremiumIntroGranted'])) {
        return $existing;
    }

    if ($launchAt !== null) {
        $start = $createdAt < $launchAt ? $now : $createdAt;
    } else {
        $start = $createdAt >= $now - 86400 ? $createdAt : $now;
    }
    $introExpiry = $start + ($days * 86400);
    $existingExpiry = isset($existing['dostExplorePlusUntil']) && is_numeric($existing['dostExplorePlusUntil'])
        ? (int) $existing['dostExplorePlusUntil']
        : (isset($existing['dostPremiumUntil']) && is_numeric($existing['dostPremiumUntil'])
            ? (int) $existing['dostPremiumUntil'] : 0);
    $hasBetterEntitlement = !empty($existing['dostExplorePlusLifetime'])
        || !empty($existing['dostPremiumLifetime']) || $existingExpiry > $introExpiry;
    if (!$hasBetterEntitlement) {
        $existing['dostExplorePlusLifetime'] = false;
        $existing['dostExplorePlusUntil'] = $introExpiry;
        $existing['dostPremiumLifetime'] = false;
        $existing['dostPremiumUntil'] = $introExpiry;
        $existing['dostPremiumSource'] = 'intro';
    }
    $existing['dostPremiumIntroGranted'] = true;
    $existing['dostPremiumIntroGrantedAt'] = $now;
    return $existing;
}

function dost_premium_intro_response($claims, $now)
{
    $lifetime = is_array($claims)
        && (!empty($claims['dostExplorePlusLifetime']) || !empty($claims['dostPremiumLifetime']));
    $until = is_array($claims) && isset($claims['dostExplorePlusUntil']) && is_numeric($claims['dostExplorePlusUntil'])
        ? (int) $claims['dostExplorePlusUntil']
        : (is_array($claims) && isset($claims['dostPremiumUntil']) && is_numeric($claims['dostPremiumUntil'])
            ? (int) $claims['dostPremiumUntil'] : null);
    $source = is_array($claims) && isset($claims['dostPremiumSource']) && is_string($claims['dostPremiumSource'])
        ? $claims['dostPremiumSource']
        : (is_array($claims) && !empty($claims['dostPremiumProductId']) ? 'play' : null);
    return array(
        'premium' => $lifetime || ($until !== null && $until > $now),
        'source' => $source,
        'lifetime' => $lifetime,
        'premium_until' => $until,
        'intro_granted' => is_array($claims) && !empty($claims['dostPremiumIntroGranted'])
    );
}

function dost_premium_service_account_token($credentials, $now = null, $scope = null)
{
    if (!is_array($credentials)
        || !isset($credentials['client_email'], $credentials['private_key'])
        || !is_string($credentials['client_email'])
        || !is_string($credentials['private_key'])
    ) {
        return false;
    }
    $now = is_int($now) ? $now : time();
    $scope = is_string($scope) && trim($scope) !== ''
        ? trim($scope)
        : 'https://www.googleapis.com/auth/androidpublisher https://www.googleapis.com/auth/identitytoolkit';
    $header = dost_age_base64url(json_encode(array('alg' => 'RS256', 'typ' => 'JWT')));
    $claims = dost_age_base64url(json_encode(array(
        'iss' => $credentials['client_email'],
        'scope' => $scope,
        'aud' => 'https://oauth2.googleapis.com/token',
        'iat' => $now,
        'exp' => $now + 3600
    ), JSON_UNESCAPED_SLASHES));
    if ($header === '' || $claims === '') {
        return false;
    }
    $unsigned = $header . '.' . $claims;
    $signature = '';
    $key = openssl_pkey_get_private($credentials['private_key']);
    if ($key === false || !openssl_sign($unsigned, $signature, $key, OPENSSL_ALGO_SHA256)) {
        return false;
    }
    $response = dost_age_http_form('https://oauth2.googleapis.com/token', array(
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $unsigned . '.' . dost_age_base64url($signature)
    ));
    if ($response === false || $response['status'] !== 200
        || !isset($response['body']['access_token'])
        || !is_string($response['body']['access_token'])
    ) {
        return false;
    }
    return $response['body']['access_token'];
}

function dost_premium_http_json($method, $url, $headers = array(), $payload = null)
{
    if (!function_exists('curl_init')) {
        return false;
    }
    $curl = curl_init($url);
    if ($curl === false) {
        return false;
    }
    $options = array(
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => array_merge(array('Accept: application/json'), $headers),
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_FOLLOWLOCATION => false
    );
    if ($payload !== null) {
        $encoded = json_encode($payload, JSON_UNESCAPED_SLASHES);
        if (!is_string($encoded)) {
            curl_close($curl);
            return false;
        }
        $options[CURLOPT_HTTPHEADER][] = 'Content-Type: application/json';
        $options[CURLOPT_POSTFIELDS] = $encoded;
    }
    curl_setopt_array($curl, $options);
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
    $tooLarge = is_string($response) && strlen($response) > 262144;
    curl_close($curl);
    if (!is_string($response) || $tooLarge) {
        return false;
    }
    $decoded = json_decode($response, true);
    return array('status' => $status, 'body' => is_array($decoded) ? $decoded : array());
}

function dost_premium_rate_limit_allows($ip, $now, $limit = 20)
{
    if (!is_string($ip) || $ip === '' || !is_int($now) || !is_int($limit) || $limit < 1) {
        return false;
    }
    $directory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'dost-premium-rate-limit';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    $path = $directory . DIRECTORY_SEPARATOR . hash('sha256', $ip) . '.json';
    $handle = @fopen($path, 'c+');
    if ($handle === false || !flock($handle, LOCK_EX)) {
        if (is_resource($handle)) {
            fclose($handle);
        }
        return false;
    }
    $contents = stream_get_contents($handle);
    $timestamps = is_string($contents) ? json_decode($contents, true) : null;
    $timestamps = is_array($timestamps) ? array_values(array_filter($timestamps, function ($timestamp) use ($now) {
        return is_int($timestamp) && $timestamp > $now - 60;
    })) : array();
    $allowed = count($timestamps) < $limit;
    if ($allowed) {
        $timestamps[] = $now;
    }
    $encoded = json_encode($timestamps);
    $saved = is_string($encoded)
        && rewind($handle)
        && ftruncate($handle, 0)
        && fwrite($handle, $encoded) === strlen($encoded)
        && fflush($handle);
    @chmod($path, 0600);
    flock($handle, LOCK_UN);
    fclose($handle);
    return $saved && $allowed;
}
