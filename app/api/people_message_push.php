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
$isCipher = isset($request['cipher']) && $request['cipher'] === true;
$isCircleGroup = isset($request['circle_group']) && $request['circle_group'] === true;
$isCircleDirect = isset($request['circle_direct']) && $request['circle_direct'] === true;
$isCircle = $isCircleGroup || $isCircleDirect;
$chatPath = $isCircleGroup
    ? 'circleGroups/' . $request['chat_id']
    : ($isCircleDirect
        ? 'circleDirectChats/' . $request['chat_id']
        : ($isCipher ? 'cipherChats/' . $request['chat_id'] : 'peopleChats/' . $request['chat_id']));
$chatResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, $chatPath);
$messageResponse = dost_call_firestore_get(
    $settings['project_id'],
    $serviceToken,
    $chatPath . '/messages/' . $request['message_id']
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
$validMessage = $isCircleGroup
    ? dost_circle_group_message_push_context_is_valid($chat, $messageFields, $uid)
    : ($isCircleDirect
        ? dost_circle_direct_message_push_context_is_valid($chat, $messageFields, $uid)
        : ($isCipher
            ? dost_cipher_message_push_context_is_valid($chat, $messageFields, $uid, $request['chat_id'])
            : dost_message_push_context_is_valid($chat, $messageFields, $uid)));
if (!$validMessage) {
    dost_call_respond(403, array('error' => 'This message is not available'));
}
$members = $chat['members'];
$recipientUids = $isCircleGroup
    ? array_values(array_filter($members, function ($memberUid) use ($uid) {
        return $memberUid !== $uid;
    }))
    : array($members[0] === $uid ? $members[1] : $members[0]);
$now = time();
$eligibleUser = $isCipher || $isCircle
    ? dost_call_user_is_cipher_eligible($settings['project_id'], $serviceToken, $uid)
    : dost_call_user_is_eligible($settings['project_id'], $serviceToken, $uid, $now);
if (!$eligibleUser) {
    dost_call_respond(403, array('error' => 'This message is not available'));
}
if (!$isCircleGroup) {
    $peerUid = $recipientUids[0];
    $eligiblePeer = ($isCipher || $isCircleDirect)
        ? dost_call_user_is_cipher_eligible($settings['project_id'], $serviceToken, $peerUid)
        : dost_call_user_is_eligible($settings['project_id'], $serviceToken, $peerUid, $now);
    if (!$eligiblePeer) {
        dost_call_respond(403, array('error' => 'This message is not available'));
    }
}
if ($isCipher) {
    if ($settings['cipher_enabled'] !== 'true') {
        dost_call_respond(403, array('error' => 'Cipher messaging is not enabled'));
    }
    $cipherConfigResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, 'cipherConfig/cipher');
    $sourceChatResponse = dost_call_firestore_get(
        $settings['project_id'],
        $serviceToken,
        'peopleChats/' . $request['chat_id']
    );
    if ($cipherConfigResponse === false || $sourceChatResponse === false) {
        error_log('Dost Cipher message push could not validate feature or source chat.');
        dost_call_respond(503, array('error' => 'Cipher messaging is unavailable'));
    }
    if ($cipherConfigResponse['status'] !== 200 || $sourceChatResponse['status'] !== 200) {
        dost_call_respond(403, array('error' => 'This message is not available'));
    }
    $cipherConfig = dost_call_document_fields($cipherConfigResponse);
    $sourceChat = dost_call_document_fields($sourceChatResponse);
    if (($cipherConfig['enabled'] ?? false) !== true
        || !isset($sourceChat['members'])
        || $sourceChat['members'] !== $members
    ) {
        dost_call_respond(403, array('error' => 'This message is not available'));
    }
}
$allowedRecipients = array();
foreach ($recipientUids as $recipientUid) {
    if ($isCircle
        && !dost_call_user_is_cipher_eligible($settings['project_id'], $serviceToken, $recipientUid)
    ) {
        continue;
    }
    $blocked = false;
    foreach (array(
        'peopleBlocks/' . $uid . '/blockedUsers/' . $recipientUid,
        'peopleBlocks/' . $recipientUid . '/blockedUsers/' . $uid
    ) as $blockPath) {
        $blockResponse = dost_call_firestore_get($settings['project_id'], $serviceToken, $blockPath);
        if ($blockResponse === false) {
            error_log('Dost People message push could not check block state.');
            dost_call_respond(503, array('error' => 'People messaging is unavailable'));
        }
        if ($blockResponse['status'] === 200) {
            $blocked = true;
            break;
        }
        if ($blockResponse['status'] !== 404) {
            error_log('Dost People message push received an unexpected block lookup response.');
            dost_call_respond(503, array('error' => 'People messaging is unavailable'));
        }
    }
    if ($blocked) {
        if (!$isCircleGroup) {
            dost_call_respond(403, array('error' => 'This message is not available'));
        }
        continue;
    }
    $allowedRecipients[] = $recipientUid;
}
$sent = 0;
$deliveryFailed = false;
foreach ($allowedRecipients as $recipientUid) {
    $devices = dost_call_http_json(
        'GET',
        dost_call_firestore_url(
            $settings['project_id'],
            'peopleMessageTokens/' . $recipientUid . '/devices',
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
                    'type' => $isCircleGroup
                        ? 'circle_group_message'
                        : ($isCircleDirect ? 'circle_direct_message' : ($isCipher ? 'cipher_message' : 'people_message')),
                    'chat_id' => $request['chat_id'],
                    'message_id' => $request['message_id'],
                    'sender_uid' => $uid,
                    'recipient_uid' => $recipientUid
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
}
if ($sent === 0 && $deliveryFailed) {
    dost_call_respond(503, array('error' => 'Could not deliver the message notification'));
}
dost_call_respond(200, array('sent' => $sent));
