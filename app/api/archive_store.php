<?php

/**
 * Conversation archive location and JSONL writes.
 *
 * The default directory is a sibling of the web root
 * (<account home>/dost-private-data/conversation-archive when this file
 * lives in public_html/api). .htaccess is not what keeps the archive private.
 * Set DOST_CONVERSATION_STORAGE_DIR to another absolute path outside the
 * web root when the default is not writable. A path inside the web root is rejected.
 */

function dost_normalize_path($path)
{
    if (!is_string($path)) {
        return '';
    }
    $path = str_replace('\\', '/', trim($path));
    $absolute = substr($path, 0, 1) === '/';
    $parts = array();
    foreach (explode('/', $path) as $part) {
        if ($part === '' || $part === '.') {
            continue;
        }
        if ($part === '..') {
            array_pop($parts);
            continue;
        }
        $parts[] = $part;
    }
    $normalized = implode('/', $parts);
    if ($absolute) {
        return '/' . $normalized;
    }
    return $normalized;
}

function dost_web_root_directory()
{
    $documentRoot = isset($_SERVER['DOCUMENT_ROOT']) ? $_SERVER['DOCUMENT_ROOT'] : '';
    if (is_string($documentRoot) && trim($documentRoot) !== '') {
        return dost_normalize_path($documentRoot);
    }
    $override = getenv('DOST_WEB_ROOT');
    if (is_string($override) && trim($override) !== '') {
        return dost_normalize_path($override);
    }
    return dost_normalize_path(dirname(__DIR__));
}

function dost_path_is_inside($path, $root)
{
    $path = dost_normalize_path($path);
    $root = rtrim(dost_normalize_path($root), '/');
    if ($path === '' || $root === '' || $root === '/') {
        return false;
    }
    return $path === $root || strpos($path, $root . '/') === 0;
}

function dost_legacy_web_archive_directory()
{
    return dost_normalize_path(dost_web_root_directory() . '/log');
}

function conversation_storage_base_directory()
{
    $configured = getenv('DOST_CONVERSATION_STORAGE_DIR');
    if (is_string($configured) && trim($configured) !== '') {
        $directory = dost_normalize_path($configured);
    } else {
        $webRoot = dost_web_root_directory();
        $parent = dirname($webRoot);
        if ($parent === '' || $parent === '/' || $parent === '.') {
            return false;
        }
        $directory = dost_normalize_path($parent . '/dost-private-data/conversation-archive');
    }
    if (substr($directory, 0, 1) !== '/') {
        return false;
    }
    if (dost_path_is_inside($directory, dost_web_root_directory())) {
        return false;
    }
    return $directory;
}

function dost_prepare_storage_directory($directory)
{
    if (!is_string($directory) || $directory === '') {
        return false;
    }
    $normalized = dost_normalize_path($directory);
    if (substr($normalized, 0, 1) !== '/' || dost_path_is_inside($normalized, dost_web_root_directory())) {
        return false;
    }
    if (!is_dir($normalized) && !is_link($normalized) && !@mkdir($normalized, 0700, true) && !is_dir($normalized)) {
        return false;
    }
    $real = realpath($normalized);
    if ($real === false || is_link($real) || !is_dir($real)
        || dost_path_is_inside(dost_normalize_path($real), dost_web_root_directory())
    ) {
        return false;
    }
    @chmod($real, 0700);
    return $real;
}

function valid_uuid($value)
{
    return is_string($value) && preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/i', $value) === 1;
}

function safe_log_path_segment($value, $fallback)
{
    if (!is_string($value)) {
        return $fallback;
    }
    $segment = preg_replace('/[^\p{L}\p{M}\p{N}._-]+/u', '_', trim($value));
    if (!is_string($segment)) {
        $segment = preg_replace('/[^A-Za-z0-9._-]+/', '_', trim($value));
    }
    $segment = preg_replace('/^(.{1,64}).*$/us', '$1', $segment);
    $segment = trim($segment, " ._-");
    return $segment === '' ? $fallback : $segment;
}

