<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

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

$configuredDirectory = getenv('DOST_CONVERSATION_STORAGE_DIR');
$storageDirectory = is_string($configuredDirectory) && trim($configuredDirectory) !== ''
    ? rtrim($configuredDirectory, DIRECTORY_SEPARATOR)
    : dirname(__DIR__) . DIRECTORY_SEPARATOR . 'log';

if (!is_dir($storageDirectory)) {
    fwrite(STDOUT, "No conversation archive directory exists; nothing to clean.\n");
    exit(0);
}

$storageRoot = realpath($storageDirectory);
if ($storageRoot === false || is_link($storageDirectory)) {
    fwrite(STDERR, "Could not safely resolve the conversation archive directory.\n");
    exit(1);
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

$heartbeatPath = $storageRoot . DIRECTORY_SEPARATOR . '.cleanup-heartbeat.json';
$heartbeat = json_encode(array(
    'last_run' => gmdate(DATE_ATOM),
    'retention_days' => DOST_RETENTION_DAYS,
    'deleted_sessions' => $deletedSessions,
    'deleted_legacy_files' => $deletedLegacyFiles
));
if (is_string($heartbeat)) {
    @file_put_contents($heartbeatPath, $heartbeat . "\n", LOCK_EX);
    @chmod($heartbeatPath, 0600);
}

fwrite(STDOUT, "Deleted {$deletedSessions} expired session archive(s) and {$deletedLegacyFiles} expired legacy archive file(s).\n");