<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

/**
 * Checks the server-key credential gate, including one HTTP request that must
 * stop before OpenRouter is called.
 */

function dost_cred_fail($message)
{
    fwrite(STDERR, "FAIL: {$message}\n");
    exit(1);
}

function dost_cred_unset($name)
{
    putenv($name);
    unset($_ENV[$name], $_SERVER[$name]);
}

require_once __DIR__ . '/app_credential.php';

$secret = 'test-secret-value-0123456789';
$install = '11111111-1111-4111-8111-111111111111';
$timestamp = 1700000000;
$vector = '6583f387a240d44cb150b813082463e6a72dea26ae6cbec862b5d90152e87e61';
$computed = hash_hmac('sha256', "v1\n{$timestamp}\n{$install}", $secret);
if ($computed !== $vector) {
    dost_cred_fail('HMAC payload no longer matches the client test vector.');
}
$token = 'v1.' . $timestamp . '.' . $vector;
$config = array('app_credential_secret' => $secret);
$userKey = 'sk-or-v1-do-not-log-this-key';

dost_cred_unset('DOST_APP_CREDENTIAL_SECRET');
dost_cred_unset('DOST_ALLOW_LEGACY_SERVER_KEY');
dost_cred_unset('OPENROUTER_API_KEY');

if (dost_server_key_decision(false, '', '', $timestamp, array()) === null) {
    dost_cred_fail('A missing server secret allowed an unauthenticated server-key request.');
}
$unconfigured = dost_server_key_decision(false, '', '', $timestamp, array('app_credential_secret' => 'short'));
if (!is_array($unconfigured) || $unconfigured['status'] !== 503 || strpos(json_encode($unconfigured), $secret) !== false) {
    dost_cred_fail('A short secret did not fail closed.');
}

$missing = dost_server_key_decision(false, '', $install, $timestamp, $config);
$bad = dost_server_key_decision(false, 'v1.' . $timestamp . '.' . str_repeat('ab', 32), $install, $timestamp, $config);
$expired = dost_server_key_decision(false, $token, $install, $timestamp + 901, $config);
$wrongInstall = dost_server_key_decision(false, $token, '22222222-2222-4222-8222-222222222222', $timestamp, $config);
foreach (array($missing, $bad, $expired, $wrongInstall) as $rejected) {
    $encoded = json_encode($rejected);
    if (!is_array($rejected) || $rejected['status'] !== 401 || $rejected['error'] !== 'App credential required'
        || strpos($encoded, $secret) !== false || strpos($encoded, $userKey) !== false || strpos($encoded, $vector) !== false
    ) {
        dost_cred_fail('Rejected server-key decision leaked a secret or used the wrong status: ' . $encoded);
    }
}

if (dost_server_key_decision(false, $token, $install, $timestamp, $config) !== null) {
    dost_cred_fail('A valid credential was rejected.');
}
if (dost_server_key_decision(false, $token, '  ' . $install . '  ', $timestamp + 900, $config) !== null) {
    dost_cred_fail('A credential inside the fifteen-minute window was rejected.');
}
if (dost_server_key_decision(false, $token, $install, $timestamp + 901, $config) === null) {
    dost_cred_fail('A credential outside the fifteen-minute window was accepted.');
}
if (dost_server_key_decision(true, '', '', $timestamp, $config) !== null) {
    dost_cred_fail('A caller-supplied OpenRouter key was forced through the server-key gate.');
}
putenv('DOST_ALLOW_LEGACY_SERVER_KEY=1');
if (dost_server_key_decision(false, '', '', $timestamp, $config) !== null) {
    dost_cred_fail('The legacy rollout flag did not allow an old client.');
}
dost_cred_unset('DOST_ALLOW_LEGACY_SERVER_KEY');
putenv('DOST_APP_CREDENTIAL_SECRET=env-secret-value-0123456789');
$envToken = 'v1.' . $timestamp . '.' . hash_hmac('sha256', "v1\n{$timestamp}\n{$install}", 'env-secret-value-0123456789');
if (dost_server_key_decision(false, $envToken, $install, $timestamp, $config) !== null) {
    dost_cred_fail('The environment secret did not override config.php.');
}
if (dost_server_key_decision(false, $token, $install, $timestamp, $config) === null) {
    dost_cred_fail('A token for the config secret was accepted while the environment secret was set.');
}
dost_cred_unset('DOST_APP_CREDENTIAL_SECRET');

$client = file_get_contents(dirname(__DIR__) . '/src/main/java/com/snigtus/bondhu/OpenRouterClient.kt');
$gradle = file_get_contents(dirname(__DIR__) . '/build.gradle');
if (!is_string($client) || !is_string($gradle)
    || strpos($client, 'X-Dost-App-Credential') === false
    || strpos($client, 'BuildConfig.DOST_APP_CREDENTIAL_SECRET') === false
    || strpos($client, $secret) !== false
    || strpos($gradle, 'DOST_APP_CREDENTIAL_SECRET') === false
    || strpos($gradle, $secret) !== false
    || strpos($gradle, '?: ""') === false
) {
    dost_cred_fail('The client header is missing, or a test/production secret was committed in source.');
}

if (filter_var(ini_get('allow_url_fopen'), FILTER_VALIDATE_BOOLEAN) !== true && ini_get('allow_url_fopen') !== '1') {
    dost_cred_fail('allow_url_fopen is required for the local HTTP check.');
}

