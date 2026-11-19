<?php

function dost_call_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

function dost_call_read_request()
{
    if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
        return false;
    }
    $rawBody = file_get_contents('php://input');
    if (!is_string($rawBody) || strlen($rawBody) > 16384) {
        return false;
    }
    $request = json_decode($rawBody, true);
    if (!is_array($request)
        || !isset($request['chat_id'], $request['call_id'])
        || !is_string($request['chat_id'])
        || !is_string($request['call_id'])
        || preg_match('/\A[A-Za-z0-9_-]{1,128}\z/', $request['chat_id']) !== 1
        || preg_match('/\A[A-Za-z0-9_-]{1,128}\z/', $request['call_id']) !== 1
    ) {
        return false;
    }
    return $request;
}

function dost_call_bearer_token()
{
    $authorization = isset($_SERVER['HTTP_AUTHORIZATION']) && is_string($_SERVER['HTTP_AUTHORIZATION'])
        ? trim($_SERVER['HTTP_AUTHORIZATION'])
        : (isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && is_string($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
            ? trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])
            : '');
    if (preg_match('/\ABearer\s+(\S+)\z/i', $authorization, $matches) !== 1
        || strlen($matches[1]) > 8192
    ) {
        return '';
    }
    return $matches[1];
}

function dost_call_config()
{
    $config = array();
    $configPath = __DIR__ . '/config.php';
    if (is_file($configPath)) {
        $loaded = require $configPath;
        if (is_array($loaded)) {
            $config = $loaded;
        }
    }
    $value = function ($environmentKey, $configKey) use ($config) {
        $environmentValue = getenv($environmentKey);
        if (is_string($environmentValue) && trim($environmentValue) !== '') {
            return trim($environmentValue);
        }
        return isset($config[$configKey]) && is_string($config[$configKey])
            ? trim($config[$configKey])
            : '';
    };

    $settings = array(
        'project_id' => $value('DOST_FIREBASE_PROJECT_ID', 'firebase_project_id'),
        'api_key' => $value('DOST_FIREBASE_WEB_API_KEY', 'firebase_web_api_key'),
        'service_account_path' => $value('DOST_CALL_SERVICE_ACCOUNT_PATH', 'call_service_account_path'),
        'turn_urls' => $value('DOST_TURN_URLS', 'turn_urls'),
        'turn_secret' => $value('DOST_TURN_SHARED_SECRET', 'turn_shared_secret'),
        'cloudflare_turn_key_id' => $value('DOST_CLOUDFLARE_TURN_KEY_ID', 'cloudflare_turn_key_id'),
        'cloudflare_turn_api_token' => $value('DOST_CLOUDFLARE_TURN_API_TOKEN', 'cloudflare_turn_api_token')
    );
    if ($settings['project_id'] === '' || $settings['api_key'] === ''
        || $settings['service_account_path'] === ''
        || !is_file($settings['service_account_path'])
        || !is_readable($settings['service_account_path'])
    ) {
        return false;
    }
    $realPath = realpath($settings['service_account_path']);
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
    $contents = file_get_contents($realPath);
    $credentials = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($credentials)
        || !isset($credentials['project_id'], $credentials['client_email'], $credentials['private_key'])
        || $credentials['project_id'] !== $settings['project_id']
        || !is_string($credentials['client_email'])
        || !is_string($credentials['private_key'])
    ) {
        return false;
    }
    $settings['credentials'] = $credentials;
    $settings['turn_urls'] = array_values(array_filter(
        array_map('trim', explode(',', $settings['turn_urls'])),
        function ($url) {
            return $url !== '';
        }
    ));
    return $settings;
}

function dost_call_base64url($value)
{
    return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
}

