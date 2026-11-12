<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/archive_store.php';

define('DOST_RETENTION_DAYS', 3650);

function is_within_storage_root($path, $root)
{
    return $path === $root || strpos($path, $root . DIRECTORY_SEPARATOR) === 0;
}

function remove_archive_directory($directory, $root, $preservePath = null)
{
    if (is_link($directory)) {
        return false;
    }
    $realDirectory = realpath($directory);
    if ($realDirectory === false || !is_within_storage_root($realDirectory, $root) || $realDirectory === $root) {
        return false;
    }

    $items = scandir($realDirectory);
    if (!is_array($items)) {
        return false;
    }
    foreach ($items as $item) {
        if ($item === '.' || $item === '..') {
            continue;
        }
        $path = $realDirectory . DIRECTORY_SEPARATOR . $item;
        if ($preservePath !== null && $path === $preservePath) {
            continue;
        }
        if (is_link($path) || !is_dir($path)) {
            if (!@unlink($path)) {
                return false;
            }
        } elseif (!remove_archive_directory($path, $root, $preservePath)) {
            return false;
        }
    }
    if ($preservePath !== null && $realDirectory === dirname($preservePath)) {
        return true;
    }
    return @rmdir($realDirectory);
}

function clean_archive_directories($directory, $root, $cutoff, &$deletedSessions)
{
    $items = scandir($directory);
    if (!is_array($items)) {
        return;
    }
    foreach ($items as $item) {
        if ($item === '.' || $item === '..') {
            continue;
        }
        $path = $directory . DIRECTORY_SEPARATOR . $item;
        if (is_link($path) || !is_dir($path)) {
            continue;
        }

        $archiveFiles = glob($path . DIRECTORY_SEPARATOR . '*.jsonl');
        if (is_array($archiveFiles) && count($archiveFiles) > 0) {
            $lastActivity = null;
            foreach ($archiveFiles as $archiveFile) {
                if (is_link($archiveFile)) {
                    continue;
                }
                $modifiedAt = @filemtime($archiveFile);
                if ($modifiedAt !== false && ($lastActivity === null || $modifiedAt > $lastActivity)) {
                    $lastActivity = $modifiedAt;
                }
            }

            if ($lastActivity !== null && $lastActivity < $cutoff) {
                $lockPath = $path . DIRECTORY_SEPARATOR . '.archive.lock';
                $lockHandle = @fopen($lockPath, 'c+');
                if ($lockHandle === false || !flock($lockHandle, LOCK_EX | LOCK_NB)) {
                    if (is_resource($lockHandle)) {
                        fclose($lockHandle);
                    }
                    continue;
                }
                $contentsRemoved = remove_archive_directory($path, $root, $lockPath);
                flock($lockHandle, LOCK_UN);
                fclose($lockHandle);
                if ($contentsRemoved && @unlink($lockPath) && @rmdir($path)) {
                    $deletedSessions++;
                }
            }
            continue;
        }

        clean_archive_directories($path, $root, $cutoff, $deletedSessions);
        $remaining = @scandir($path);
        if (is_array($remaining) && count($remaining) === 2) {
            @rmdir($path);
        }
    }
}

function prune_install_index($storageRoot)
{
    $indexDirectory = $storageRoot . DIRECTORY_SEPARATOR . '_by_install';
    if (!is_dir($indexDirectory) || is_link($indexDirectory)) {
        return;
    }
    $indexFiles = glob($indexDirectory . DIRECTORY_SEPARATOR . '*.txt');
    if (!is_array($indexFiles)) {
        return;
    }
    $rootPrefix = rtrim(dost_normalize_path($storageRoot), '/');
    foreach ($indexFiles as $indexFile) {
        if (is_link($indexFile) || !is_file($indexFile)) {
            continue;
        }
        $handle = @fopen($indexFile, 'c+');
        if ($handle === false || !flock($handle, LOCK_EX)) {
            if (is_resource($handle)) {
                fclose($handle);
            }
            continue;
        }
        $kept = array();
        rewind($handle);
        while (($line = fgets($handle)) !== false) {
            $relative = str_replace('\\', '/', trim($line));
            if ($relative === '' || $relative[0] === '/' || strpos($relative, '..') !== false) {
                continue;
            }
            $session = $storageRoot . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relative);
            if (is_link($session) || !is_dir($session)) {
                continue;
            }
            $real = realpath($session);
            if ($real === false || !dost_path_is_inside(dost_normalize_path($real), $rootPrefix)) {
                continue;
            }
            $kept[] = $relative;
        }
        if (count($kept) === 0) {
            flock($handle, LOCK_UN);
            fclose($handle);
            @unlink($indexFile);
            continue;
        }
        $payload = implode("\n", $kept) . "\n";
        ftruncate($handle, 0);
        rewind($handle);
        fwrite($handle, $payload);
        fflush($handle);
        @chmod($indexFile, 0600);
        flock($handle, LOCK_UN);
        fclose($handle);
    }
}

