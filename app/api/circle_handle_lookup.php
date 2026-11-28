<?php

require_once __DIR__ . '/circle_support.php';

header('Content-Type: application/json; charset=utf-8');

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_call_respond(404, array('error' => 'Not found'));
}
$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 4096) {
    dost_call_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
$handle = dost_circle_normalize_handle(is_array($request) ? ($request['handle'] ?? '') : '');
if (!dost_circle_handle_is_valid($handle) || (is_array($request) && count($request) !== 1)) {
    dost_call_respond(400, array('error' => 'Enter an exact @handle'));
}
$idToken = dost_call_bearer_token();
if ($idToken === '') {
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
$settings = dost_call_config();
if ($settings === false) {
    error_log('Dost Circle handle lookup is not configured.');
    dost_call_respond(503, array('error' => 'Handle lookup is unavailable'));
}
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
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
$uid = $identity['body']['users'][0]['localId'];
if (preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $uid) !== 1) {
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
if (!dost_call_rate_limit_allows('uid:' . $uid . ':circle-handle-lookup', 8, 600, time())) {
    dost_call_respond(429, array('error' => 'Too many handle lookups. Try again later.'));
}
$serviceToken = dost_call_service_account_token($settings['credentials']);
if (!is_string($serviceToken)) {
    error_log('Dost Circle handle lookup service account token exchange failed.');
    dost_call_respond(503, array('error' => 'Handle lookup is unavailable'));
}
if (!dost_call_user_is_cipher_eligible($settings['project_id'], $serviceToken, $uid)) {
    dost_call_respond(403, array('error' => 'Handle lookup is not available for this account'));
}
$handleResponse = dost_call_firestore_get(
    $settings['project_id'],
    $serviceToken,
    'circleHandles/' . $handle
);
if ($handleResponse === false) {
    error_log('Dost Circle handle lookup could not read the handle document.');
    dost_call_respond(503, array('error' => 'Handle lookup is unavailable'));
}
if ($handleResponse['status'] === 404) {
    dost_call_respond(404, array('error' => 'No one is using that handle'));
}
if ($handleResponse['status'] !== 200) {
    error_log('Dost Circle handle lookup received an unexpected Firestore response.');
    dost_call_respond(503, array('error' => 'Handle lookup is unavailable'));
}
$handleFields = dost_call_document_fields($handleResponse);
$ownerUid = is_array($handleFields) ? ($handleFields['ownerUid'] ?? '') : '';
if (!is_string($ownerUid) || preg_match('/\A[A-Za-z0-9:_-]{1,128}\z/', $ownerUid) !== 1) {
    dost_call_respond(404, array('error' => 'No one is using that handle'));
}
$profileResponse = dost_call_firestore_get(
    $settings['project_id'],
    $serviceToken,
    'circleProfiles/' . $ownerUid
);
if ($profileResponse === false || $profileResponse['status'] !== 200) {
    dost_call_respond(404, array('error' => 'No one is using that handle'));
}
$profile = dost_call_document_fields($profileResponse);
if (!dost_circle_profile_allows_lookup($profile) || ($profile['uid'] ?? '') !== $ownerUid) {
    dost_call_respond(404, array('error' => 'No one is using that handle'));
}
if ($ownerUid === $uid) {
    dost_call_respond(200, array('owner_uid' => $ownerUid, 'handle' => $handle, 'self' => true));
}
dost_call_respond(200, array('owner_uid' => $ownerUid, 'handle' => $handle, 'self' => false));
