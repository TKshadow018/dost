<?php

require_once __DIR__ . '/people_call_support.php';

header('Content-Type: application/json; charset=utf-8');

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_call_respond(404, array('error' => 'Not found'));
}
$rawBody = file_get_contents('php://input');
if (!is_string($rawBody) || strlen($rawBody) > 16384) {
    dost_call_respond(400, array('error' => 'Invalid request'));
}
$request = json_decode($rawBody, true);
if (!dost_message_push_request_is_valid($request)) {
    dost_call_respond(400, array('error' => 'Invalid request'));
}
$idToken = dost_call_bearer_token();
if ($idToken === '') {
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
$settings = dost_call_config();
if ($settings === false) {
    error_log('Dost People message push is not configured.');
    dost_call_respond(503, array('error' => 'People messaging is not configured'));
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
if (!dost_call_rate_limit_allows('uid:' . $uid . ':people-message-push', 120, 60, time())) {
    dost_call_respond(429, array('error' => 'Too many message notification requests'));
}
$serviceToken = dost_call_service_account_token($settings['credentials']);
if (!is_string($serviceToken)) {
    error_log('Dost People message push service account token exchange failed.');
    dost_call_respond(503, array('error' => 'People messaging is unavailable'));
}
$chatResponse = dost_call_firestore_get(
    $settings['project_id'],
    $serviceToken,
    'peopleChats/' . $request['chat_id']
);
$messageResponse = dost_call_firestore_get(
    $settings['project_id'],
    $serviceToken,
    'peopleChats/' . $request['chat_id'] . '/messages/' . $request['message_id']
);
if ($chatResponse === false || $messageResponse === false) {
    error_log('Dost People message push could not read the conversation or message.');
    dost_call_respond(503, array('error' => 'People messaging is unavailable'));
}
if ($chatResponse['status'] === 404 || $messageResponse['status'] === 404) {
    dost_call_respond(403, array('error' => 'This message is not available'));
}
if ($chatResponse['status'] !== 200 || $messageResponse['status'] !== 200) {
    error_log('Dost People message push received an unexpected Firestore response.');
    dost_call_respond(503, array('error' => 'People messaging is unavailable'));
}
$chat = dost_call_document_fields($chatResponse);
$messageFields = dost_call_document_fields($messageResponse);
if (!dost_message_push_context_is_valid($chat, $messageFields, $uid)) {
    dost_call_respond(403, array('error' => 'This message is not available'));
}
$members = $chat['members'];
$peerUid = $members[0] === $uid ? $members[1] : $members[0];
$now = time();
if (!dost_call_user_is_eligible($settings['project_id'], $serviceToken, $uid, $now)
    || !dost_call_user_is_eligible($settings['project_id'], $serviceToken, $peerUid, $now)
) {
    dost_call_respond(403, array('error' => 'This message is not available'));
}
foreach (array(
    'peopleBlocks/' . $uid . '/blockedUsers/' . $peerUid,
    'peopleBlocks/' . $peerUid . '/blockedUsers/' . $uid
) as $blockPath) {
    $blockResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, $blockPath);
    if ($blockResponse === false) {
        error_log('Dost People message push could not check block state.');
        dost_call_respond(503, array('error' => 'People messaging is unavailable'));
    }
    if ($blockResponse['status'] === 200) {
        dost_call_respond(403, array('error' => 'This message is not available'));
    }
    if ($blockResponse['status'] !== 404) {
        error_log('Dost People message push received an unexpected block lookup response.');
        dost_call_respond(503, array('error' => 'People messaging is unavailable'));
    }
}
$devices = dost_call_http_json(
    'GET',
    dost_call_firestore_url(
        $settings['project_id'],
        'peopleMessageTokens/' . $peerUid . '/devices',
        'pageSize=100'
    ),
    array('Authorization: Bearer ' . $serviceToken)
);
if ($devices === false || !in_array($devices['status'], array(200, 404), true)) {
    error_log('Dost People message push could not read registered devices.');
    dost_call_respond(503, array('error' => 'People messaging is unavailable'));
}
$documents = isset($devices['body']['documents']) && is_array($devices['body']['documents'])
    ? array_slice($devices['body']['documents'], 0, 100)
    : array();
$sent = 0;
$deliveryFailed = false;
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
    $result = dost_call_http_json(
        'POST',
        'https://fcm.googleapis.com/v1/projects/' . rawurlencode($settings['project_id']) . '/messages:send',
        array('Authorization: Bearer ' . $serviceToken),
        array(
            'message' => array(
                'token' => $token,
                'data' => array(
                    'type' => 'people_message',
                    'chat_id' => $request['chat_id'],
                    'message_id' => $request['message_id'],
                    'sender_uid' => $uid,
                    'recipient_uid' => $peerUid
                ),
                'android' => array(
                    'priority' => 'normal',
                    'ttl' => '86400s'
                )
            )
        )
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
    $deliveryFailed = true;
    error_log('Dost People message push FCM delivery failed.');
}
if ($sent === 0 && $deliveryFailed) {
    dost_call_respond(503, array('error' => 'Could not deliver the message notification'));
}
dost_call_respond(200, array('sent' => $sent));
