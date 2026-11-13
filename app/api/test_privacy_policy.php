<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

/**
 * Checks privacy-policy.txt against the deletion, retention, and audience
 * facts this version of the app actually implements.
 */

$path = dirname(__FILE__) . DIRECTORY_SEPARATOR . 'privacy-policy.txt';
$text = file_get_contents($path);
if (!is_string($text) || trim($text) === '') {
    fwrite(STDERR, "FAIL: privacy-policy.txt is missing or empty.\n");
    exit(1);
}

$failures = array();

$forbidden = array(
    '/all ages/i' => 'claims the app is for all ages',
    '/including children/i' => 'claims the app is for children',
    '/intended for users of all ages/i' => 'states an all-ages audience',
    '/does not currently erase/i' => 'says friend removal keeps local history',
    '/removal does not erase/i' => 'says removal does not erase local history',
    '/does not currently erase that friend/i' => 'says friend removal keeps local chat',
    '/Google Play requires/i' => 'states a third-party store rule in the user-facing policy',
    '/not configured to exclude/i' => 'says chat photos are included in backup',
    '/does not check the value/i' => 'says registration does not validate age',
    '/meter is not turned off because of the age/i' => 'says romance is not age-gated'
);
foreach ($forbidden as $pattern => $reason) {
    if (preg_match($pattern, $text) === 1) {
        $failures[] = $reason;
    }
}

$required = array(
    '3,650' => 'states the 3,650-day server retention period',
    'Delete server archive' => 'describes the in-app server archive deletion control',
    'export or import local data' => 'describes local data export and import',
    'does not include your OpenRouter API key or installation id' => 'states sensitive data is excluded from backup',
    'hashed-installation deletion marker' => 'discloses metadata retained to suppress delayed uploads',
    'chat transcript file' => 'states that friend removal deletes the local transcript',
    'learned memories' => 'states that friend removal deletes local memories',
    'session summaries' => 'states that friend removal deletes local summaries',
    'whole-number age from 13 through 120' => 'states the registration age requirement',
    'user or friend is under 18' => 'states the romance age gate',
    'child-directed service' => 'states that Dost is not a child-directed service',
    'tushartk018@gmail.com' => 'keeps the archive-request contact address',
    'November 12, 2026' => 'uses the revised effective date',
    'API keys are not written into conversation logs' => 'still says API keys are not logged',
    'chat_images' => 'mentions chat image files',
    'system-friend-photos' => 'says system-friend photos are excluded from backup',
    'up to the 200 most recent saved facts' => 'names learned facts sent with each turn',
    'up to the seven most recent summaries' => 'names session summaries sent with each turn',
    'friendship score and love score' => 'names relationship scores sent with each turn',
    'does not display the installation id' => 'says the app does not display the installation id',
    'installation id' => 'describes install-id lookup for newer archives',
    'Share anonymous event counts' => 'discloses optional event-only analytics',
    'daily event totals for 90 days' => 'states analytics data retention',
    'does not receive message content, profile details, account data, or installation ids' => 'states analytics excludes conversation and identity data',
    'may see your IP address' => 'discloses IP visibility to the API host'
);
foreach ($required as $needle => $reason) {
    if (strpos($text, $needle) === false) {
        $failures[] = 'missing: ' . $reason;
    }
}

if (count($failures) > 0) {
    fwrite(STDERR, "FAIL: privacy policy does not match shipped behavior:\n");
    foreach ($failures as $failure) {
        fwrite(STDERR, " - {$failure}\n");
    }
    exit(1);
}

fwrite(STDOUT, "PASS: privacy policy matches local deletion, 3,650-day retention, and the age gate.\n");
