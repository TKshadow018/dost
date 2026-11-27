<?php

function dost_age_base64url($value)
{
    return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
}

function dost_age_request_hash($uid, $eligible, $nonce)
{
    if (!is_string($uid) || $uid === ''
        || !is_bool($eligible)
        || !is_string($nonce) || preg_match('/\A[a-f0-9]{64}\z/', $nonce) !== 1
    ) {
        return '';
    }

    $payload = "dost-age-v1\n" . $uid . "\n" . ($eligible ? '1' : '0') . "\n" . $nonce;
    return dost_age_base64url(hash('sha256', $payload, true));
}

function dost_age_test_mode_enabled($environmentValue, $configValue)
{
    if ($environmentValue !== false) {
        return $environmentValue === 'true';
    }

    return $configValue === true || $configValue === 'true';
}

function dost_age_test_uids($environmentValue, $configValue)
{
    if ($environmentValue !== false) {
        $uids = explode(',', $environmentValue);
    } elseif (is_array($configValue)) {
        $uids = $configValue;
    } else {
        return array();
    }

    return array_values(array_filter(array_map(function ($uid) {
        return is_string($uid) ? trim($uid) : '';
    }, $uids), function ($uid) {
        return $uid !== '';
    }));
}

function dost_age_test_uid_is_allowlisted($uid, $testUids)
{
    if (!is_string($uid) || !is_array($testUids)) {
        return false;
    }

    foreach ($testUids as $testUid) {
        if (is_string($testUid) && hash_equals($testUid, $uid)) {
            return true;
        }
    }

    return false;
}

function dost_age_authenticated_uid_matches($requestUid, $authenticatedUid)
{
    return is_string($requestUid)
        && $requestUid !== ''
        && is_string($authenticatedUid)
        && hash_equals($requestUid, $authenticatedUid);
}

function dost_age_integrity_failure_field($payload, $uid, $eligible, $nonce, $now, $allowTestAppVerdicts = false)
{
    if (!is_array($payload) || !isset($payload['tokenPayloadExternal']) || !is_array($payload['tokenPayloadExternal'])) {
        return 'payload';
    }
    $external = $payload['tokenPayloadExternal'];
    $request = isset($external['requestDetails']) && is_array($external['requestDetails'])
        ? $external['requestDetails']
        : array();
    $app = isset($external['appIntegrity']) && is_array($external['appIntegrity'])
        ? $external['appIntegrity']
        : array();
    $account = isset($external['accountDetails']) && is_array($external['accountDetails'])
        ? $external['accountDetails']
        : array();
    $device = isset($external['deviceIntegrity']) && is_array($external['deviceIntegrity'])
        ? $external['deviceIntegrity']
        : array();
    $deviceVerdicts = isset($device['deviceRecognitionVerdict']) && is_array($device['deviceRecognitionVerdict'])
        ? $device['deviceRecognitionVerdict']
        : array();
    $timestamp = isset($request['timestampMillis']) && is_string($request['timestampMillis'])
        && preg_match('/\A[0-9]{1,16}\z/', $request['timestampMillis']) === 1
        ? (int) $request['timestampMillis']
        : 0;
    $expectedHash = dost_age_request_hash($uid, $eligible, $nonce);

    if (!isset($request['requestPackageName']) || $request['requestPackageName'] !== 'com.snigtus.dost') {
        return 'package';
    }
    if ($expectedHash === '' || !isset($request['requestHash'])
        || !is_string($request['requestHash'])
        || !hash_equals($expectedHash, $request['requestHash'])
    ) {
        return 'hash';
    }
    if ($timestamp <= 0 || abs(($now * 1000) - $timestamp) > 300000) {
        return 'timestamp';
    }
    if (!isset($app['appRecognitionVerdict'])
        || ($app['appRecognitionVerdict'] !== 'PLAY_RECOGNIZED'
            && !($allowTestAppVerdicts && $app['appRecognitionVerdict'] === 'UNRECOGNIZED_VERSION'))
    ) {
        return 'app_recognition';
    }
    if (!isset($account['appLicensingVerdict'])
        || ($account['appLicensingVerdict'] !== 'LICENSED'
            && !($allowTestAppVerdicts && $account['appLicensingVerdict'] === 'UNLICENSED'))
    ) {
        return 'licensing';
    }
    if (!in_array('MEETS_DEVICE_INTEGRITY', $deviceVerdicts, true)) {
        return 'device';
    }

    return '';
}