function conversation_storage_directory($userName, $clientIp, $friendName, $sessionId)
{
    $baseDirectory = dost_prepare_storage_directory(conversation_storage_base_directory());
    if ($baseDirectory === false) {
        return false;
    }
    $userSegment = safe_log_path_segment($userName, 'unknown-user');
    $ipSegment = safe_log_path_segment(str_replace(':', '-', $clientIp), 'unknown-ip');
    $friendSegment = safe_log_path_segment($friendName, 'unknown-friend');
    $directory = $baseDirectory . DIRECTORY_SEPARATOR . $userSegment . '_' . $ipSegment
        . DIRECTORY_SEPARATOR . $friendSegment . DIRECTORY_SEPARATOR . strtolower($sessionId);
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    if (is_link($directory)) {
        return false;
    }
    $real = realpath($directory);
    if ($real === false || !dost_path_is_inside(dost_normalize_path($real), dost_normalize_path($baseDirectory))) {
        return false;
    }
    @chmod($real, 0700);
    return $real;
}

function save_conversation_image($directory, $messageId, $imageData, $imageMime)
{
    if (!valid_uuid($messageId) || !is_string($imageData) || strlen($imageData) > 500000
        || !in_array($imageMime, array('image/jpeg', 'image/png', 'image/webp'), true)
    ) {
        return false;
    }
    $bytes = base64_decode($imageData, true);
    if (!is_string($bytes) || strlen($bytes) > 375000) {
        return false;
    }
    $detectedMime = detect_image_mime($bytes);
    if ($detectedMime !== $imageMime) {
        return false;
    }
    $extension = $imageMime === 'image/jpeg' ? 'jpg' : substr($imageMime, 6);
    $filename = strtolower($messageId) . '.' . $extension;
    $path = $directory . DIRECTORY_SEPARATOR . $filename;
    if (@file_put_contents($path, $bytes, LOCK_EX) !== strlen($bytes)) {
        return false;
    }
    @chmod($path, 0600);
    return $filename;
}

function detect_image_mime($bytes)
{
    if (class_exists('finfo')) {
        return (new finfo(FILEINFO_MIME_TYPE))->buffer($bytes);
    }
    if (strncmp($bytes, "\x89PNG\r\n\x1a\n", 8) === 0) {
        return 'image/png';
    }
    if (strncmp($bytes, "\xff\xd8\xff", 3) === 0) {
        return 'image/jpeg';
    }
    if (strlen($bytes) >= 12 && substr($bytes, 0, 4) === 'RIFF' && substr($bytes, 8, 4) === 'WEBP') {
        return 'image/webp';
    }
    return false;
}

function dost_remember_install_session($baseDirectory, $installationId, $sessionDirectory)
{
    if (!valid_uuid($installationId) || !is_string($baseDirectory) || !is_string($sessionDirectory)) {
        return false;
    }
    $base = rtrim(dost_normalize_path($baseDirectory), '/');
    $session = dost_normalize_path($sessionDirectory);
    if ($base === '' || !dost_path_is_inside($session, $base)) {
        return false;
    }
    $relative = substr($session, strlen($base) + 1);
    if (!is_string($relative) || $relative === '' || strpos($relative, '..') !== false) {
        return false;
    }
    $indexDirectory = $baseDirectory . DIRECTORY_SEPARATOR . '_by_install';
    if (!is_dir($indexDirectory) && !@mkdir($indexDirectory, 0700, true) && !is_dir($indexDirectory)) {
        return false;
    }
    @chmod($indexDirectory, 0700);
    $indexPath = $indexDirectory . DIRECTORY_SEPARATOR . strtolower($installationId) . '.txt';
    $handle = @fopen($indexPath, 'c+');
    if ($handle === false || !flock($handle, LOCK_EX)) {
        if (is_resource($handle)) {
            fclose($handle);
        }
        return false;
    }
    $known = false;
    rewind($handle);
    while (($line = fgets($handle)) !== false) {
        if (trim($line) === $relative) {
            $known = true;
            break;
        }
    }
    $saved = true;
    if (!$known) {
        fseek($handle, 0, SEEK_END);
        $encoded = $relative . "\n";
        $saved = fwrite($handle, $encoded) === strlen($encoded) && fflush($handle);
    }
    @chmod($indexPath, 0600);
    flock($handle, LOCK_UN);
    fclose($handle);
    return $saved;
}

