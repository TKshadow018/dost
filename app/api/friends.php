<?php

function respond_json($status, $payload)
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

function valid_friend_id($value)
{
    return is_string($value) && preg_match('/\A[A-Za-z0-9][A-Za-z0-9_-]{0,79}\z/', $value) === 1;
}

function load_system_friend($friendId, $catalogDirectory)
{
    if (!valid_friend_id($friendId)) {
        return array('status' => 400, 'error' => 'Invalid friend ID');
    }
    $path = $catalogDirectory . DIRECTORY_SEPARATOR . $friendId . '.json';
    $resolvedDirectory = realpath($catalogDirectory);
    $resolvedPath = realpath($path);
    if (!is_string($resolvedDirectory) || !is_string($resolvedPath)
        || dirname($resolvedPath) !== $resolvedDirectory || !is_file($resolvedPath)
    ) {
        return array('status' => 404, 'error' => 'Friend profile not found');
    }
    $profileSize = filesize($resolvedPath);
    if (!is_int($profileSize) || $profileSize > 262144) {
        return array('status' => 500, 'error' => 'Friend profile file is too large');
    }
    $contents = file_get_contents($resolvedPath);
    if (!is_string($contents)) {
        return array('status' => 500, 'error' => 'Could not read friend profile');
    }
    $source = json_decode($contents, true);
    if (!is_array($source) || json_last_error() !== JSON_ERROR_NONE) {
        return array('status' => 500, 'error' => 'Friend profile is not valid JSON');
    }
    foreach (array('name', 'age', 'gender') as $requiredField) {
        if (!isset($source[$requiredField]) || !is_scalar($source[$requiredField])
            || trim((string) $source[$requiredField]) === ''
        ) {
            return array('status' => 500, 'error' => 'Friend profile is missing a required field');
        }
    }

    $profile = array(
        'id' => $friendId,
        'name' => trim((string) $source['name']),
        'age' => trim((string) $source['age']),
        'gender' => trim((string) $source['gender'])
    );
    $stringFields = array(
        'personality', 'interests', 'memories', 'conversationStyle', 'family', 'familyMembers',
        'familyActivities', 'financialCondition', 'address', 'height', 'weight', 'facialFeatures',
        'bodyFeatures', 'busyReason', 'timeZoneId'
    );
    foreach ($stringFields as $field) {
        if (!isset($source[$field])) {
            continue;
        }
        if (!is_string($source[$field]) || strlen($source[$field]) > 12000) {
            return array('status' => 500, 'error' => 'Friend profile contains an invalid field');
        }
        $profile[$field] = $source[$field];
    }
    foreach (array('busyStartHour' => array(0, 23), 'busyDurationHours' => array(4, 8)) as $field => $range) {
        if (isset($source[$field])) {
            if (!is_numeric($source[$field]) || (int) $source[$field] < $range[0] || (int) $source[$field] > $range[1]) {
                return array('status' => 500, 'error' => 'Friend profile contains an invalid schedule');
            }
            $profile[$field] = (int) $source[$field];
        }
    }

    $profile['_photo_path'] = '';
    $profile['photo_url'] = '';
    if (isset($source['photo']) && !is_string($source['photo'])) {
        return array('status' => 500, 'error' => 'Friend profile contains an invalid photo filename');
    }
    $photoFile = isset($source['photo']) ? trim($source['photo']) : '';
    if ($photoFile !== '') {
        $photoFile = ltrim($photoFile, '/');
        if (strncmp($photoFile, 'photos/', 7) === 0) {
            $photoFile = substr($photoFile, 7);
        }
        if (preg_match('/\A[A-Za-z0-9][A-Za-z0-9._-]{0,119}\.(?:jpg|jpeg|png|webp)\z/i', $photoFile) !== 1) {
            return array('status' => 500, 'error' => 'Friend profile contains an invalid photo filename');
        }
        $photoDirectory = $catalogDirectory . DIRECTORY_SEPARATOR . 'photos';
        $photoPath = $photoDirectory . DIRECTORY_SEPARATOR . $photoFile;
        $resolvedDirectory = realpath($photoDirectory);
        $resolvedPhoto = realpath($photoPath);
        if (!is_string($resolvedDirectory) || !is_string($resolvedPhoto)
            || dirname($resolvedPhoto) !== $resolvedDirectory || !is_file($resolvedPhoto)
        ) {
            return array('status' => 500, 'error' => 'Friend profile photo was not found');
        }
        $profile['_photo_path'] = $resolvedPhoto;
        $profile['photo_url'] = 'friends.php?photo=' . rawurlencode($friendId)
            . '&v=' . (string) filemtime($resolvedPhoto);
    }
    return array('status' => 200, 'profile' => $profile);
}

function public_friend_profile($profile)
{
    unset($profile['_photo_path']);
    return $profile;
}

if (!isset($_SERVER['REQUEST_METHOD']) || $_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond_json(405, array('error' => 'GET requests only'));
}

$catalogDirectory = __DIR__ . DIRECTORY_SEPARATOR . 'system_friends';
if (!is_dir($catalogDirectory) || !is_readable($catalogDirectory)) {
    respond_json(503, array('error' => 'System friend catalog is unavailable'));
}

if (isset($_GET['photo'])) {
    $loaded = load_system_friend($_GET['photo'], $catalogDirectory);
    if ($loaded['status'] !== 200) {
        respond_json($loaded['status'], array('error' => $loaded['error']));
    }
    $photoPath = $loaded['profile']['_photo_path'];
    if (!is_string($photoPath) || $photoPath === '' || !is_readable($photoPath) || filesize($photoPath) > 5242880) {
        respond_json(404, array('error' => 'Friend photo not found'));
    }
    if (function_exists('finfo_open')) {
        $mime = (new finfo(FILEINFO_MIME_TYPE))->file($photoPath);
    } else {
        $imageInfo = function_exists('getimagesize') ? @getimagesize($photoPath) : false;
        $mime = is_array($imageInfo) && isset($imageInfo['mime']) ? $imageInfo['mime'] : false;
    }
    if (!in_array($mime, array('image/jpeg', 'image/png', 'image/webp'), true)) {
        respond_json(415, array('error' => 'Friend photo must be JPEG, PNG, or WebP'));
    }
    header('Content-Type: ' . $mime);
    header('Content-Length: ' . (string) filesize($photoPath));
    header('Cache-Control: public, max-age=86400');
    header('X-Content-Type-Options: nosniff');
    readfile($photoPath);
    exit;
}

if (isset($_GET['id'])) {
    $loaded = load_system_friend($_GET['id'], $catalogDirectory);
    if ($loaded['status'] !== 200) {
        respond_json($loaded['status'], array('error' => $loaded['error']));
    }
    respond_json(200, array('profile' => public_friend_profile($loaded['profile'])));
}

$files = glob($catalogDirectory . DIRECTORY_SEPARATOR . '*.json');
if (!is_array($files)) {
    respond_json(500, array('error' => 'Could not list system friend profiles'));
}
sort($files, SORT_STRING);
$profiles = array();
foreach ($files as $file) {
    $friendId = basename($file, '.json');
    $loaded = load_system_friend($friendId, $catalogDirectory);
    if ($loaded['status'] !== 200) {
        error_log('Dost system friend catalog contains an invalid profile: ' . basename($file));
        respond_json(500, array('error' => 'Invalid system friend profile: ' . basename($file)));
    }
    $profiles[] = public_friend_profile($loaded['profile']);
}
respond_json(200, array('profiles' => $profiles));
