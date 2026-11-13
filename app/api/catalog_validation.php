<?php

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
        'name', 'age', 'gender', 'category', 'description', 'relationship',
        'greeting', 'personality', 'interests', 'conversationStyle', 'timeZoneId'
    ) as $field) {
        if (!isset($profile[$field]) || !is_string($profile[$field]) || trim($profile[$field]) === '') {
            $errors[] = $field . ' must be a non-empty string.';
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
    }

    return $errors;
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
        if (!isset($profile['photo']) || trim((string) $profile['photo']) === '') {
            $warnings[] = $id . ': no profile photo is configured.';
        }
    }

    return array('count' => count($files), 'errors' => $errors, 'warnings' => $warnings);
}