function dost_age_integrity_allows($payload, $uid, $eligible, $nonce, $now, $allowTestAppVerdicts = false, &$failureField = null)
{
    $failureField = dost_age_integrity_failure_field(
        $payload,
        $uid,
        $eligible,
        $nonce,
        $now,
        $allowTestAppVerdicts
    );

    return $failureField === '';
}

function dost_age_integrity_reason_code($failureField, $payload)
{
    if ($failureField === 'app_recognition'
        && isset($payload['tokenPayloadExternal']['appIntegrity']['appRecognitionVerdict'])
        && $payload['tokenPayloadExternal']['appIntegrity']['appRecognitionVerdict'] === 'UNRECOGNIZED_VERSION'
    ) {
        return 'integrity_unrecognized_app';
    }
    if ($failureField === 'licensing'
        && isset($payload['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'])
        && $payload['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'] === 'UNLICENSED'
    ) {
        return 'integrity_unlicensed_app';
    }

    $reasonCodes = array(
        'payload' => 'integrity_payload',
        'package' => 'integrity_package',
        'hash' => 'integrity_request_hash',
        'timestamp' => 'integrity_timestamp',
        'app_recognition' => 'integrity_app_recognition',
        'licensing' => 'integrity_app_licensing',
        'device' => 'integrity_device'
    );

    return isset($reasonCodes[$failureField]) ? $reasonCodes[$failureField] : 'integrity_verification';
}

function dost_age_custom_attributes($eligible, $now, $ttlSeconds)
{
    if (!is_bool($eligible) || !is_int($now) || !is_int($ttlSeconds) || $ttlSeconds < 60 || $ttlSeconds > 86400) {
        return '';
    }
    $attributes = json_encode(array(
        'dostAgeEligible' => $eligible,
        'dostAgeEligibleUntil' => $now + $ttlSeconds
    ), JSON_UNESCAPED_SLASHES);
    return is_string($attributes) ? $attributes : '';
}

function dost_age_from_birth_month_year($birthMonth, $birthYear, $currentMonth, $currentYear)
{
    if (!is_int($birthMonth) || $birthMonth < 1 || $birthMonth > 12
        || !is_int($birthYear) || !is_int($currentMonth) || $currentMonth < 1 || $currentMonth > 12
        || !is_int($currentYear) || $birthYear < 1900 || $birthYear > $currentYear
    ) {
        return false;
    }
    $age = $currentYear - $birthYear - ($birthMonth >= $currentMonth ? 1 : 0);
    return $age >= 0 && $age <= 120 ? $age : false;
}

function dost_age_cohort_claims($existing, $birthMonth, $birthYear, $currentMonth, $currentYear)
{
    if (!is_array($existing)) {
        return false;
    }
    $age = dost_age_from_birth_month_year($birthMonth, $birthYear, $currentMonth, $currentYear);
    if ($age === false || $age < 13) {
        return false;
    }
    if (isset($existing['dostAgeCohort'])
        && (!isset($existing['dostBirthMonth'], $existing['dostBirthYear'])
            || !is_int($existing['dostBirthMonth']) || !is_int($existing['dostBirthYear']))
    ) {
        return false;
    }
    if (isset($existing['dostBirthMonth']) || isset($existing['dostBirthYear'])) {
        if (!isset($existing['dostBirthMonth'], $existing['dostBirthYear'])
            || !is_int($existing['dostBirthMonth']) || !is_int($existing['dostBirthYear'])
            || $existing['dostBirthMonth'] !== $birthMonth || $existing['dostBirthYear'] !== $birthYear
        ) return false;
    }
    $cohort = $age >= 18 ? 'adult' : 'teen';
    if (isset($existing['dostAgeCohort'])
        && (!in_array($existing['dostAgeCohort'], array('teen', 'adult'), true)
            || ($existing['dostAgeCohort'] === 'adult' && $cohort !== 'adult'))
    ) {
        return false;
    }
    $existing['dostAgeCohort'] = $cohort;
    $existing['dostBirthMonth'] = $birthMonth;
    $existing['dostBirthYear'] = $birthYear;
    return $existing;
}

