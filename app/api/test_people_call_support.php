<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/people_call_support.php';

function dost_call_test_assert(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$now = 1770000000;
$secret = str_repeat('turn-secret-', 4);
$credentials = dost_call_turn_credentials(
    array('turn:turn.example.net:3478?transport=udp', 'turns:turn.example.net:5349'),
    $secret,
    'firebase-user-123',
    $now,
    3600
);
dost_call_test_assert(is_array($credentials) && count($credentials) === 1, 'TURN credentials should be produced for valid settings.');
dost_call_test_assert(
    $credentials[0]['username'] === ($now + 3600) . ':firebase-user-123',
    'TURN username must encode the bounded expiration and authenticated Firebase UID.'
);
dost_call_test_assert(
    hash_equals(
        base64_encode(hash_hmac('sha1', $credentials[0]['username'], $secret, true)),
        $credentials[0]['credential']
    ),
    'TURN credential must use the coturn REST HMAC-SHA1 format.'
);
dost_call_test_assert(
    dost_call_turn_credentials(array('https://attacker.example/'), $secret, 'firebase-user-123', $now, 3600) === false,
    'TURN URLs must not permit non-TURN schemes.'
);
dost_call_test_assert(
    dost_call_turn_credentials(array('turn:turn.example.net:3478'), 'short', 'firebase-user-123', $now, 3600) === false,
    'The shared TURN secret must not be accepted when too short.'
);

$call = array(
    'callerUid' => 'alice',
    'calleeUid' => 'bob',
    'mediaType' => 'audio',
    'status' => 'ringing',
    'expiresAt' => $now + 3600,
    'ringExpiresAt' => $now + 60
);
$chat = array('members' => array('alice', 'bob'), 'ageCohort' => 'adult');
dost_call_test_assert(dost_call_valid_context($call, $chat, 'alice', $now, true), 'An unexpired call in an adult two-person chat should authorize its participant.');
$videoCall = $call;
$videoCall['mediaType'] = 'video';
dost_call_test_assert(
    dost_call_valid_context($videoCall, $chat, 'alice', $now, true),
    'A video call should authorize its participants.'
);
$invalidMediaCall = $call;
$invalidMediaCall['mediaType'] = 'screen';
dost_call_test_assert(
    !dost_call_valid_context($invalidMediaCall, $chat, 'alice', $now, true),
    'Unsupported call media types must not be authorized.'
);
dost_call_test_assert(
    !dost_call_valid_context($call, $chat, 'mallory', $now, false),
    'A nonparticipant must not access call credentials or send a call notification.'
);
$minorChat = $chat;
$minorChat['ageCohort'] = 'minor';
dost_call_test_assert(
    !dost_call_valid_context($call, $minorChat, 'alice', $now, false),
    'Calls must not be authorized for a non-adult chat.'
);
$expiredCall = $call;
$expiredCall['expiresAt'] = $now;
dost_call_test_assert(
    !dost_call_valid_context($expiredCall, $chat, 'alice', $now, false),
    'Expired calls must not obtain TURN credentials.'
);
$answeredCall = $call;
$answeredCall['status'] = 'accepted';
dost_call_test_assert(
    !dost_call_valid_context($answeredCall, $chat, 'alice', $now, true),
    'An answered call must not trigger another incoming-call push.'
);
dost_call_test_assert(
    dost_call_valid_context($answeredCall, $chat, 'alice', $now + 120, false),
    'An accepted call may obtain TURN credentials after its ringing window expires.'
);
$endedCall = $call;
$endedCall['status'] = 'ended';
dost_call_test_assert(
    !dost_call_valid_context($endedCall, $chat, 'alice', $now, false),
    'A call that has ended must not obtain TURN credentials.'
);
$expiredRingingCall = $call;
$expiredRingingCall['ringExpiresAt'] = $now;
dost_call_test_assert(
    !dost_call_valid_context($expiredRingingCall, $chat, 'alice', $now, false),
    'An unanswered call must not obtain TURN credentials after its ringing window.'
);

$rateLimitKey = 'support-test-' . bin2hex(random_bytes(12));
$rateLimitPath = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR)
    . DIRECTORY_SEPARATOR . 'dost-people-call-rate-limit'
    . DIRECTORY_SEPARATOR . hash('sha256', $rateLimitKey) . '.json';
$firstRequestAllowed = dost_call_rate_limit_allows($rateLimitKey, 1, 60, $now);
$secondRequestAllowed = dost_call_rate_limit_allows($rateLimitKey, 1, 60, $now + 1);
if (is_file($rateLimitPath)) {
    unlink($rateLimitPath);
}
dost_call_test_assert($firstRequestAllowed, 'The first request within a UID rate-limit window should pass.');
dost_call_test_assert(!$secondRequestAllowed, 'Repeated requests within a UID rate-limit window should be denied.');

$fields = dost_call_decode_firestore_fields(array(
    'uid' => array('stringValue' => 'alice'),
    'members' => array('arrayValue' => array('values' => array(
        array('stringValue' => 'alice'),
        array('stringValue' => 'bob')
    ))),
    'profile' => array('mapValue' => array('fields' => array(
        'displayName' => array('stringValue' => 'Alice')
    )))
));
dost_call_test_assert(
    $fields['uid'] === 'alice'
    && $fields['members'] === array('alice', 'bob')
    && $fields['profile']['displayName'] === 'Alice',
    'Firestore REST fields must decode nested maps and arrays.'
);

