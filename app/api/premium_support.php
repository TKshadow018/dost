<?php

require_once __DIR__ . '/age_eligibility_support.php';

function dost_premium_product_type($productId)
{
    $products = array(
        'dost_premium_monthly' => 'subscription',
        'dost_premium_annual' => 'subscription',
        'dost_premium_lifetime' => 'lifetime'
    );
    return isset($products[$productId]) ? $products[$productId] : false;
}

function dost_premium_user_adult_eligible($claims, $now)
{
    return is_array($claims)
        && is_int($now)
        && isset(
            $claims['dostAgeCohort'],
            $claims['dostAgeEligible'],
            $claims['dostAgeEligibleUntil']
        )
        && $claims['dostAgeCohort'] === 'adult'
        && $claims['dostAgeEligible'] === true
        && is_int($claims['dostAgeEligibleUntil'])
        && $claims['dostAgeEligibleUntil'] > $now;
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
            ) {
                continue;
            }
            $expiresAt = strtotime($lineItem['expiryTime']);
            if ($expiresAt !== false && $expiresAt > $now) {
                return array('lifetime' => false, 'expires_at' => $expiresAt);
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
            return array('lifetime' => true, 'expires_at' => null);
        }
    }
    return false;
}

function dost_premium_claims($existing, $productId, $entitlement, $now)
{
    if (!is_array($existing) || !is_string($productId) || !is_array($entitlement) || !is_int($now)) {
        return false;
    }
    $existingExpiry = max(
        isset($existing['dostPremiumUntil']) && is_numeric($existing['dostPremiumUntil'])
            ? (int) $existing['dostPremiumUntil']
            : 0,
        isset($existing['dostExplorePlusUntil']) && is_numeric($existing['dostExplorePlusUntil'])
            ? (int) $existing['dostExplorePlusUntil']
            : 0
    );
    $newLifetime = !empty($entitlement['lifetime']);
    $newExpiry = $newLifetime ? 0 : (isset($entitlement['expires_at']) ? (int) $entitlement['expires_at'] : 0);
    if (!$newLifetime && ($newExpiry <= 0 || $newExpiry <= $now)) {
        return false;
    }
    $lifetime = !empty($existing['dostPremiumLifetime'])
        || !empty($existing['dostExplorePlusLifetime'])
        || $newLifetime;
    $existing['dostPremiumLifetime'] = $lifetime;
    $existing['dostPremiumUntil'] = $lifetime ? 0 : max($existingExpiry, $newExpiry);
    $existing['dostExplorePlusLifetime'] = $existing['dostPremiumLifetime'];
    $existing['dostExplorePlusUntil'] = $existing['dostPremiumUntil'];
    $existing['dostPremiumProductId'] = $productId;
    $existing['dostPremiumSource'] = 'play';
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
    $existingExpiry = max(
        isset($existing['dostPremiumUntil']) && is_numeric($existing['dostPremiumUntil'])
            ? (int) $existing['dostPremiumUntil']
            : 0,
        isset($existing['dostExplorePlusUntil']) && is_numeric($existing['dostExplorePlusUntil'])
            ? (int) $existing['dostExplorePlusUntil']
            : 0
    );
    $hasBetterEntitlement = !empty($existing['dostPremiumLifetime'])
        || !empty($existing['dostExplorePlusLifetime'])
        || $existingExpiry > $introExpiry;
    if (!$hasBetterEntitlement) {
        $existing['dostPremiumLifetime'] = false;
        $existing['dostPremiumUntil'] = $introExpiry;
        $existing['dostPremiumSource'] = 'intro';
    }
    $existing['dostPremiumIntroGranted'] = true;
    $existing['dostPremiumIntroGrantedAt'] = $now;
    $lifetime = !empty($existing['dostPremiumLifetime']) || !empty($existing['dostExplorePlusLifetime']);
    $until = $lifetime ? 0 : max($existingExpiry, $hasBetterEntitlement ? 0 : $introExpiry);
    $existing['dostPremiumLifetime'] = $lifetime;
    $existing['dostPremiumUntil'] = $until;
    $existing['dostExplorePlusLifetime'] = $lifetime;
    $existing['dostExplorePlusUntil'] = $until;
    return $existing;
}

function dost_explore_plus_claim_active($claims, $now)
{
    if (!is_array($claims) || !is_int($now)) {
        return false;
    }
    if (!empty($claims['dostExplorePlusLifetime']) || !empty($claims['dostPremiumLifetime'])) {
        return true;
    }
    $expiresAt = max(
        isset($claims['dostExplorePlusUntil']) && is_numeric($claims['dostExplorePlusUntil'])
            ? (int) $claims['dostExplorePlusUntil']
            : 0,
        isset($claims['dostPremiumUntil']) && is_numeric($claims['dostPremiumUntil'])
            ? (int) $claims['dostPremiumUntil']
            : 0
    );
    return $expiresAt > $now;
}

function dost_premium_intro_response($claims, $now)
{
    $lifetime = is_array($claims) && !empty($claims['dostPremiumLifetime']);
    $until = is_array($claims) && isset($claims['dostPremiumUntil']) && is_numeric($claims['dostPremiumUntil'])
        ? (int) $claims['dostPremiumUntil']
        : null;
    $source = is_array($claims) && isset($claims['dostPremiumSource']) && is_string($claims['dostPremiumSource'])
        ? $claims['dostPremiumSource']
        : (is_array($claims) && !empty($claims['dostPremiumProductId']) ? 'play' : null);
    $active = $lifetime || ($until !== null && $until > $now);
    return array(
        'premium' => $active,
        'explore_plus' => $active,
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
