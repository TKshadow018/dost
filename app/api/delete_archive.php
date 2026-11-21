<?php

require_once __DIR__ . '/app_credential.php';
require_once __DIR__ . '/archive_store.php';

header('Content-Type: application/json; charset=utf-8');

if (!isset($_SERVER['REQUEST_METHOD']) || $_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    http_response_code(405);
    echo json_encode(array('error' => 'POST requests only'));
    exit;
}

$installationId = isset($_SERVER['HTTP_X_DOST_INSTALL_ID']) && is_string($_SERVER['HTTP_X_DOST_INSTALL_ID'])
    ? trim($_SERVER['HTTP_X_DOST_INSTALL_ID'])
    : '';
if (!valid_uuid($installationId)) {
    http_response_code(400);
    echo json_encode(array('error' => 'A valid installation id is required'));
    exit;
}

$config = array();
$configPath = __DIR__ . '/config.php';
if (is_file($configPath)) {
    $loadedConfig = require $configPath;
    if (is_array($loadedConfig)) {
        $config = $loadedConfig;
    }
}
$credentialPresented = isset($_SERVER['HTTP_X_DOST_APP_CREDENTIAL']) && is_string($_SERVER['HTTP_X_DOST_APP_CREDENTIAL'])
    ? trim($_SERVER['HTTP_X_DOST_APP_CREDENTIAL'])
    : '';
$credentialDecision = dost_required_app_credential_decision(
    $credentialPresented,
    $installationId,
    time(),
    $config
);
if ($credentialDecision !== null) {
    http_response_code($credentialDecision['status']);
    echo json_encode(array('error' => $credentialDecision['error']));
    exit;
}

$deletedSessions = dost_delete_installation_archive($installationId);
if ($deletedSessions === false) {
    http_response_code(503);
    echo json_encode(array('error' => 'Could not delete the conversation archive'));
    exit;
}

echo json_encode(array('deleted_sessions' => $deletedSessions));
