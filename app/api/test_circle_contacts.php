<?php

require_once __DIR__ . '/circle_support.php';

function dost_circle_assert($condition, $message)
{
    if (!$condition) {
        fwrite(STDERR, $message . PHP_EOL);
        exit(1);
    }
}

dost_circle_assert(dost_circle_handle_is_valid('alice'), 'short valid handle');
dost_circle_assert(dost_circle_handle_is_valid('alice_one'), 'underscore handle');
dost_circle_assert(dost_circle_handle_is_valid('a' . str_repeat('b', 19)), '20-character handle');
dost_circle_assert(!dost_circle_handle_is_valid('ab'), 'too short');
dost_circle_assert(!dost_circle_handle_is_valid('Alice'), 'uppercase rejected');
dost_circle_assert(!dost_circle_handle_is_valid('1alice'), 'must start with a letter');
dost_circle_assert(!dost_circle_handle_is_valid('alice-one'), 'hyphen rejected');
dost_circle_assert(dost_circle_normalize_handle(' @Alice_One ') === 'alice_one', 'normalize handle');
dost_circle_assert(dost_circle_invite_token_is_valid(str_repeat('ab', 32)), '64 hex token');
dost_circle_assert(!dost_circle_invite_token_is_valid(str_repeat('z', 64)), 'non-hex token');
dost_circle_assert(!dost_circle_invite_token_is_valid(str_repeat('a', 63)), 'short token');
dost_circle_assert(
    dost_circle_invite_deep_link(str_repeat('a', 64)) === 'dost://circle/invite/' . str_repeat('a', 64),
    'deep link'
);
dost_circle_assert(
    strpos(dost_circle_invite_url(str_repeat('a', 64)), 'circle_invite.php?t=') !== false,
    'web invite url'
);

dost_circle_assert(
    dost_circle_profile_allows_lookup(array(
        'uid' => 'alice',
        'ageCohort' => 'adult',
        'handleLookupEnabled' => true,
    )),
    'adult lookup on'
);
dost_circle_assert(
    dost_circle_profile_allows_lookup(array(
        'uid' => 'teen',
        'ageCohort' => 'teen',
        'handleLookupEnabled' => true,
    )),
    'teen can opt in'
);
dost_circle_assert(
    !dost_circle_profile_allows_lookup(array(
        'uid' => 'teen',
        'ageCohort' => 'teen',
        'handleLookupEnabled' => false,
    )),
    'teen lookup off'
);
dost_circle_assert(
    !dost_circle_profile_allows_lookup(array(
        'uid' => 'alice',
        'ageCohort' => 'adult',
        'handleLookupEnabled' => false,
    )),
    'adult lookup off'
);

$now = 1_700_000_000;
$key = 'uid:test-circle-lookup:' . bin2hex(random_bytes(8));
for ($i = 0; $i < 8; $i++) {
    dost_circle_assert(dost_call_rate_limit_allows($key, 8, 600, $now + $i), 'lookup ' . ($i + 1) . ' allowed');
}
dost_circle_assert(!dost_call_rate_limit_allows($key, 8, 600, $now + 8), 'ninth lookup blocked');
dost_circle_assert(dost_call_rate_limit_allows($key, 8, 600, $now + 600), 'window reset');

echo "circle contact tests passed\n";
