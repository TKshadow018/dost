<?php

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

$deletedSessions = dost_delete_installation_archive($installationId);
if ($deletedSessions === false) {
    http_response_code(503);
    echo json_encode(array('error' => 'Could not delete the conversation archive'));
    exit;
}

echo json_encode(array('deleted_sessions' => $deletedSessions));