function append_conversation_events($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $events)
{
    if (!valid_uuid($installationId) || !valid_uuid($friendId) || !valid_uuid($sessionId) || !is_array($events)) {
        return false;
    }
    foreach ($events as $event) {
        if (!is_array($event) || !valid_uuid(isset($event['message_id']) ? $event['message_id'] : null)
            || !isset($event['role'], $event['content'], $event['timestamp_ms'])
            || !in_array($event['role'], array('user', 'assistant'), true)
            || !is_string($event['content']) || strlen($event['content']) > 25000
            || !is_numeric($event['timestamp_ms'])
            || (isset($event['model']) && (!is_string($event['model']) || strlen($event['model']) > 160))
            || (isset($event['image_data']) && (!isset($event['image_mime']) || !is_string($event['image_mime'])))
        ) {
            return false;
        }
    }

    $baseDirectory = dost_prepare_storage_directory(conversation_storage_base_directory());
    if ($baseDirectory === false) {
        return false;
    }
    $directory = conversation_storage_directory($userName, $clientIp, $friendName, $sessionId);
    if ($directory === false) {
        return false;
    }
    $sessionLock = @fopen($directory . DIRECTORY_SEPARATOR . '.archive.lock', 'c+');
    if ($sessionLock === false || !flock($sessionLock, LOCK_EX)) {
        if (is_resource($sessionLock)) {
            fclose($sessionLock);
        }
        return false;
    }
    @chmod($directory . DIRECTORY_SEPARATOR . '.archive.lock', 0600);

    $existingFiles = glob($directory . DIRECTORY_SEPARATOR . '*.jsonl');
    if (is_array($existingFiles) && count($existingFiles) > 0) {
        sort($existingFiles, SORT_STRING);
        $path = $existingFiles[0];
    } else {
        $firstTimestamp = null;
        foreach ($events as $event) {
            $timestamp = (int) $event['timestamp_ms'];
            if ($timestamp > 0 && ($firstTimestamp === null || $timestamp < $firstTimestamp)) {
                $firstTimestamp = $timestamp;
            }
        }
        $timestampSeconds = $firstTimestamp === null ? time() : (int) floor($firstTimestamp / 1000);
        $filename = gmdate('Y-m-d_H-i-s', $timestampSeconds) . '.jsonl';
        $path = $directory . DIRECTORY_SEPARATOR . $filename;
    }
    $handle = @fopen($path, 'c+');
    if ($handle === false || !flock($handle, LOCK_EX)) {
        flock($sessionLock, LOCK_UN);
        fclose($sessionLock);
        if (is_resource($handle)) {
            fclose($handle);
        }
        return false;
    }
    flock($sessionLock, LOCK_UN);
    fclose($sessionLock);

    $knownIds = array();
    rewind($handle);
    while (($line = fgets($handle)) !== false) {
        $saved = json_decode($line, true);
        if (is_array($saved) && isset($saved['message_id'])) {
            $knownIds[$saved['message_id']] = true;
        }
    }

    $newLines = array();
    foreach ($events as $event) {
        if (isset($knownIds[$event['message_id']])) {
            continue;
        }
        $record = array(
            'installation_id' => strtolower($installationId),
            'message_id' => $event['message_id'],
            'role' => $event['role'],
            'content' => $event['content'],
            'timestamp_ms' => (int) $event['timestamp_ms'],
            'saved_at' => gmdate(DATE_ATOM)
        );
        if (isset($event['model']) && is_string($event['model']) && $event['model'] !== '') {
            $record['model'] = $event['model'];
        }
        if (isset($event['image_data'])) {
            $imageMime = isset($event['image_mime']) ? $event['image_mime'] : '';
            $imageFilename = save_conversation_image($directory, $event['message_id'], $event['image_data'], $imageMime);
            if ($imageFilename === false) {
                flock($handle, LOCK_UN);
                fclose($handle);
                return false;
            }
            $record['image_file'] = $imageFilename;
        }
        $encoded = json_encode($record, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
        if (!is_string($encoded)) {
            flock($handle, LOCK_UN);
            fclose($handle);
            return false;
        }
        $newLines[] = $encoded . "\n";
        $knownIds[$event['message_id']] = true;
    }
    if (count($newLines) === 0) {
        flock($handle, LOCK_UN);
        fclose($handle);
        dost_remember_install_session($baseDirectory, $installationId, $directory);
        return true;
    }
    fseek($handle, 0, SEEK_END);
    $savedSuccessfully = true;
    foreach ($newLines as $line) {
        if (fwrite($handle, $line) !== strlen($line)) {
            $savedSuccessfully = false;
            break;
        }
    }
    $savedSuccessfully = fflush($handle) && $savedSuccessfully;
    if (function_exists('fsync')) {
        $savedSuccessfully = fsync($handle) && $savedSuccessfully;
    }
    @chmod($path, 0600);
    flock($handle, LOCK_UN);
    fclose($handle);
    if ($savedSuccessfully) {
        dost_remember_install_session($baseDirectory, $installationId, $directory);
    }
    return $savedSuccessfully;
}

function append_conversation_event($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $event)
{
    return append_conversation_events($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, array($event));
}
