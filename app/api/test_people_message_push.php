<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/people_call_support.php';

function dost_message_push_test_assert(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$request = array('chat_id' => 'chat_123', 'message_id' => 'message-123');
dost_message_push_test_assert(
    dost_message_push_request_is_valid($request),
    'A bounded chat and message ID should be accepted.'
);
dost_message_push_test_assert(
    !dost_message_push_request_is_valid(array('chat_id' => 'chat/123', 'message_id' => 'message-123')),
    'Path separators must not be accepted in IDs.'
);
dost_message_push_test_assert(
    !dost_message_push_request_is_valid($request + array('text' => 'message body')),
    'The notification request must not accept message content or extra fields.'
);
dost_message_push_test_assert(
    dost_message_push_request_is_valid($request + array('cipher' => true)),
    'Cipher notification requests should accept only the explicit metadata-only mode flag.'
);
dost_message_push_test_assert(
    !dost_message_push_request_is_valid($request + array('cipher' => false)),
    'Cipher notification mode must not be selectable with a false or ambiguous flag.'
);

$chat = array('members' => array('alice', 'bob'), 'ageCohort' => 'adult');
$message = array(
    'senderUid' => 'alice',
    'text' => 'A valid message',
    'createdAt' => 1770000000
);
dost_message_push_test_assert(
    dost_message_push_context_is_valid($chat, $message, 'alice'),
    'Only the authenticated sender of a message in an adult two-person chat should be accepted.'
);
dost_message_push_test_assert(
    !dost_message_push_context_is_valid($chat, $message, 'mallory'),
    'A nonparticipant must not send a notification for the message.'
);
$wrongSender = $message;
$wrongSender['senderUid'] = 'bob';
dost_message_push_test_assert(
    !dost_message_push_context_is_valid($chat, $wrongSender, 'alice'),
    'The persisted message sender must match the authenticated caller.'
);
$minorChat = $chat;
$minorChat['ageCohort'] = 'minor';
dost_message_push_test_assert(
    !dost_message_push_context_is_valid($minorChat, $message, 'alice'),
    'Message notifications must not be authorized for a non-adult chat.'
);

$cipherChat = array(
    'members' => array('alice', 'bob'),
    'sourceCircleChatId' => 'chat_123',
    'salt' => base64_encode(random_bytes(16)),
    'keyCheck' => 'cipher-key-check'
);
$cipherMessage = array(
    'ct' => base64_encode(random_bytes(32)),
    'nonce' => base64_encode(random_bytes(12)),
    'v' => 1,
    'senderUid' => 'alice',
    'createdAt' => 1770000000
);
dost_message_push_test_assert(
    dost_cipher_message_push_context_is_valid($cipherChat, $cipherMessage, 'alice', 'chat_123'),
    'A ciphertext-only message from a Cipher chat member should be accepted.'
);
dost_message_push_test_assert(
    !dost_cipher_message_push_context_is_valid(
        $cipherChat + array('ageCohort' => 'adult'),
        $cipherMessage + array('text' => 'plaintext'),
        'alice',
        'chat_123'
    ),
    'Cipher notifications must reject any plaintext message field.'
);
dost_message_push_test_assert(
    !dost_cipher_message_push_context_is_valid($cipherChat, $cipherMessage, 'mallory', 'chat_123'),
    'Cipher notifications must reject nonmembers.'
);

fwrite(STDOUT, "PASS: People message push authorization checks passed.\n");
