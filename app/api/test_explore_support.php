<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/explore_support.php';

function dost_explore_test_assert(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$directory = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'dost-explore-test-' . bin2hex(random_bytes(8));
$uid = 'firebase-user-123';
$limits = array(
    array('custom', 3),
    array('catalog', 7)
);
foreach ($limits as $limit) {
    [$kind, $maximum] = $limit;
    for ($index = 0; $index < $maximum; $index++) {
        $result = dost_explore_registry_change($directory, $uid, $kind . '-' . $index, $kind, 'add', false);
        dost_explore_test_assert($result['status'] === 200, 'Free accounts can register the included friend limit.');
    }
    $blocked = dost_explore_registry_change($directory, $uid, $kind . '-over', $kind, 'add', false);
    dost_explore_test_assert($blocked['status'] === 403, 'Free accounts cannot exceed the server-side friend limit.');
    $plus = dost_explore_registry_change($directory, 'plus-' . $kind, $kind . '-extra', $kind, 'add', true);
    dost_explore_test_assert($plus['status'] === 200, 'Explore Plus accounts are not subject to the free friend limit.');
}
$removed = dost_explore_registry_change($directory, $uid, 'custom-0', 'custom', 'remove', false);
$afterRemoval = dost_explore_registry_change($directory, $uid, 'custom-new', 'custom', 'add', false);
dost_explore_test_assert(
    $removed['status'] === 200 && $afterRemoval['status'] === 200,
    'Removing a friend frees a server-tracked slot.'
);
dost_explore_test_assert(
    !dost_explore_enabled(array()) && dost_explore_enabled(array('explore_enabled' => 'true')),
    'Explore is disabled unless the server feature flag is explicitly enabled.'
);
dost_explore_test_assert(
    dost_explore_authorize('invalid token', array(), time())['status'] === 401,
    'Explore endpoints reject requests without a Firebase bearer token.'
);
dost_explore_test_assert(
    dost_explore_plus_active(array('dostExplorePlusUntil' => time() + 60), time())
        && !dost_explore_plus_active(array('dostExplorePlusUntil' => time()), time()),
    'Explore Plus access comes only from active server-side claims.'
);
dost_explore_test_assert(
    dost_explore_ai_friend_allowed(array(), '17', time())
        && !dost_explore_ai_friend_allowed(array(), '18', time())
        && dost_explore_ai_friend_allowed(
            array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => time() + 60),
            '18',
            time()
        ),
    'Only an active adult-eligibility claim unlocks adult AI characters.'
);
dost_explore_test_assert(
    dost_explore_catalog_profile_allowed(array(), array('age' => '17'), time())
        && !dost_explore_catalog_profile_allowed(array(), array('age' => '18'), time())
        && dost_explore_catalog_profile_allowed(
            array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => time() + 60),
            array('age' => '18'),
            time()
        ),
    'Teen accounts receive only age-appropriate catalog profiles.'
);

foreach (glob($directory . DIRECTORY_SEPARATOR . '*') ?: array() as $file) {
    unlink($file);
}
rmdir($directory);

echo "PASS: Explore server authorization helpers and friend limits passed.\n";
