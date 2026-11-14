<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/romance_safety.php';

function dost_assert(bool $condition, string $message): void
{
    if (!$condition) {
        $backtrace = debug_backtrace(DEBUG_BACKTRACE_IGNORE_ARGS, 1);
        $line = $backtrace[0]['line'] ?? 'unknown';
        fwrite(STDERR, "FAIL [Line {$line}]: {$message}\n");
        exit(1);
    }
}

// Age gating: valid adult boundaries pass; malformed and underage values fail closed.
$validAgePairs = array(
    array('18', '18', 'minimum adult ages'),
    array('120', '25', 'maximum user age'),
    array('25', '120', 'maximum friend age'),
    array(' 18 ', '25', 'surrounding whitespace is trimmed')
);
foreach ($validAgePairs as $case) {
    list($userAge, $friendAge, $label) = $case;
    dost_assert(
        dost_romance_allowed_by_age($userAge, $friendAge) === true,
        "Valid age pair ({$userAge}, {$friendAge}) was rejected: {$label}"
    );
}

$invalidAgePairs = array(
    array('17', '25', 'minor user'),
    array('25', '17', 'minor friend'),
    array('0', '25', 'zero age'),
    array('-18', '25', 'negative age'),
    array('121', '25', 'age above the maximum'),
    array('', '25', 'empty age'),
    array('   ', '25', 'whitespace-only age'),
    array('18 years', '25', 'trailing letters'),
    array('18.5', '25', 'decimal age'),
    array('18e1', '25', 'scientific notation'),
    array("18\n", '25', 'newline in age'),
    array(18, '25', 'integer instead of string'),
    array(null, '25', 'null age'),
    array(true, '25', 'boolean age'),
    array(array('18'), '25', 'array age')
);
foreach ($invalidAgePairs as $case) {
    list($userAge, $friendAge, $label) = $case;
    dost_assert(
        dost_romance_allowed_by_age($userAge, $friendAge) === false,
        "Invalid age pair unexpectedly passed: {$label}"
    );
}

// Content classification: romance depends on age; sexual content is blocked for everyone.
$contentCases = array(
    array('I love you and want to kiss you.', false, true, 'romance when disabled'),
    array('I want to make out with you.', false, true, 'make-out phrase when disabled'),
    array('{"reply":"Let us hook up tonight."}', false, true, 'phrase inside JSON'),
    array('{"reply":"Glad to chat","memories":["I love you"]}', false, true, 'romance inside JSON memory'),
    array('We had a friendly conversation about school.', false, false, 'safe school conversation'),
    array('I love you.', true, false, 'romance when allowed'),
    array('I want to make out with you.', true, false, 'make-out phrase when allowed'),
    array('This is sexual content.', true, true, 'sexual content for adults'),
    array('THIS IS SEXUAL CONTENT.', true, true, 'case-insensitive sexual match'),
    array('পাশে যৌন বিষয় আছে', false, true, 'Bangla sexual content'),
    array(null, true, true, 'invalid content fails closed'),
    array('', false, false, 'safe fallback does not self-trigger')
);
foreach ($contentCases as $case) {
    list($text, $romanceAllowed, $expectedBlock, $label) = $case;
    if ($text === '') {
        $text = dost_safety_fallback(false);
    }
    dost_assert(
        dost_content_requires_safety_block($text, $romanceAllowed) === $expectedBlock,
        "Content classification failed: {$label}"
    );
}

dost_assert(
    dost_content_requires_safety_block(dost_safety_fallback(true), true) === false,
    'Adult safety fallback should not trigger a safety block.'
);

// Only the latest user turn controls the current input block.
$conversationCases = array(
    array(array(array('role' => 'user', 'content' => 'Can we date?')), false, true, 'romance when disabled'),
    array(array(array('role' => 'user', 'content' => 'This is sexual content.')), true, true, 'sexual text for adults'),
    array(array(array('role' => 'user', 'content' => 'Look at this picture.', 'image_data' => 'encoded-image')), false, true, 'minor image attachment'),
    array(array(array('role' => 'user', 'content' => 'How was school today?')), false, false, 'safe current turn'),
    array(array(array('role' => 'system', 'content' => 'Reply naturally.')), false, false, 'no user turn'),
    array('invalid messages', false, true, 'non-array messages'),
    array(array(array('role' => 'tool', 'content' => 'unknown role')), false, true, 'unknown role'),
    array(array(array('role' => 'user')), false, true, 'missing content')
);
foreach ($conversationCases as $case) {
    list($messages, $romanceAllowed, $expectedBlock, $label) = $case;
    dost_assert(
        dost_conversation_requires_safety_block($messages, $romanceAllowed) === $expectedBlock,
        "Conversation safety check failed: {$label}"
    );
}

