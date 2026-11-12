<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/romance_safety.php';

function dost_safety_test_fail($message)
{
    fwrite(STDERR, "FAIL: {$message}\n");
    exit(1);
}

if (!dost_romance_allowed_by_age('18', '18')
    || !dost_romance_allowed_by_age('120', '25')
    || dost_romance_allowed_by_age('17', '25')
    || dost_romance_allowed_by_age('25', '17')
    || dost_romance_allowed_by_age('', '25')
    || dost_romance_allowed_by_age('18 years', '25')
    || dost_romance_allowed_by_age(18, '25')
) {
    dost_safety_test_fail('Romance age gating did not fail closed for underage or unusable ages.');
}

if (!dost_content_requires_safety_block('I love you and want to kiss you.', false)
    || dost_content_requires_safety_block('We had a friendly conversation about school.', false)
    || dost_content_requires_safety_block('I love you.', true)
    || !dost_content_requires_safety_block('This is sexual content.', true)
    || !dost_content_requires_safety_block('পাশে যৌন বিষয় আছে', false)
    || !dost_content_requires_safety_block('{"reply":"Glad to chat","memories":["I love you"]}', false)
    || !dost_content_requires_safety_block(null, true)
    || dost_content_requires_safety_block(dost_safety_fallback(false), false)
) {
    dost_safety_test_fail('Romantic or sexual text was not classified conservatively.');
}

if (!dost_conversation_requires_safety_block(array(
    array('role' => 'user', 'content' => 'Can we date?')
), false)
    || !dost_conversation_requires_safety_block(array(
        array('role' => 'user', 'content' => 'Look at this picture.', 'image_data' => 'encoded-image')
    ), false)
    || dost_conversation_requires_safety_block(array(
        array('role' => 'user', 'content' => 'How was school today?')
    ), false)
) {
    dost_safety_test_fail('Conversation input checks did not block unsafe text or unclassified minor images.');
}

$clientSource = file_get_contents(dirname(__DIR__) . '/src/main/java/com/snigtus/bondhu/OpenRouterClient.kt');
if (!is_string($clientSource)
    || strpos($clientSource, 'put("user_age", userAge)') === false
    || strpos($clientSource, 'put("friend_age", friend.age)') === false
) {
    dost_safety_test_fail('The chat request does not send both ages to the server safety layer.');
}

fwrite(STDOUT, "PASS: server-side romance and sexual safety checks fail closed for minors and invalid input.\n");
