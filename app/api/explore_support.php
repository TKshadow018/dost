<?php

require_once __DIR__ . '/age_eligibility_support.php';
require_once __DIR__ . '/premium_support.php';

if (!defined('DOST_MAX_CUSTOM_FRIENDS')) {
    define('DOST_MAX_CUSTOM_FRIENDS', 3);
}
if (!defined('DOST_MAX_CATALOG_FRIENDS')) {
    define('DOST_MAX_CATALOG_FRIENDS', 7);
}

function dost_explore_setting($environmentKey, $configKey, $config)
{
    $value = getenv($environmentKey);
    if (is_string($value) && trim($value) !== '') {
        return trim($value);
    }
    return isset($config[$configKey]) && is_string($config[$configKey])
        ? trim($config[$configKey])
        : '';
}

function dost_explore_enabled($config)
{
    $value = getenv('DOST_EXPLORE_ENABLED');
    return is_string($value) && strtolower(trim($value)) === 'true';
}

function dost_explore_authorization_header($server)
{
    foreach (array('HTTP_AUTHORIZATION', 'REDIRECT_HTTP_AUTHORIZATION') as $key) {
        if (isset($server[$key]) && is_string($server[$key])) {
            return $server[$key];
        }
    }
    return '';
}

function dost_explore_valid_uuid($value)
{
    return is_string($value)
        && preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/i', $value) === 1;
}

function dost_explore_storage_directory($config)
{
    $directory = dost_explore_setting('DOST_EXPLORE_STORAGE_DIR', 'explore_storage_directory', $config);
    if ($directory === '') {
        return false;
    }
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    $realPath = realpath($directory);
    $documentRoot = isset($_SERVER['DOCUMENT_ROOT']) && is_string($_SERVER['DOCUMENT_ROOT'])
        ? realpath($_SERVER['DOCUMENT_ROOT'])
        : false;
    if (!is_string($realPath)
        || ($documentRoot !== false
            && ($realPath === $documentRoot
                || strpos($realPath, rtrim($documentRoot, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR) === 0))
    ) {
        return false;
    }
    @chmod($realPath, 0700);
    return $realPath;
}

function dost_explore_authorize($authorization, $config, $now)
{
    if (!is_string($authorization)
        || preg_match('/\ABearer\s+(\S+)\z/i', trim($authorization), $matches) !== 1
        || strlen($matches[1]) > 8192
    ) {
        return array('status' => 401, 'error' => 'Sign-in required');
    }
    $projectId = dost_explore_setting('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id', $config);
    $apiKey = dost_explore_setting('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key', $config);
    $serviceAccountPath = dost_explore_setting(
        'DOST_PREMIUM_SERVICE_ACCOUNT_PATH',
        'premium_service_account_path',
        $config
    );
    if ($serviceAccountPath === '') {
        $serviceAccountPath = dost_explore_setting(
            'DOST_FIREBASE_SERVICE_ACCOUNT_PATH',
            'firebase_service_account_path',
            $config
        );
    }
    if (!is_int($now)) {
        return array('status' => 503, 'error' => 'Explore is unavailable');
    }
    if ($projectId === '' || $apiKey === '' || $serviceAccountPath === ''
        || !is_file($serviceAccountPath) || !is_readable($serviceAccountPath)
    ) {
        return array('status' => 503, 'error' => 'Explore is not configured');
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
        return array('status' => 503, 'error' => 'Explore is not configured');
    }
    $contents = file_get_contents($realPath);
    $credentials = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($credentials) || !isset($credentials['project_id'])
        || $credentials['project_id'] !== $projectId
    ) {
        return array('status' => 503, 'error' => 'Explore is not configured');
    }
    $identity = dost_age_http_json(
        'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($apiKey),
        array(),
        array('idToken' => $matches[1])
    );
    if ($identity === false) {
        return array('status' => 503, 'error' => 'Explore identity verification is unavailable');
    }
    if ($identity['status'] !== 200 || !isset($identity['body']['users'][0]['localId'])
        || !is_string($identity['body']['users'][0]['localId'])
        || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $identity['body']['users'][0]['localId']) !== 1
    ) {
        return array('status' => 401, 'error' => 'Sign-in required');
    }
    $uid = $identity['body']['users'][0]['localId'];
    $serviceToken = dost_age_service_account_token($credentials);
    if (!is_string($serviceToken)) {
        return array('status' => 503, 'error' => 'Explore identity verification is unavailable');
    }
    $account = dost_age_http_json(
        'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:lookup',
        array('Authorization: Bearer ' . $serviceToken),
        array('localId' => array($uid))
    );
    if ($account === false || $account['status'] !== 200
        || !isset($account['body']['users'][0]['localId'])
        || !is_string($account['body']['users'][0]['localId'])
        || !hash_equals($uid, $account['body']['users'][0]['localId'])
    ) {
        return array('status' => 503, 'error' => 'Explore identity verification is unavailable');
    }
    $attributes = $account['body']['users'][0]['customAttributes'] ?? '{}';
    $claims = is_string($attributes) ? json_decode($attributes, true) : null;
    if (!is_array($claims)) {
        return array('status' => 503, 'error' => 'Explore identity verification is unavailable');
    }
    return array('status' => 200, 'uid' => $uid, 'claims' => $claims);
}

