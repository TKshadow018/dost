<?php

function system_friend_photo_location($catalogDirectory, $photo)
{
    if (!is_string($photo) || trim($photo) === '') {
        return null;
    }
    if (strncmp($photo, 'photos/', 7) === 0) {
        $photoDirectory = $catalogDirectory . DIRECTORY_SEPARATOR . 'photos';
        $photoFile = substr($photo, 7);
    } else {
        $photoDirectory = $catalogDirectory;
        $photoFile = $photo;
    }
    if (preg_match('/\A[A-Za-z0-9][A-Za-z0-9._-]{0,119}\.(?:jpg|jpeg|png|webp)\z/i', $photoFile) !== 1) {
        return null;
    }
    return array(
        'directory' => $photoDirectory,
        'path' => $photoDirectory . DIRECTORY_SEPARATOR . $photoFile
    );
}

function validate_system_friend_profile($profile)
{
    $errors = array();
    if (!is_array($profile)) {
        return array('Profile must be a JSON object.');
    }

    if (!isset($profile['schemaVersion']) || !is_int($profile['schemaVersion'])
        || $profile['schemaVersion'] !== 1
    ) {
        $errors[] = 'schemaVersion must be the supported integer 1.';
    }

    foreach (array(
        'name', 'age', 'gender', 'category', 'country', 'description', 'relationship',
        'personality', 'interests', 'conversationStyle', 'timeZoneId'
    ) as $field) {
        if (!isset($profile[$field]) || !is_string($profile[$field]) || trim($profile[$field]) === '') {
            $errors[] = $field . ' must be a non-empty string.';
        }
    }

    if (isset($profile['gender']) && !in_array($profile['gender'], array('Male', 'Female'), true)) {
        $errors[] = 'gender must be Male or Female.';
    }

    $englishSpeakingCountries = array('Australia', 'Canada', 'Ireland', 'New Zealand', 'United Kingdom', 'United States');
    if (isset($profile['country'])
        && !in_array($profile['country'], array('Bangladesh', 'India', 'Japan', 'Nepal'), true)
        && !in_array($profile['country'], $englishSpeakingCountries, true)
    ) {
        $errors[] = 'country must be Bangladesh, India, Japan, Nepal, or a supported English-speaking country.';
    }

    if (!isset($profile['greeting']) || !is_array($profile['greeting'])) {
        $errors[] = 'greeting must contain localized en, bn, and hi strings.';
    } else {
        foreach (array('en', 'bn', 'hi') as $language) {
            if (!isset($profile['greeting'][$language])
                || !is_string($profile['greeting'][$language])
                || trim($profile['greeting'][$language]) === ''
            ) {
                $errors[] = 'greeting must contain a non-empty ' . $language . ' string.';
            }
        }
    }

    if (isset($profile['age'])) {
        $age = filter_var($profile['age'], FILTER_VALIDATE_INT);
        if ($age === false || $age < 13 || $age > 120) {
            $errors[] = 'age must be an integer from 13 through 120.';
        }
    }

    if (isset($profile['timeZoneId'])
        && !in_array($profile['timeZoneId'], timezone_identifiers_list(), true)
    ) {
        $errors[] = 'timeZoneId must be an IANA time-zone identifier.';
    }

    foreach (array('busyStartHour' => array(0, 23), 'busyDurationHours' => array(4, 8)) as $field => $range) {
        if (!isset($profile[$field]) || !is_int($profile[$field])
            || $profile[$field] < $range[0] || $profile[$field] > $range[1]
        ) {
            $errors[] = $field . ' is outside its supported schedule range.';
        }
    }
    if (isset($profile['photo']) && !is_string($profile['photo'])) {
        $errors[] = 'photo must be a string when configured.';
    } elseif (isset($profile['photo']) && trim($profile['photo']) !== ''
        && system_friend_photo_location(__DIR__ . DIRECTORY_SEPARATOR . 'system_friends', $profile['photo']) === null
    ) {
        $errors[] = 'photo must be a valid image filename in the catalog or photos directory.';
    }

    return $errors;
}

function localized_system_friend_greeting($greetings, $language = 'en')
{
    if (!is_array($greetings)) {
        return '';
    }
    if (!is_string($language) || !in_array($language, array('en', 'bn', 'hi'), true)) {
        $language = 'en';
    }
    $greeting = isset($greetings[$language]) ? $greetings[$language] : $greetings['en'];
    return is_string($greeting) ? trim($greeting) : '';
}

function validate_system_friend_catalog($catalogDirectory)
{
    $errors = array();
    $warnings = array();
    $files = glob($catalogDirectory . DIRECTORY_SEPARATOR . '*.json');
    if (!is_array($files) || count($files) === 0) {
        return array('count' => 0, 'errors' => array('No catalog profiles were found.'), 'warnings' => array());
    }
    sort($files, SORT_STRING);
    $voiceProfiles = array();

    foreach ($files as $file) {
        $id = basename($file, '.json');
        $contents = file_get_contents($file);
        $profile = is_string($contents) ? json_decode($contents, true) : null;
        if (!is_array($profile) || json_last_error() !== JSON_ERROR_NONE) {
            $errors[] = $id . ': invalid JSON.';
            continue;
        }
        foreach (validate_system_friend_profile($profile) as $error) {
            $errors[] = $id . ': ' . $error;
        }

        $voiceSignature = strtolower(trim(preg_replace(
            '/\s+/',
            ' ',
            (isset($profile['personality']) ? $profile['personality'] : '') . ' ' .
                (isset($profile['conversationStyle']) ? $profile['conversationStyle'] : '')
        )));
        if ($voiceSignature !== '') {
            if (isset($voiceProfiles[$voiceSignature])) {
                $errors[] = $id . ': voice profile duplicates ' . $voiceProfiles[$voiceSignature] . '.';
            } else {
                $voiceProfiles[$voiceSignature] = $id;
            }
        }
        $photo = isset($profile['photo']) && is_string($profile['photo']) ? trim($profile['photo']) : '';
        if ($photo === '') {
            $warnings[] = $id . ': no profile photo is configured.';
        } else {
            if (preg_match('/\A' . preg_quote($id, '/') . '\.(?:jpg|jpeg|png|webp)\z/i', $photo) !== 1) {
                $errors[] = $id . ': photo must use the profile ID and a supported image extension.';
            }
            $location = system_friend_photo_location($catalogDirectory, $photo);
            if (is_array($location)) {
                $resolvedDirectory = realpath($location['directory']);
                $resolvedPhoto = realpath($location['path']);
                if (!is_string($resolvedPhoto)) {
                    $warnings[] = $id . ': configured photo is missing: ' . $photo . '.';
                } elseif (!is_string($resolvedDirectory)
                    || dirname($resolvedPhoto) !== $resolvedDirectory
                    || !is_file($resolvedPhoto)
                    || !is_readable($resolvedPhoto)
                ) {
                    $errors[] = $id . ': photo resolves outside its allowed directory or is unreadable.';
                }
            }
        }
    }

    return array('count' => count($files), 'errors' => $errors, 'warnings' => $warnings);
}
