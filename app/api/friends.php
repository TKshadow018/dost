<?php
require_once __DIR__ . DIRECTORY_SEPARATOR . 'catalog_validation.php';
require_once __DIR__ . DIRECTORY_SEPARATOR . 'explore_support.php';

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

function load_system_friend($friendId, $catalogDirectory, $chatLanguage = 'en')
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
    if (count(validate_system_friend_profile($source)) > 0) {
        return array('status' => 500, 'error' => 'Friend profile does not match the catalog schema');
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
        'schemaVersion' => $source['schemaVersion'],
        'name' => trim((string) $source['name']),
        'age' => trim((string) $source['age']),
        'gender' => trim((string) $source['gender'])
    );
    $stringFields = array(
        'category', 'country', 'description', 'relationship', 'personality', 'interests', 'memories', 'conversationStyle', 'family', 'familyMembers',
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
    $profile['greeting'] = localized_system_friend_greeting($source['greeting'], $chatLanguage);
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
        $photoLocation = system_friend_photo_location($catalogDirectory, $photoFile);
        if (!is_array($photoLocation)) {
            return array('status' => 500, 'error' => 'Friend profile contains an invalid photo filename');
        }
        $resolvedDirectory = realpath($photoLocation['directory']);
        $resolvedPhoto = realpath($photoLocation['path']);
        if (is_string($resolvedPhoto)) {
            if (!is_string($resolvedDirectory)
                || dirname($resolvedPhoto) !== $resolvedDirectory
                || !is_file($resolvedPhoto)
                || !is_readable($resolvedPhoto)
            ) {
                return array('status' => 500, 'error' => 'Friend profile photo was not found');
            }
            $profile['_photo_path'] = $resolvedPhoto;
            $profile['photo_url'] = 'friends.php?photo=' . rawurlencode($friendId)
                . '&v=' . (string) filemtime($resolvedPhoto);
        }
    }
    return array('status' => 200, 'profile' => $profile);
}

function public_friend_profile($profile)
{
    unset($profile['_photo_path']);
    return $profile;
}

if (!isset($_SERVER['REQUEST_METHOD']) || !in_array($_SERVER['REQUEST_METHOD'], array('GET', 'POST'), true)) {
    header('Allow: GET, POST');
    respond_json(405, array('error' => 'GET or POST requests only'));
}

$catalogDirectory = __DIR__ . DIRECTORY_SEPARATOR . 'system_friends';
if (!is_dir($catalogDirectory) || !is_readable($catalogDirectory)) {
    respond_json(503, array('error' => 'System friend catalog is unavailable'));
}

if (isset($_SERVER['CONTENT_LENGTH']) && (int) $_SERVER['CONTENT_LENGTH'] > 4096) {
    respond_json(413, array('error' => 'Request is too large'));
}
$config = array();
$configPath = __DIR__ . DIRECTORY_SEPARATOR . 'config.php';
if (is_file($configPath)) {
    $loadedConfig = require $configPath;
    if (is_array($loadedConfig)) {
        $config = $loadedConfig;
    }
}
$authorization = dost_explore_authorization_header($_SERVER);
$identity = dost_explore_authorize($authorization, $config, time());
if ($identity['status'] !== 200) {
    respond_json($identity['status'], array('error' => $identity['error']));
}
if (!dost_explore_enabled($config)) {
    respond_json(503, array('error' => 'Explore is not enabled'));
}
$explorePlusActive = dost_explore_plus_active($identity['claims'], time());

if ($_SERVER['REQUEST_METHOD'] === 'GET' && isset($_GET['photo'])) {
    $chatLanguage = isset($_GET['chat_language']) ? $_GET['chat_language'] : 'en';
    $loaded = load_system_friend($_GET['photo'], $catalogDirectory, $chatLanguage);
    if ($loaded['status'] !== 200) {
        respond_json(404, array('error' => 'Friend photo not found'));
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
    header('Cache-Control: private, max-age=86400');
    header('X-Content-Type-Options: nosniff');
    readfile($photoPath);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $body = file_get_contents('php://input', false, null, 0, 4097);
    if (is_string($body) && strlen($body) > 4096) {
        respond_json(413, array('error' => 'Request is too large'));
    }
    $request = is_string($body) ? json_decode($body, true) : null;
    if (!is_array($request) || json_last_error() !== JSON_ERROR_NONE
        || !isset($request['friend_id'], $request['kind'], $request['action'])
        || !is_string($request['friend_id']) || !is_string($request['kind'])
        || !is_string($request['action'])
    ) {
        respond_json(400, array('error' => 'Invalid friend registration'));
    }
    if ($request['kind'] === 'catalog') {
        $catalogProfile = load_system_friend($request['friend_id'], $catalogDirectory);
        if ($catalogProfile['status'] !== 200) {
            respond_json(400, array('error' => 'Invalid catalog friend ID'));
        }
    } elseif ($request['kind'] !== 'custom' || !dost_explore_valid_uuid($request['friend_id'])) {
        respond_json(400, array('error' => 'Invalid custom friend ID'));
    }
    $storageDirectory = dost_explore_storage_directory($config);
    if ($storageDirectory === false) {
        respond_json(503, array('error' => 'Explore friend storage is unavailable'));
    }
    $result = dost_explore_registry_change(
        $storageDirectory,
        $identity['uid'],
        $request['friend_id'],
        $request['kind'],
        $request['action'],
        $explorePlusActive
    );
    respond_json($result['status'], $result);
}

if (isset($_GET['id'])) {
    $chatLanguage = isset($_GET['chat_language']) ? $_GET['chat_language'] : 'en';
    $loaded = load_system_friend($_GET['id'], $catalogDirectory, $chatLanguage);
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
    $chatLanguage = isset($_GET['chat_language']) ? $_GET['chat_language'] : 'en';
    $loaded = load_system_friend($friendId, $catalogDirectory, $chatLanguage);
    if ($loaded['status'] !== 200) {
        error_log('Dost system friend catalog contains an invalid profile: ' . basename($file));
        respond_json(500, array('error' => 'Invalid system friend profile: ' . basename($file)));
    }
    $profiles[] = public_friend_listing($loaded['profile']);
}
respond_json(200, array('profiles' => $profiles));