function dost_cred_post($url, $headers)
{
    $headerLines = "Content-Type: application/json\r\n";
    foreach ($headers as $name => $value) {
        $headerLines .= $name . ': ' . $value . "\r\n";
    }
    $context = stream_context_create(array(
        'http' => array(
            'method' => 'POST',
            'header' => $headerLines,
            'content' => '{"model":"openrouter/free","messages":[{"role":"user","content":"hi"}]}',
            'ignore_errors' => true,
            'timeout' => 10
        )
    ));
    $body = @file_get_contents($url, false, $context);
    $status = 0;
    if (isset($http_response_header) && is_array($http_response_header)) {
        foreach ($http_response_header as $header) {
            if (preg_match('/^HTTP\/\S+\s+(\d{3})\b/', $header, $matches) === 1) {
                $status = (int) $matches[1];
            }
        }
    }
    return array($status, is_string($body) ? $body : '');
}

function dost_cred_start_server($port, &$process)
{
    $process = proc_open(
        array('php', '-S', '127.0.0.1:' . $port, '-t', __DIR__),
        array(array('pipe', 'r'), array('file', '/dev/null', 'w'), array('file', '/dev/null', 'w')),
        $pipes,
        __DIR__
    );
    if (!is_resource($process)) {
        return false;
    }
    fclose($pipes[0]);
    for ($attempt = 0; $attempt < 50; $attempt++) {
        $socket = @fsockopen('127.0.0.1', $port, $errno, $errstr, 0.2);
        if (is_resource($socket)) {
            fclose($socket);
            return true;
        }
        usleep(100000);
    }
    proc_terminate($process);
    proc_close($process);
    $process = null;
    return false;
}

function dost_cred_stop_server(&$process)
{
    if (is_resource($process)) {
        proc_terminate($process);
        proc_close($process);
        $process = null;
    }
}

$storage = sys_get_temp_dir() . '/dost-credential-' . bin2hex(random_bytes(4));
if (!mkdir($storage, 0700, true)) {
    dost_cred_fail('Could not create a temporary diagnostic-log directory.');
}
putenv('DOST_CONVERSATION_STORAGE_DIR=' . $storage);
putenv('DOST_APP_CREDENTIAL_SECRET=' . $secret);
dost_cred_unset('OPENROUTER_API_KEY');
dost_cred_unset('DOST_ALLOW_LEGACY_SERVER_KEY');

$port = random_int(20000, 45000);
$server = null;
register_shutdown_function(function () use (&$server) {
    if (is_resource($server)) {
        proc_terminate($server);
        proc_close($server);
    }
});
if (!dost_cred_start_server($port, $server)) {
    dost_cred_fail('Could not start the local API server.');
}
$url = 'http://127.0.0.1:' . $port . '/ai.php';
list($status, $body) = dost_cred_post($url, array());
if ($status !== 401 || strpos($body, 'App credential required') === false
    || strpos($body, $secret) !== false || strpos($body, $userKey) !== false
) {
    dost_cred_stop_server($server);
    dost_cred_fail('Unauthenticated POST was not rejected before using the server key. HTTP ' . $status . ' ' . $body);
}

$now = time();
$liveToken = 'v1.' . $now . '.' . hash_hmac('sha256', "v1\n{$now}\n{$install}", $secret);
list($allowedStatus, $allowedBody) = dost_cred_post($url, array(
    'X-Dost-App-Credential' => $liveToken,
    'X-Dost-Install-ID' => $install
));
dost_cred_stop_server($server);
$diagnostic = $storage . '/ai-errors.jsonl';
$diagnosticText = is_file($diagnostic) ? file_get_contents($diagnostic) : '';
if ($allowedStatus === 401 || strpos($diagnosticText, 'missing_api_key') === false) {
    dost_cred_fail('A valid credential did not pass the gate. HTTP ' . $allowedStatus . ' log=' . $diagnosticText);
}
if (strpos($diagnosticText, 'provider_') !== false || strpos($diagnosticText, 'curl_') !== false) {
    dost_cred_fail('The valid-credential check reached OpenRouter.');
}
if (strpos($diagnosticText, $secret) !== false || strpos($diagnosticText, $vector) !== false || strpos($diagnosticText, $liveToken) !== false) {
    dost_cred_fail('The diagnostic log contains the credential secret or token.');
}
if (strpos($allowedBody, $secret) !== false || strpos($allowedBody, $userKey) !== false) {
    dost_cred_fail('The HTTP body echoed a secret.');
}

dost_cred_unset('DOST_APP_CREDENTIAL_SECRET');
@unlink($diagnostic);
$port = random_int(20000, 45000);
if (!dost_cred_start_server($port, $server)) {
    dost_cred_fail('Could not restart the local API server.');
}
list($closedStatus, $closedBody) = dost_cred_post('http://127.0.0.1:' . $port . '/ai.php', array());
dost_cred_stop_server($server);
$closedLog = is_file($diagnostic) ? file_get_contents($diagnostic) : '';
if ($closedStatus !== 503 || strpos($closedLog, 'app_credential_not_configured') === false) {
    dost_cred_fail('A missing credential secret did not fail closed. HTTP ' . $closedStatus . ' log=' . $closedLog);
}
if (strpos($closedBody, $userKey) !== false || strpos($closedLog, $userKey) !== false || strpos($closedLog, $secret) !== false) {
    dost_cred_fail('A secret was written to a response or the diagnostic log.');
}
if (strpos($closedLog, 'provider_') !== false || strpos($closedLog, 'curl_') !== false) {
    dost_cred_fail('A request with no credential secret called OpenRouter.');
}
if (strpos($closedLog, '"key_source":"server"') === false) {
    dost_cred_fail('The diagnostic log did not record that the server key was the one being protected.');
}

fwrite(STDOUT, "PASS: server-key requests require a valid app credential and do not log the OpenRouter key.\n");