$coturnSettings = array(
    'turn_urls' => array('turn:turn.example.net:3478?transport=udp'),
    'turn_secret' => $secret,
    'cloudflare_turn_key_id' => '',
    'cloudflare_turn_api_token' => ''
);
dost_call_test_assert(
    dost_call_cloudflare_turn_settings_state($coturnSettings) === 'absent',
    'Empty Cloudflare TURN settings must leave the coturn path available.'
);
$coturnIssued = dost_call_issue_ice_servers($coturnSettings, 'firebase-user-123', $now);
dost_call_test_assert(
    is_array($coturnIssued)
    && $coturnIssued['expires_in'] === 3600
    && $coturnIssued['ice_servers'][0]['username'] === ($now + 3600) . ':firebase-user-123',
    'Coturn credentials must still be issued when Cloudflare settings are absent.'
);
$incompleteSettings = $coturnSettings;
$incompleteSettings['cloudflare_turn_key_id'] = '0123456789abcdef';
dost_call_test_assert(
    dost_call_cloudflare_turn_settings_state($incompleteSettings) === 'incomplete'
    && dost_call_issue_ice_servers($incompleteSettings, 'firebase-user-123', $now) === false,
    'A partial Cloudflare TURN configuration must not fall back to coturn.'
);
dost_call_test_assert(
    dost_call_cloudflare_turn_settings_state(array(
        'cloudflare_turn_key_id' => '../private',
        'cloudflare_turn_api_token' => 'cf-api-token-value-0123456789'
    )) === 'incomplete',
    'A Cloudflare TURN key id must not be able to change the request path.'
);

$cloudflareToken = 'cf-api-token-value-0123456789';
$cloudflareKeyId = '0123456789abcdef';
$capturedCloudflareRequest = null;
$cloudflareTransport = function ($method, $url, $headers, $payload, $timeout) use (&$capturedCloudflareRequest) {
    $capturedCloudflareRequest = array(
        'method' => $method,
        'url' => $url,
        'headers' => $headers,
        'payload' => $payload,
        'timeout' => $timeout
    );
    return array(
        'status' => 201,
        'body' => array(
            'iceServers' => array(
                array('urls' => array('stun:stun.cloudflare.com:3478')),
                array(
                    'urls' => array(
                        'turn:turn.cloudflare.com:3478?transport=udp',
                        'turns:turn.cloudflare.com:5349?transport=tcp'
                    ),
                    'username' => 'cloudflare-user',
                    'credential' => 'cloudflare-credential',
                    'apiToken' => 'cf-api-token-value-0123456789'
                )
            )
        )
    );
};
$cloudflareSettings = $coturnSettings;
$cloudflareSettings['cloudflare_turn_key_id'] = $cloudflareKeyId;
$cloudflareSettings['cloudflare_turn_api_token'] = $cloudflareToken;
$cloudflareIssued = dost_call_issue_ice_servers($cloudflareSettings, 'firebase-user-123', $now, $cloudflareTransport);
$encodedCloudflareIssued = json_encode($cloudflareIssued);
dost_call_test_assert(
    is_array($cloudflareIssued)
    && $cloudflareIssued['expires_in'] === 3600
    && $cloudflareIssued['ice_servers'][0] === array('urls' => array('stun:stun.cloudflare.com:3478'))
    && $cloudflareIssued['ice_servers'][1]['username'] === 'cloudflare-user'
    && $cloudflareIssued['ice_servers'][1]['credential'] === 'cloudflare-credential'
    && in_array('turns:turn.cloudflare.com:5349?transport=tcp', $cloudflareIssued['ice_servers'][1]['urls'], true)
    && is_array($capturedCloudflareRequest)
    && $capturedCloudflareRequest['method'] === 'POST'
    && $capturedCloudflareRequest['url'] === 'https://rtc.live.cloudflare.com/v1/turn/keys/' . $cloudflareKeyId . '/credentials/generate-ice-servers'
    && $capturedCloudflareRequest['headers'] === array('Authorization: Bearer ' . $cloudflareToken)
    && $capturedCloudflareRequest['payload'] === array('ttl' => 3600)
    && $capturedCloudflareRequest['timeout'] === 8
    && is_string($encodedCloudflareIssued)
    && strpos($encodedCloudflareIssued, $cloudflareToken) === false
    && strpos($encodedCloudflareIssued, 'apiToken') === false,
    'Cloudflare TURN settings must replace coturn and must not expose the API token.'
);
dost_call_test_assert(
    dost_call_cloudflare_ice_servers($cloudflareKeyId, $cloudflareToken, 3600, function () {
        return false;
    }) === false,
    'A Cloudflare TURN timeout must not return credentials.'
);
dost_call_test_assert(
    dost_call_cloudflare_ice_servers($cloudflareKeyId, $cloudflareToken, 3600, function () {
        return array('status' => 502, 'body' => array('error' => 'upstream'));
    }) === false,
    'A Cloudflare TURN error response must not return credentials.'
);
dost_call_test_assert(
    dost_call_normalize_cloudflare_ice_servers(array(
        'iceServers' => array(array('urls' => array('https://attacker.example/')))
    )) === false,
    'Cloudflare ICE URLs must stay on stun or turn schemes.'
);
dost_call_test_assert(
    dost_call_normalize_cloudflare_ice_servers(array(
        'iceServers' => array(array('urls' => array('stun:stun.cloudflare.com:3478')))
    )) === false,
    'Cloudflare ICE configuration must include at least one TURN URL.'
);

fwrite(STDOUT, "PASS: People call authorization and TURN credential checks passed.\n");
