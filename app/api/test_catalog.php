<?php

require_once __DIR__ . DIRECTORY_SEPARATOR . 'catalog_validation.php';

$report = validate_system_friend_catalog(__DIR__ . DIRECTORY_SEPARATOR . 'system_friends');
foreach ($report['warnings'] as $warning) {
    fwrite(STDOUT, 'WARN: ' . $warning . PHP_EOL);
}
if (count($report['errors']) > 0) {
    foreach ($report['errors'] as $error) {
        fwrite(STDERR, 'FAIL: ' . $error . PHP_EOL);
    }
    exit(1);
}

$validFixture = array(
    'schemaVersion' => 1,
    'name' => 'Catalog test',
    'age' => '24',
    'gender' => 'Unspecified',
    'category' => 'Test',
    'description' => 'A test profile.',
    'relationship' => 'You met as friends.',
    'greeting' => 'Hello.',
    'personality' => 'Curious and thoughtful.',
    'interests' => 'Books and music.',
    'conversationStyle' => 'Warm and concise.',
    'timeZoneId' => 'UTC',
    'busyStartHour' => 9,
    'busyDurationHours' => 8
);
if (count(validate_system_friend_profile($validFixture)) !== 0) {
    fwrite(STDERR, "FAIL: A valid catalog fixture was rejected.\n");
    exit(1);
}
$invalidFixture = $validFixture;
$invalidFixture['schemaVersion'] = 2;
$invalidFixture['busyStartHour'] = 24;
if (count(validate_system_friend_profile($invalidFixture)) < 2) {
    fwrite(STDERR, "FAIL: Unsupported schema and schedule values were accepted.\n");
    exit(1);
}

fwrite(STDOUT, 'PASS: ' . $report['count'] . " catalog profiles and validator fixtures passed.\n");
