<?php

require_once __DIR__ . '/people_call_support.php';

header('Content-Type: application/json; charset=utf-8');

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_call_respond(404, array('error' => 'Not found'));
}
$request = dost_call_read_request();
if ($request === false) {
    dost_call_respond(400, array('error' => 'Invalid request'));
}
$idToken = dost_call_bearer_token();
if ($idToken === '') {
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
$settings = dost_call_config();
if ($settings === false) {
    error_log('Dost People call push is not configured.');
    dost_call_respond(503, array('error' => 'People calling is not configured'));
}
$authorization = dost_call_authorize(
    $settings,
    $idToken,
    $request['chat_id'],
    $request['call_id'],
    true,
    'push',
    10
);
if ($authorization['status'] !== 200) {
    dost_call_respond($authorization['status'], array('error' => $authorization['message']));
}
$peerUid = $authorization['peer_uid'];
$devices = dost_call_http_json(
    'GET',
    dost_call_firestore_url(
        $settings['project_id'],
        'peopleCallTokens/' . $peerUid . '/devices',
        'pageSize=100'
    ),
    array('Authorization: Bearer ' . $authorization['service_token'])
);
if ($devices === false || !in_array($devices['status'], array(200, 404), true)) {
    error_log('Dost People call push could not read registered devices.');
    dost_call_respond(503, array('error' => 'People calling is unavailable'));
}
$documents = isset($devices['body']['documents']) && is_array($devices['body']['documents'])
    ? array_slice($devices['body']['documents'], 0, 100)
    : array();
if (count($documents) === 0) {
    dost_call_respond(503, array('error' => 'The other person has no registered call device'));
}
$profileSnapshots = isset($authorization['chat']['memberProfiles'])
    && is_array($authorization['chat']['memberProfiles'])
    ? $authorization['chat']['memberProfiles']
    : array();
$callerSnapshot = isset($profileSnapshots[$authorization['uid']]) && is_array($profileSnapshots[$authorization['uid']])
    ? $profileSnapshots[$authorization['uid']]
    : array();
$callerName = isset($callerSnapshot['displayName']) && is_string($callerSnapshot['displayName'])
    ? trim($callerSnapshot['displayName'])
    : 'Dost user';
if ($callerName === '' || strlen($callerName) > 180) {
    $callerName = 'Dost user';
}
$callType = $authorization['call']['mediaType'] ?? 'audio';
$serviceToken = dost_call_service_account_token($settings['credentials']);
if (!is_string($serviceToken)) {
    error_log('Dost People call push FCM token exchange failed.');
    dost_call_respond(503, array('error' => 'People calling is unavailable'));
}
$sent = 0;
foreach ($documents as $document) {
    if (!is_array($document) || !isset($document['name'], $document['fields'])
        || !is_string($document['name']) || !is_array($document['fields'])
    ) {
        continue;
    }
    $fields = dost_call_decode_firestore_fields($document['fields']);
    $token = isset($fields['token']) && is_string($fields['token']) ? $fields['token'] : '';
    if ($token === '' || strlen($token) > 4096) {
        continue;
    }
    $message = array(
        'message' => array(
            'token' => $token,
            'data' => array(
                'type' => 'people_call',
                'chat_id' => $request['chat_id'],
                'call_id' => $request['call_id'],
                'caller_uid' => $authorization['uid'],
                'callee_uid' => $peerUid,
                'caller_name' => $callerName,
                'call_type' => $callType
            ),
            'android' => array(
                'priority' => 'high',
                'ttl' => '60s'
            )
        )
    );
    $result = dost_call_http_json(
        'POST',
        'https://fcm.googleapis.com/v1/projects/' . rawurlencode($settings['project_id']) . '/messages:send',
        array('Authorization: Bearer ' . $serviceToken),
        $message
    );
    if ($result !== false && $result['status'] === 200) {
        $sent++;
        continue;
    }
    $errorStatus = isset($result['body']['error']['status']) && is_string($result['body']['error']['status'])
        ? $result['body']['error']['status']
        : '';
    if ($errorStatus === 'UNREGISTERED') {
        $nameParts = explode('/documents/', $document['name'], 2);
        if (count($nameParts) === 2) {
            dost_call_firestore_delete($settings['project_id'], $serviceToken, $nameParts[1]);
        }
        continue;
    }
    error_log('Dost People call push FCM delivery failed.');
}
if ($sent === 0) {
    dost_call_respond(503, array('error' => 'Could not deliver the incoming-call alert'));
}
dost_call_respond(200, array('sent' => $sent));
