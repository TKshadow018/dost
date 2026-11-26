<?php

require_once __DIR__ . '/archive_store.php';
require_once __DIR__ . '/telemetry_store.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

function telemetry_respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    telemetry_respond(405, array('error' => 'POST requests only'));
}

$contentLength = isset($_SERVER['CONTENT_LENGTH']) ? (int) $_SERVER['CONTENT_LENGTH'] : 0;
if ($contentLength < 0 || $contentLength > 256) {
    telemetry_respond(413, array('error' => 'Request too large'));
}

$input = fopen('php://input', 'rb');
$raw = $input === false ? false : fread($input, 257);
if (is_resource($input)) {
    fclose($input);
}
if (!is_string($raw) || strlen($raw) > 256) {
    telemetry_respond(413, array('error' => 'Request too large'));
}
$payload = is_string($raw) ? json_decode($raw, true) : null;
if (!is_array($payload) || count($payload) !== 1 || !dost_telemetry_is_allowed_event($payload['event'] ?? null)) {
    telemetry_respond(400, array('error' => 'Invalid event'));
}

$archiveDirectory = conversation_storage_base_directory();
if ($archiveDirectory === false) {
    telemetry_respond(503, array('error' => 'Telemetry storage is unavailable'));
}
$telemetryDirectory = dost_prepare_storage_directory(dirname($archiveDirectory) . DIRECTORY_SEPARATOR . 'anonymous-telemetry');
if ($telemetryDirectory === false || !dost_telemetry_record($telemetryDirectory, $payload['event'], gmdate('Y-m-d'))) {
    telemetry_respond(503, array('error' => 'Telemetry storage is unavailable'));
}

dost_telemetry_prune($telemetryDirectory, dost_telemetry_retention_cutoff());
http_response_code(204);
