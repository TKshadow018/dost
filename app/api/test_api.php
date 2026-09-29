<?php

$url = isset($argv[1]) ? $argv[1] : 'https://bisque-bear-900175.hostingersite.com/api/ai.php';
if (!function_exists('http_get_last_response_headers')) {
    fwrite(STDERR, "FAIL: PHP 8.5 or newer is required.\n");
    exit(2);
}

$parts = parse_url($url);
if (!is_array($parts) || !isset($parts['scheme'], $parts['host'])
    || !in_array(strtolower($parts['scheme']), array('http', 'https'), true)
) {
    fwrite(STDERR, "FAIL: Provide a valid HTTP or HTTPS API URL.\n");
    exit(2);
}

$context = stream_context_create(array(
    'http' => array(
        'method' => 'GET',
        'timeout' => 15,
        'ignore_errors' => true
    )
));
$response = @file_get_contents($url, false, $context);
$responseHeaders = http_get_last_response_headers();
if ($response === false || !is_array($responseHeaders)) {
    fwrite(STDERR, "FAIL: Could not reach the API at {$url}.\n");
    exit(1);
}

$status = 0;
foreach ($responseHeaders as $header) {
    if (preg_match('/^HTTP\/\S+\s+(\d{3})\b/', $header, $matches)) {
        $status = (int) $matches[1];
    }
}
$payload = json_decode($response, true);
if ($status !== 405 || !is_array($payload)
    || !isset($payload['error']) || $payload['error'] !== 'POST requests only'
) {
    fwrite(STDERR, "FAIL: Expected HTTP 405 with JSON error \"POST requests only\"; received HTTP {$status}.\n");
    exit(1);
}

fwrite(STDOUT, "PASS: API is reachable and returned the expected HTTP 405 response.\n");