function dost_call_service_account_token($credentials, $now = null)
{
    if (!is_array($credentials)
        || !isset($credentials['client_email'], $credentials['private_key'])
        || !is_string($credentials['client_email'])
        || !is_string($credentials['private_key'])
    ) {
        return false;
    }
    $now = is_int($now) ? $now : time();
    $header = dost_call_base64url(json_encode(array('alg' => 'RS256', 'typ' => 'JWT')));
    $claims = dost_call_base64url(json_encode(array(
        'iss' => $credentials['client_email'],
        'scope' => 'https://www.googleapis.com/auth/identitytoolkit https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging',
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
    $response = dost_call_http_json('POST', 'https://oauth2.googleapis.com/token', array(), array(
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion' => $unsigned . '.' . dost_call_base64url($signature)
    ));
    if ($response === false || $response['status'] !== 200
        || !isset($response['body']['access_token'])
        || !is_string($response['body']['access_token'])
    ) {
        return false;
    }
    return $response['body']['access_token'];
}

function dost_call_http_json($method, $url, $headers = array(), $payload = null, $timeoutSeconds = 20)
{
    if (!function_exists('curl_init')) {
        return false;
    }
    $timeoutSeconds = is_int($timeoutSeconds) ? max(1, min(30, $timeoutSeconds)) : 20;
    $curl = curl_init($url);
    if ($curl === false) {
        return false;
    }
    $options = array(
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => array_merge(array('Accept: application/json'), $headers),
        CURLOPT_CONNECTTIMEOUT => min(10, $timeoutSeconds),
        CURLOPT_TIMEOUT => $timeoutSeconds,
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

function dost_call_firestore_url($projectId, $path, $query = '')
{
    $segments = explode('/', trim($path, '/'));
    $encoded = implode('/', array_map('rawurlencode', $segments));
    $url = 'https://firestore.googleapis.com/v1/projects/' . rawurlencode($projectId)
        . '/databases/(default)/documents/' . $encoded;
    return $query === '' ? $url : $url . '?' . $query;
}

function dost_call_firestore_get($projectId, $serviceToken, $path)
{
    return dost_call_http_json('GET', dost_call_firestore_url($projectId, $path), array(
        'Authorization: Bearer ' . $serviceToken
    ));
}

function dost_call_firestore_delete($projectId, $serviceToken, $path)
{
    return dost_call_http_json('DELETE', dost_call_firestore_url($projectId, $path), array(
        'Authorization: Bearer ' . $serviceToken
    ));
}

function dost_call_decode_firestore_value($value)
{
    if (!is_array($value)) {
        return null;
    }
    foreach (array('stringValue', 'booleanValue', 'integerValue', 'doubleValue', 'timestampValue', 'nullValue') as $key) {
        if (array_key_exists($key, $value)) {
            if ($key === 'integerValue') {
                return is_numeric($value[$key]) ? (int) $value[$key] : null;
            }
            if ($key === 'timestampValue') {
                $timestamp = is_string($value[$key]) ? strtotime($value[$key]) : false;
                return $timestamp === false ? null : $timestamp;
            }
            return $value[$key];
        }
    }
    if (isset($value['arrayValue']['values']) && is_array($value['arrayValue']['values'])) {
        return array_map('dost_call_decode_firestore_value', $value['arrayValue']['values']);
    }
    if (isset($value['mapValue']['fields']) && is_array($value['mapValue']['fields'])) {
        return dost_call_decode_firestore_fields($value['mapValue']['fields']);
    }
    return null;
}

function dost_call_decode_firestore_fields($fields)
{
    if (!is_array($fields)) {
        return array();
    }
    $decoded = array();
    foreach ($fields as $key => $value) {
        $decoded[$key] = dost_call_decode_firestore_value($value);
    }
    return $decoded;
}

function dost_call_document_fields($response)
{
    return is_array($response)
        && isset($response['body']['fields'])
        && is_array($response['body']['fields'])
        ? dost_call_decode_firestore_fields($response['body']['fields'])
        : array();
}

function dost_call_valid_context($call, $chat, $uid, $now, $requireRinging)
{
    if (!is_array($call) || !is_array($chat)
        || !isset($chat['members'], $chat['ageCohort'], $call['callerUid'], $call['calleeUid'],
            $call['status'], $call['expiresAt'], $call['ringExpiresAt'])
        || (array_key_exists('mediaType', $call)
            && !in_array($call['mediaType'], array('audio', 'video'), true))
        || !is_array($chat['members'])
        || count($chat['members']) !== 2
        || $chat['members'][0] === $chat['members'][1]
        || $chat['ageCohort'] !== 'adult'
        || !in_array($call['callerUid'], $chat['members'], true)
        || !in_array($call['calleeUid'], $chat['members'], true)
        || $call['callerUid'] === $call['calleeUid']
        || !in_array($uid, array($call['callerUid'], $call['calleeUid']), true)
        || !is_int($call['expiresAt'])
        || $call['expiresAt'] <= $now
        || !is_int($call['ringExpiresAt'])
    ) {
        return false;
    }
    if ($requireRinging) {
        return $call['status'] === 'ringing' && $call['ringExpiresAt'] > $now;
    }
    return in_array($call['status'], array('ringing', 'accepted'), true)
        && ($call['status'] !== 'ringing' || $call['ringExpiresAt'] > $now);
}

function dost_call_user_is_eligible($projectId, $serviceToken, $uid, $now)
{
    $response = dost_call_http_json(
        'POST',
        'https://identitytoolkit.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/accounts:lookup',
        array('Authorization: Bearer ' . $serviceToken),
        array('localId' => array($uid))
    );
    if ($response === false || $response['status'] !== 200
        || !isset($response['body']['users'][0]['localId'])
        || !is_string($response['body']['users'][0]['localId'])
        || !hash_equals($uid, $response['body']['users'][0]['localId'])
    ) {
        return false;
    }
    $customAttributes = $response['body']['users'][0]['customAttributes'] ?? '{}';
    if (!is_string($customAttributes)) {
        return false;
    }
    $claims = json_decode($customAttributes, true);
    return is_array($claims)
        && isset($claims['dostAgeEligible'], $claims['dostAgeEligibleUntil'])
        && $claims['dostAgeEligible'] === true
        && is_numeric($claims['dostAgeEligibleUntil'])
        && (int) $claims['dostAgeEligibleUntil'] > $now;
}

function dost_call_authorize($settings, $idToken, $chatId, $callId, $requireRinging, $rateLimitScope = '', $rateLimit = 0)
{
    $identity = dost_call_http_json(
        'POST',
        'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' . rawurlencode($settings['api_key']),
        array(),
        array('idToken' => $idToken)
    );
    if ($identity === false || $identity['status'] !== 200
        || !isset($identity['body']['users'][0]['localId'])
        || !is_string($identity['body']['users'][0]['localId'])
    ) {
        return array('status' => 401, 'message' => 'Sign-in required');
    }
    $uid = $identity['body']['users'][0]['localId'];
    if (preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1) {
        return array('status' => 401, 'message' => 'Sign-in required');
    }
    if ($rateLimitScope !== ''
        && !dost_call_rate_limit_allows('uid:' . $uid . ':' . $rateLimitScope, $rateLimit, 60, time())
    ) {
        return array('status' => 429, 'message' => 'Too many call requests');
    }
    $serviceToken = dost_call_service_account_token($settings['credentials']);
    if (!is_string($serviceToken)) {
        error_log('Dost People call service account token exchange failed.');
        return array('status' => 503, 'message' => 'People calling is unavailable');
    }
    $callResponse = dost_call_firestore_get(
        $settings['project_id'],
        $serviceToken,
        'peopleChats/' . $chatId . '/calls/' . $callId
    );
    $chatResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, 'peopleChats/' . $chatId);
    if ($callResponse === false || $chatResponse === false
        || $callResponse['status'] !== 200 || $chatResponse['status'] !== 200
    ) {
        if ($callResponse !== false && $chatResponse !== false
            && ($callResponse['status'] === 404 || $chatResponse['status'] === 404)
        ) {
            return array('status' => 403, 'message' => 'This call is not available');
        }
        error_log('Dost People call authorization could not read Firestore.');
        return array('status' => 503, 'message' => 'People calling is unavailable');
    }
    $call = dost_call_document_fields($callResponse);
    $chat = dost_call_document_fields($chatResponse);
    $now = time();
    if (!dost_call_valid_context($call, $chat, $uid, $now, $requireRinging)) {
        return array('status' => 403, 'message' => 'This call is not available');
    }
    $callerUid = $call['callerUid'];
    $calleeUid = $call['calleeUid'];
    if (!dost_call_user_is_eligible($settings['project_id'], $serviceToken, $callerUid, $now)
        || !dost_call_user_is_eligible($settings['project_id'], $serviceToken, $calleeUid, $now)
    ) {
        return array('status' => 403, 'message' => 'This call is not available');
    }
    foreach (array(
        'peopleBlocks/' . $callerUid . '/blockedUsers/' . $calleeUid,
        'peopleBlocks/' . $calleeUid . '/blockedUsers/' . $callerUid
    ) as $blockPath) {
        $blockResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, $blockPath);
        if ($blockResponse === false) {
            error_log('Dost People call authorization could not check block state.');
            return array('status' => 503, 'message' => 'People calling is unavailable');
        }
        if ($blockResponse['status'] === 200) {
            return array('status' => 403, 'message' => 'This call is not available');
        }
        if ($blockResponse['status'] !== 404) {
            error_log('Dost People call authorization received an unexpected block lookup response.');
            return array('status' => 503, 'message' => 'People calling is unavailable');
        }
    }
    return array(
        'status' => 200,
        'uid' => $uid,
        'peer_uid' => $uid === $callerUid ? $calleeUid : $callerUid,
        'call' => $call,
        'chat' => $chat,
        'service_token' => $serviceToken
    );
}

function dost_call_turn_ttl_seconds()
{
    return 3600;
}

function dost_call_cloudflare_turn_settings_state($settings)
{
    if (!is_array($settings)) {
        return 'absent';
    }
    $keyId = isset($settings['cloudflare_turn_key_id']) && is_string($settings['cloudflare_turn_key_id'])
        ? $settings['cloudflare_turn_key_id']
        : '';
    $apiToken = isset($settings['cloudflare_turn_api_token']) && is_string($settings['cloudflare_turn_api_token'])
        ? $settings['cloudflare_turn_api_token']
        : '';
    if ($keyId === '' && $apiToken === '') {
        return 'absent';
    }
    if (preg_match('/\A[A-Za-z0-9][A-Za-z0-9_-]{7,127}\z/', $keyId) !== 1
        || preg_match('/\A[\x21-\x7E]{20,512}\z/', $apiToken) !== 1
    ) {
        return 'incomplete';
    }
    return 'configured';
}

function dost_call_coturn_configured($settings)
{
    return is_array($settings)
        && isset($settings['turn_urls'], $settings['turn_secret'])
        && is_array($settings['turn_urls'])
        && count($settings['turn_urls']) > 0
        && is_string($settings['turn_secret'])
        && strlen($settings['turn_secret']) >= 32;
}

function dost_call_valid_ice_url($url)
{
    return is_string($url)
        && preg_match('/\A(?:stun|stuns|turn|turns):[A-Za-z0-9.\-\[\]:]+(?:\?[A-Za-z0-9=&._-]+)?\z/', $url) === 1;
}

function dost_call_ice_url_needs_credentials($url)
{
    return is_string($url) && (strpos($url, 'turn:') === 0 || strpos($url, 'turns:') === 0);
}

function dost_call_normalize_cloudflare_ice_servers($body)
{
    if (!is_array($body) || !isset($body['iceServers']) || !is_array($body['iceServers'])
        || count($body['iceServers']) < 1 || count($body['iceServers']) > 8
    ) {
        return false;
    }
    $servers = array();
    $turnUrlCount = 0;
    foreach ($body['iceServers'] as $server) {
        if (!is_array($server) || !isset($server['urls'])) {
            return false;
        }
        $urls = $server['urls'];
        if (is_string($urls)) {
            $urls = array($urls);
        }
        if (!is_array($urls) || count($urls) < 1 || count($urls) > 16) {
            return false;
        }
        $cleanUrls = array();
        $needsCredentials = false;
        foreach ($urls as $url) {
            if (!dost_call_valid_ice_url($url)) {
                return false;
            }
            if (dost_call_ice_url_needs_credentials($url)) {
                $needsCredentials = true;
                $turnUrlCount++;
            }
            $cleanUrls[] = $url;
        }
        $username = isset($server['username']) && is_string($server['username']) ? $server['username'] : '';
        $credential = isset($server['credential']) && is_string($server['credential']) ? $server['credential'] : '';
        if (preg_match('/[\x00-\x1F\x7F]/', $username . $credential) === 1
            || strlen($username) > 512
            || strlen($credential) > 512
        ) {
            return false;
        }
        $entry = array('urls' => $cleanUrls);
        if ($needsCredentials || $username !== '' || $credential !== '') {
            if ($username === '' || $credential === '') {
                return false;
            }
            $entry['username'] = $username;
            $entry['credential'] = $credential;
        }
        $servers[] = $entry;
    }
    if ($turnUrlCount < 1) {
        return false;
    }
    return $servers;
}

function dost_call_cloudflare_ice_servers($keyId, $apiToken, $ttlSeconds = 3600, $transport = null)
{
    if (preg_match('/\A[A-Za-z0-9][A-Za-z0-9_-]{7,127}\z/', $keyId) !== 1
        || preg_match('/\A[\x21-\x7E]{20,512}\z/', $apiToken) !== 1
        || !is_int($ttlSeconds) || $ttlSeconds < 60 || $ttlSeconds > 3600
    ) {
        return false;
    }
    $transport = is_callable($transport) ? $transport : 'dost_call_http_json';
    $response = call_user_func(
        $transport,
        'POST',
        'https://rtc.live.cloudflare.com/v1/turn/keys/' . rawurlencode($keyId) . '/credentials/generate-ice-servers',
        array('Authorization: Bearer ' . $apiToken),
        array('ttl' => $ttlSeconds),
        8
    );
    if (!is_array($response) || !isset($response['status'], $response['body'])
        || ($response['status'] !== 200 && $response['status'] !== 201)
    ) {
        return false;
    }
    $servers = dost_call_normalize_cloudflare_ice_servers($response['body']);
    if ($servers === false) {
        return false;
    }
    return array(
        'ice_servers' => $servers,
        'expires_in' => $ttlSeconds
    );
}

function dost_call_issue_ice_servers($settings, $uid, $now, $transport = null)
{
    $cloudflareState = dost_call_cloudflare_turn_settings_state($settings);
    if ($cloudflareState === 'configured') {
        return dost_call_cloudflare_ice_servers(
            $settings['cloudflare_turn_key_id'],
            $settings['cloudflare_turn_api_token'],
            dost_call_turn_ttl_seconds(),
            $transport
        );
    }
    if ($cloudflareState === 'incomplete' || !dost_call_coturn_configured($settings)) {
        return false;
    }
    $credentials = dost_call_turn_credentials(
        $settings['turn_urls'],
        $settings['turn_secret'],
        $uid,
        $now,
        dost_call_turn_ttl_seconds()
    );
    if ($credentials === false) {
        return false;
    }
    return array(
        'ice_servers' => $credentials,
        'expires_in' => dost_call_turn_ttl_seconds()
    );
}

function dost_call_turn_credentials($urls, $secret, $uid, $now, $ttlSeconds)
{
    if (!is_array($urls) || count($urls) < 1 || count($urls) > 8
        || !is_string($secret) || strlen($secret) < 32
        || !is_string($uid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1
        || !is_int($now) || !is_int($ttlSeconds) || $ttlSeconds < 60 || $ttlSeconds > 7200
    ) {
        return false;
    }
    foreach ($urls as $url) {
        if (!is_string($url)
            || preg_match('/\Aturns?:[A-Za-z0-9.\-\[\]:]+(?:\?[A-Za-z0-9=&._-]+)?\z/', $url) !== 1
        ) {
            return false;
        }
    }
    $username = ($now + $ttlSeconds) . ':' . $uid;
    return array(array(
        'urls' => array_values($urls),
        'username' => $username,
        'credential' => base64_encode(hash_hmac('sha1', $username, $secret, true))
    ));
}

function dost_call_rate_limit_allows($key, $limit, $windowSeconds, $now)
{
    if (!is_string($key) || $key === '' || !is_int($limit) || $limit < 1
        || !is_int($windowSeconds) || $windowSeconds < 1 || !is_int($now)
    ) {
        return false;
    }
    $directory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . 'dost-people-call-rate-limit';
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    @chmod($directory, 0700);
    $path = $directory . DIRECTORY_SEPARATOR . hash('sha256', $key) . '.json';
    $handle = @fopen($path, 'c+');
    if ($handle === false) {
        return false;
    }
    if (!flock($handle, LOCK_EX)) {
        fclose($handle);
        return false;
    }
    $contents = stream_get_contents($handle);
    $timestamps = is_string($contents) ? json_decode($contents, true) : null;
    if (!is_array($timestamps)) {
        $timestamps = array();
    }
    $timestamps = array_values(array_filter($timestamps, function ($timestamp) use ($now, $windowSeconds) {
        return is_int($timestamp) && $timestamp > $now - $windowSeconds;
    }));
    $allowed = count($timestamps) < $limit;
    if ($allowed) {
        $timestamps[] = $now;
    }
    rewind($handle);
    $encoded = json_encode($timestamps);
    if (!is_string($encoded) || !ftruncate($handle, 0) || fwrite($handle, $encoded) === false) {
        $allowed = false;
    }
    fflush($handle);
    flock($handle, LOCK_UN);
    fclose($handle);
    return $allowed;
}