function run_conversation_archive_cleanup($storageDirectory, $writeHeartbeat)
{
    if (!is_string($storageDirectory) || $storageDirectory === '' || !is_dir($storageDirectory)) {
        return array(
            'ok' => true,
            'skipped' => true,
            'deleted_sessions' => 0,
            'deleted_legacy_files' => 0
        );
    }
    if (is_link($storageDirectory)) {
        return array('ok' => false, 'error' => 'Could not safely resolve the conversation archive directory.');
    }
    $storageRoot = realpath($storageDirectory);
    if ($storageRoot === false) {
        return array('ok' => false, 'error' => 'Could not safely resolve the conversation archive directory.');
    }

    $cutoff = time() - (DOST_RETENTION_DAYS * 24 * 60 * 60);
    $deletedLegacyFiles = 0;
    foreach (glob($storageRoot . DIRECTORY_SEPARATOR . '*.jsonl') ?: array() as $archiveFile) {
        if (is_link($archiveFile)) {
            continue;
        }
        $modifiedAt = @filemtime($archiveFile);
        if ($modifiedAt !== false && $modifiedAt < $cutoff && @unlink($archiveFile)) {
            $deletedLegacyFiles++;
        }
    }

    $deletedSessions = 0;
    clean_archive_directories($storageRoot, $storageRoot, $cutoff, $deletedSessions);
    prune_install_index($storageRoot);

    if ($writeHeartbeat) {
        $heartbeatPath = $storageRoot . DIRECTORY_SEPARATOR . '.cleanup-heartbeat.json';
        $heartbeat = json_encode(array(
            'last_run' => gmdate(DATE_ATOM),
            'retention_days' => DOST_RETENTION_DAYS,
            'storage_directory' => $storageRoot,
            'deleted_sessions' => $deletedSessions,
            'deleted_legacy_files' => $deletedLegacyFiles
        ));
        if (is_string($heartbeat)) {
            @file_put_contents($heartbeatPath, $heartbeat . "\n", LOCK_EX);
            @chmod($heartbeatPath, 0600);
        }
    }

    return array(
        'ok' => true,
        'skipped' => false,
        'deleted_sessions' => $deletedSessions,
        'deleted_legacy_files' => $deletedLegacyFiles
    );
}

if (defined('DOST_CLEANUP_LIBRARY_ONLY')) {
    return;
}

$storageDirectory = conversation_storage_base_directory();
if ($storageDirectory === false) {
    fwrite(STDERR, "Conversation archive directory is inside the web root or is not an absolute path. Set DOST_CONVERSATION_STORAGE_DIR outside the web root.\n");
    exit(1);
}

$primary = run_conversation_archive_cleanup($storageDirectory, true);
if (!isset($primary['ok']) || $primary['ok'] !== true) {
    fwrite(STDERR, isset($primary['error']) ? $primary['error'] . "\n" : "Cleanup failed.\n");
    exit(1);
}

$legacyDirectory = dost_legacy_web_archive_directory();
$legacy = array('skipped' => true, 'deleted_sessions' => 0, 'deleted_legacy_files' => 0);
if (dost_normalize_path($legacyDirectory) !== dost_normalize_path($storageDirectory)) {
    $legacy = run_conversation_archive_cleanup($legacyDirectory, false);
    if (!isset($legacy['ok']) || $legacy['ok'] !== true) {
        fwrite(STDERR, isset($legacy['error']) ? $legacy['error'] . "\n" : "Legacy cleanup failed.\n");
        exit(1);
    }
}

if (!empty($primary['skipped']) && !empty($legacy['skipped'])) {
    fwrite(STDOUT, "No conversation archive directory exists; nothing to clean.\n");
    exit(0);
}

$deletedSessions = (isset($primary['deleted_sessions']) ? $primary['deleted_sessions'] : 0)
    + (isset($legacy['deleted_sessions']) ? $legacy['deleted_sessions'] : 0);
$deletedLegacyFiles = (isset($primary['deleted_legacy_files']) ? $primary['deleted_legacy_files'] : 0)
    + (isset($legacy['deleted_legacy_files']) ? $legacy['deleted_legacy_files'] : 0);
fwrite(STDOUT, "Deleted {$deletedSessions} expired session archive(s) and {$deletedLegacyFiles} expired legacy archive file(s).\n");