function dost_firebase_claim_lock($uid)
{
    if (!is_string($uid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1) {
        return false;
    }
    $directory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'dost-firebase-claim-locks';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    $path = $directory . DIRECTORY_SEPARATOR . hash('sha256', $uid) . '.lock';
    $handle = @fopen($path, 'c');
    if ($handle === false) {
        return false;
    }
    @chmod($path, 0600);
    $deadline = microtime(true) + 5;
    do {
        if (flock($handle, LOCK_EX | LOCK_NB)) {
            return $handle;
        }
        usleep(10000);
    } while (microtime(true) < $deadline);
    fclose($handle);
    return false;
}

function dost_firebase_claim_unlock($handle)
{
    if (is_resource($handle)) {
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

function dost_age_http_json($url, $headers, $payload)
{
    if (!function_exists('curl_init')) {
        return false;
    }
    $encoded = json_encode($payload, JSON_UNESCAPED_SLASHES);
    if (!is_string($encoded)) {
        return false;
    }
    $curl = curl_init($url);
    if ($curl === false) {
        return false;
    }
    curl_setopt_array($curl, array(
        CURLOPT_POST => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => array_merge(array('Content-Type: application/json'), $headers),
        CURLOPT_POSTFIELDS => $encoded,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_FOLLOWLOCATION => false
    ));
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
    $tooLarge = is_string($response) && strlen($response) > 262144;
    curl_close($curl);

    if (!is_string($response) || $tooLarge) {
        return false;
    }
    $decoded = json_decode($response, true);
    return array(
        'status' => $status,
        'body' => is_array($decoded) ? $decoded : array()
    );
}

function dost_age_http_form($url, $payload)
{
    if (!function_exists('curl_init')) {
        return false;
    }
    $curl = curl_init($url);
    if ($curl === false) {
        return false;
    }
    curl_setopt_array($curl, array(
        CURLOPT_POST => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => array('Content-Type: application/x-www-form-urlencoded'),
        CURLOPT_POSTFIELDS => http_build_query($payload),
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
        CURLOPT_FOLLOWLOCATION => false
    ));
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
    $tooLarge = is_string($response) && strlen($response) > 262144;
    curl_close($curl);

    if (!is_string($response) || $tooLarge) {
        return false;
    }
    $decoded = json_decode($response, true);
    return array(
        'status' => $status,
        'body' => is_array($decoded) ? $decoded : array()
    );
}

function dost_age_service_account_token($credentials)
{
    if (!is_array($credentials)
        || !isset($credentials['client_email'], $credentials['private_key'])
        || !is_string($credentials['client_email'])
        || !is_string($credentials['private_key'])
    ) {
        return false;
    }
    $now = time();
    $header = dost_age_base64url(json_encode(array('alg' => 'RS256', 'typ' => 'JWT')));
    $claims = dost_age_base64url(json_encode(array(
        'iss' => $credentials['client_email'],
        'scope' => 'https://www.googleapis.com/auth/playintegrity https://www.googleapis.com/auth/identitytoolkit',
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
    $assertion = $unsigned . '.' . dost_age_base64url($signature);
    $response = dost_age_http_form('https://oauth2.googleapis.com/token', array(
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $assertion
    ));
    if ($response === false || $response['status'] !== 200
        || !isset($response['body']['access_token'])
        || !is_string($response['body']['access_token'])
    ) {
        return false;
    }
    return $response['body']['access_token'];
}

function dost_age_rate_limit_allows($ip, $now)
{
    if (!is_string($ip) || $ip === '' || !is_int($now)) {
        return false;
    }
    $directory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'dost-age-eligibility-rate-limit';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    @chmod($directory, 0700);
    $path = $directory . DIRECTORY_SEPARATOR . 'requests.json';
    $handle = @fopen($path, 'c+');
    if ($handle === false) {
        return false;
    }
    if (!flock($handle, LOCK_EX)) {
        fclose($handle);
        return false;
    }
    $contents = stream_get_contents($handle);
    $requestsByIp = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($requestsByIp)) {
        $requestsByIp = array();
    }
    foreach ($requestsByIp as $key => $timestamps) {
        if (!is_array($timestamps)) {
            unset($requestsByIp[$key]);
            continue;
        }
        $recent = array_values(array_filter($timestamps, function ($timestamp) use ($now) {
            return is_int($timestamp) && $timestamp > $now - 60;
        }));
        if (count($recent) === 0) {
            unset($requestsByIp[$key]);
        } else {
            $requestsByIp[$key] = $recent;
        }
    }
    $ipKey = hash('sha256', $ip);
    $timestamps = isset($requestsByIp[$ipKey]) ? $requestsByIp[$ipKey] : array();
    $allowed = count($timestamps) < 10;
    if ($allowed) {
        $timestamps[] = $now;
        $requestsByIp[$ipKey] = $timestamps;
    }
    $encoded = json_encode($requestsByIp);
    if (!is_string($encoded)) {
        $allowed = false;
    } else {
        rewind($handle);
        $allowed = ftruncate($handle, 0)
            && fwrite($handle, $encoded) === strlen($encoded)
            && fflush($handle)
            && $allowed;
    }
    @chmod($path, 0600);
    flock($handle, LOCK_UN);
    fclose($handle);
    return $allowed;
}