// Earlier unsafe turns and their refusals must not block or contaminate a later safe turn.
$historyWithBlockedTurn = array(
    array('role' => 'system', 'content' => 'Reply naturally.'),
    array('role' => 'user', 'content' => 'Can you help with my homework?'),
    array('role' => 'assistant', 'content' => 'Sure, what subject?'),
    array('role' => 'user', 'content' => 'This is sexual content.'),
    array('role' => 'assistant', 'content' => dost_safety_fallback(true)),
    array('role' => 'user', 'content' => 'What is the capital of France?')
);
$filteredHistory = dost_filter_unsafe_conversation_history($historyWithBlockedTurn, true);
dost_assert(
    dost_conversation_requires_safety_block($historyWithBlockedTurn, true) === false,
    'An earlier blocked turn should not block a later safe turn.'
);
dost_assert(
    count($filteredHistory) === 4,
    'Expected four safe messages after removing the blocked turn and its refusal.'
);
dost_assert(
    $filteredHistory[0]['role'] === 'system'
    && $filteredHistory[1]['content'] === 'Can you help with my homework?'
    && $filteredHistory[2]['content'] === 'Sure, what subject?'
    && $filteredHistory[3]['content'] === 'What is the capital of France?',
    'History filtering did not preserve the safe message sequence.'
);

// A blocked assistant response also removes the user prompt that elicited it.
$historyWithBlockedOutput = array(
    array('role' => 'user', 'content' => 'Tell me about biology.'),
    array('role' => 'assistant', 'content' => 'This is sexual content.'),
    array('role' => 'user', 'content' => 'Now explain cells.')
);
$filteredBlockedOutput = dost_filter_unsafe_conversation_history($historyWithBlockedOutput, true);
dost_assert(
    count($filteredBlockedOutput) === 1
    && $filteredBlockedOutput[0]['content'] === 'Now explain cells.',
    'Unsafe assistant output left its prompt in provider history.'
);

// Underage histories must not forward old images, regardless of the message role.
$historyWithOldImages = array(
    array('role' => 'system', 'content' => 'Reply naturally.', 'image_data' => 'system-image'),
    array('role' => 'user', 'content' => 'Look at this.', 'image_data' => 'user-image'),
    array('role' => 'assistant', 'content' => 'I see it.', 'image_data' => 'assistant-image'),
    array('role' => 'user', 'content' => 'What is the weather like?')
);
$filteredMinorHistory = dost_filter_unsafe_conversation_history($historyWithOldImages, false);
dost_assert(
    count($filteredMinorHistory) === 1
    && $filteredMinorHistory[0]['content'] === 'What is the weather like?',
    'Historical image data was not removed for an age-ineligible conversation.'
);

// Fallbacks should match the requested chat language and safely default for unknown values.
$fallbackCases = array(
    array(true, 'en', 'I can keep our conversation respectful and non-explicit.'),
    array(false, 'bn', 'চলুন, আমাদের কথোপকথন বন্ধুত্বপূর্ণ ও বয়স-উপযোগী রাখি।'),
    array(true, 'bn', 'আমি আমাদের কথোপকথনকে সম্মানজনক ও অশ্লীলতামুক্ত রাখতে পারি।'),
    array(false, 'hi', 'आइए, बातचीत को दोस्ताना और उम्र के हिसाब से उचित रखें।'),
    array(true, 'unsupported_lang', 'I can keep our conversation respectful and non-explicit.')
);
foreach ($fallbackCases as $case) {
    list($romanceAllowed, $chatLanguage, $expectedFallback) = $case;
    dost_assert(
        dost_safety_fallback($romanceAllowed, $chatLanguage) === $expectedFallback,
        "Safety fallback did not match chat language: {$chatLanguage}"
    );
}

dost_assert(
    dost_safety_fallback(false, array('hi')) === "Let's keep our conversation friendly and age-appropriate.",
    'Non-string chat language did not fall back to English.'
);

// Client/server payload contract: age and chat language must reach the safety layer.
$clientPath = dirname(__DIR__) . '/src/main/java/com/snigtus/bondhu/OpenRouterClient.kt';
dost_assert(
    file_exists($clientPath) && is_readable($clientPath),
    "Kotlin client source is missing or unreadable: {$clientPath}"
);
$clientSource = file_get_contents($clientPath);
$clientPatterns = array(
    'user_age' => '/put\s*\(\s*"user_age"\s*,\s*userAge\s*\)/',
    'friend_age' => '/put\s*\(\s*"friend_age"\s*,\s*friend\.age\s*\)/',
    'chat_language' => '/put\s*\(\s*"chat_language"/'
);
foreach ($clientPatterns as $field => $pattern) {
    dost_assert(
        is_string($clientSource) && preg_match($pattern, $clientSource) === 1,
        "Client payload is missing the {$field} field."
    );
}

$apiPath = __DIR__ . '/ai.php';
dost_assert(
    file_exists($apiPath) && is_readable($apiPath),
    "AI API source is missing or unreadable: {$apiPath}"
);
$apiSource = file_get_contents($apiPath);
$apiPatterns = array(
    'latest-turn check' => '/dost_conversation_requires_safety_block\s*\(\s*\$messages\s*,\s*\$romanceAllowed\s*\)/',
    'history filtering' => '/dost_filter_unsafe_conversation_history\s*\(\s*\$messages\s*,\s*\$romanceAllowed\s*\)/',
    'localized fallback' => '/dost_safety_fallback\s*\(\s*\$romanceAllowed\s*,\s*\$chatLanguage\s*\)/'
);
foreach ($apiPatterns as $behavior => $pattern) {
    dost_assert(
        is_string($apiSource) && preg_match($pattern, $apiSource) === 1,
        "AI API is missing the {$behavior} integration."
    );
}

fwrite(STDOUT, "PASS: All romance and sexual safety tests passed.\n");