function dost_explore_plus_active($claims, $now)
{
    return dost_explore_plus_claim_active($claims, $now);
}

function dost_explore_registry_change($directory, $uid, $friendId, $kind, $action, $hasPlus)
{
    if (!is_string($directory) || $directory === ''
        || !is_string($uid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1
        || !is_string($friendId) || preg_match('/\A[A-Za-z0-9_-]{1,80}\z/', $friendId) !== 1
        || !in_array($kind, array('custom', 'catalog'), true)
        || !in_array($action, array('add', 'remove'), true)
        || !is_bool($hasPlus)
    ) {
        return array('status' => 400, 'error' => 'Invalid friend registration');
    }
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return array('status' => 503, 'error' => 'Explore friend storage is unavailable');
    }
    $path = rtrim($directory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
        . hash('sha256', $uid) . '.json';
    $lock = @fopen($path . '.lock', 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        return array('status' => 503, 'error' => 'Explore friend storage is unavailable');
    }
    @chmod($path . '.lock', 0600);
    if (is_file($path) && filesize($path) > 262144) {
        flock($lock, LOCK_UN);
        fclose($lock);
        return array('status' => 503, 'error' => 'Explore friend storage is unavailable');
    }
    $contents = is_file($path) ? file_get_contents($path) : '{}';
    $registry = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($registry)
        || (isset($registry['custom']) && !is_array($registry['custom']))
        || (isset($registry['catalog']) && !is_array($registry['catalog']))
    ) {
        flock($lock, LOCK_UN);
        fclose($lock);
        return array('status' => 503, 'error' => 'Explore friend storage is unavailable');
    }
    $registry += array('custom' => array(), 'catalog' => array());
    $ids = array_values(array_unique(array_filter($registry[$kind], function ($id) {
        return is_string($id) && preg_match('/\A[A-Za-z0-9_-]{1,80}\z/', $id) === 1;
    })));
    if ($action === 'add' && !in_array($friendId, $ids, true)) {
        $maximum = $kind === 'custom' ? DOST_MAX_CUSTOM_FRIENDS : DOST_MAX_CATALOG_FRIENDS;
        if (!$hasPlus && count($ids) >= $maximum) {
            flock($lock, LOCK_UN);
            fclose($lock);
            return array('status' => 403, 'error' => 'The free Explore friend limit has been reached');
        }
        if (count($ids) >= 1000) {
            flock($lock, LOCK_UN);
            fclose($lock);
            return array('status' => 403, 'error' => 'The friend limit has been reached');
        }
        $ids[] = $friendId;
    } elseif ($action === 'remove') {
        $ids = array_values(array_filter($ids, function ($id) use ($friendId) {
            return $id !== $friendId;
        }));
    }
    $registry[$kind] = $ids;
    $encoded = json_encode($registry, JSON_UNESCAPED_SLASHES);
    $temporaryPath = $path . '.' . bin2hex(random_bytes(8)) . '.tmp';
    $saved = is_string($encoded) && file_put_contents($temporaryPath, $encoded, LOCK_EX) === strlen($encoded);
    if ($saved) {
        @chmod($temporaryPath, 0600);
        $saved = @rename($temporaryPath, $path);
    }
    if (!$saved) {
        @unlink($temporaryPath);
    }
    flock($lock, LOCK_UN);
    fclose($lock);
    return $saved
        ? array('status' => 200, 'registered' => $action === 'add')
        : array('status' => 503, 'error' => 'Explore friend storage is unavailable');
}

function dost_explore_registry_contains($directory, $uid, $friendId, $kind)
{
    if (!is_string($directory) || $directory === ''
        || !is_string($uid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1
        || !is_string($friendId) || preg_match('/\A[A-Za-z0-9_-]{1,80}\z/', $friendId) !== 1
        || !in_array($kind, array('custom', 'catalog'), true)
    ) {
        return false;
    }
    $path = rtrim($directory, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
        . hash('sha256', $uid) . '.json';
    $lock = @fopen($path . '.lock', 'c');
    if ($lock === false || !flock($lock, LOCK_SH)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        return false;
    }
    $contents = is_file($path) && filesize($path) <= 262144 ? file_get_contents($path) : false;
    $registry = is_string($contents) ? json_decode($contents, true) : null;
    $ids = is_array($registry) && isset($registry[$kind]) && is_array($registry[$kind])
        ? $registry[$kind]
        : array();
    $found = in_array($friendId, $ids, true);
    flock($lock, LOCK_UN);
    fclose($lock);
    return $found;
}
