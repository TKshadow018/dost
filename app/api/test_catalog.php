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
    'gender' => 'Female',
    'category' => 'Test',
    'country' => 'Bangladesh',
    'description' => 'A test profile.',
    'relationship' => 'You met as friends.',
    'greeting' => array('en' => 'Hi!', 'bn' => 'হাই!', 'hi' => 'नमस्ते!'),
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
$invalidFixture['gender'] = 'Unspecified';
if (count(validate_system_friend_profile($invalidFixture)) < 3) {
    fwrite(STDERR, "FAIL: Unsupported schema and schedule values were accepted.\n");
    exit(1);
}

if (localized_system_friend_greeting($validFixture['greeting'], 'bn') !== 'হাই!'
    || localized_system_friend_greeting($validFixture['greeting'], 'hi') !== 'नमस्ते!'
    || localized_system_friend_greeting($validFixture['greeting'], 'unsupported') !== 'Hi!'
) {
    fwrite(STDERR, "FAIL: Localized profile greetings did not select the chat language or default to English.\n");
    exit(1);
}

$catalogDirectory = __DIR__ . DIRECTORY_SEPARATOR . 'system_friends';
$colocatedPhoto = system_friend_photo_location($catalogDirectory, 'aarav-mehta.webp');
$caseSensitivePhoto = system_friend_photo_location($catalogDirectory, 'tania-akter.jPG');
$legacyPhoto = system_friend_photo_location($catalogDirectory, 'photos/asha.jpg');
if (!is_array($colocatedPhoto)
    || $colocatedPhoto['path'] !== $catalogDirectory . DIRECTORY_SEPARATOR . 'aarav-mehta.webp'
    || !is_array($caseSensitivePhoto)
    || $caseSensitivePhoto['path'] !== $catalogDirectory . DIRECTORY_SEPARATOR . 'tania-akter.jPG'
    || !is_array($legacyPhoto)
    || $legacyPhoto['path'] !== $catalogDirectory . DIRECTORY_SEPARATOR . 'photos' . DIRECTORY_SEPARATOR . 'asha.jpg'
    || system_friend_photo_location($catalogDirectory, '../outside.jpg') !== null
    || system_friend_photo_location($catalogDirectory, 'photos/../outside.jpg') !== null
) {
    fwrite(STDERR, "FAIL: Profile photo paths are not correctly constrained to supported catalog locations.\n");
    exit(1);
}

$distribution = array(
    'total' => 0,
    'Male' => 0,
    'Female' => 0,
    'Bangladesh' => 0,
    'India' => 0,
    'Nepal' => 0,
    'Japan' => 0,
    'English-speaking' => 0
);
$englishSpeakingCountries = array('Australia', 'Canada', 'Ireland', 'New Zealand', 'United Kingdom', 'United States');
$profileFiles = glob(__DIR__ . DIRECTORY_SEPARATOR . 'system_friends' . DIRECTORY_SEPARATOR . '*.json');
$profilesById = array();
foreach ($profileFiles as $profileFile) {
    $profile = json_decode(file_get_contents($profileFile), true);
    $profilesById[basename($profileFile, '.json')] = $profile;
    $distribution['total']++;
    if (isset($distribution[$profile['gender']])) {
        $distribution[$profile['gender']]++;
    }
    if (isset($distribution[$profile['country']])) {
        $distribution[$profile['country']]++;
    } elseif (in_array($profile['country'], $englishSpeakingCountries, true)) {
        $distribution['English-speaking']++;
    }
}
if ($distribution['total'] !== 50
    || $distribution['Male'] !== 17
    || $distribution['Female'] !== 33
    || $distribution['Bangladesh'] !== 14
    || $distribution['India'] !== 14
    || $distribution['Nepal'] !== 3
    || $distribution['Japan'] !== 1
    || $distribution['English-speaking'] !== 18
) {
    fwrite(STDERR, 'FAIL: Catalog distribution mismatch: ' . json_encode($distribution) . PHP_EOL);
    exit(1);
}

$modelProfileRequirements = array(
    'avni-kapoor' => array('Female', 18, 20),
    'sienna-brooks' => array('Female', 18, 20),
    'avery-claire' => array('Female', 18, 20),
    'naira-chowdhury' => array('Female', 18, 20),
    'isla-bennett' => array('Female', 18, 20),
    'elliot-hayes' => array('Male', 20, 24),
    'reyansh-malhotra' => array('Male', 20, 24),
    'liam-parker' => array('Male', 20, 24)
);
foreach ($modelProfileRequirements as $profileId => $requirements) {
    if (!isset($profilesById[$profileId])) {
        fwrite(STDERR, 'FAIL: Missing requested model profile: ' . $profileId . PHP_EOL);
        exit(1);
    }
    $profile = $profilesById[$profileId];
    $age = isset($profile['age']) ? filter_var($profile['age'], FILTER_VALIDATE_INT) : false;
    if ($profile['gender'] !== $requirements[0]
        || $age === false
        || $age < $requirements[1]
        || $age > $requirements[2]
        || $profile['category'] !== 'Fashion and modeling'
        || stripos($profile['description'], 'supermodel') === false
        || stripos($profile['description'], 'fair complexion') === false
    ) {
        fwrite(STDERR, 'FAIL: Requested model profile does not match gender, age, or description requirements: ' . $profileId . PHP_EOL);
        exit(1);
    }
}

fwrite(STDOUT, 'PASS: ' . $report['count'] . " catalog profiles and validator fixtures passed.\n");
