<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/archive_store.php';

function dost_relative_archive_path($path, $root)
{
    $path = dost_normalize_path($path);
    $root = rtrim(dost_normalize_path($root), '/');
    if ($root !== '' && ($path === $root || strpos($path, $root . '/') === 0)) {
        $relative = substr($path, strlen($root) + 1);
        return $relative === false || $relative === '' ? basename($path) : $relative;
    }
    return $path;
}

function dost_merge_jsonl_file($sourceFile, $destinationFile, $apply)
{
    $sourceLines = @file($sourceFile, FILE_IGNORE_NEW_LINES);
    if (!is_array($sourceLines)) {
        return false;
    }
    $destinationLines = is_file($destinationFile) ? @file($destinationFile, FILE_IGNORE_NEW_LINES) : array();
    if (!is_array($destinationLines)) {
        return false;
    }
    $known = array();
    $merged = array();
    foreach (array($destinationLines, $sourceLines) as $group) {
        foreach ($group as $line) {
            if (trim($line) === '') {
                continue;
            }
            $decoded = json_decode($line, true);
            $messageId = is_array($decoded) && isset($decoded['message_id']) && is_string($decoded['message_id'])
                ? $decoded['message_id']
                : '';
            $key = $messageId !== '' ? 'id:' . $messageId : 'line:' . $line;
            if (isset($known[$key])) {
                continue;
            }
            $known[$key] = true;
            $merged[] = $line;
        }
    }
    if (!$apply) {
        return true;
    }
    $payload = count($merged) === 0 ? '' : implode("\n", $merged) . "\n";
    if (@file_put_contents($destinationFile, $payload, LOCK_EX) === false) {
        return false;
    }
    @chmod($destinationFile, 0600);
    return @unlink($sourceFile);
}

function dost_migrate_archive_entry($from, $to, $apply, $sourceRoot, &$moved, &$leftBehind)
{
    $label = dost_relative_archive_path($from, $sourceRoot);
    if (is_link($from)) {
        $leftBehind[] = $label . ' (symlink)';
        return;
    }
    if (is_dir($from)) {
        if (!is_dir($to)) {
            if ($apply && !@mkdir($to, 0700, true) && !is_dir($to)) {
                $leftBehind[] = $label . ' (could not create destination)';
                return;
            }
        }
        $items = @scandir($from);
        if (!is_array($items)) {
            $leftBehind[] = $label . ' (unreadable)';
            return;
        }
        foreach ($items as $item) {
            if ($item === '.' || $item === '..' || $item === '.htaccess') {
                continue;
            }
            dost_migrate_archive_entry(
                $from . DIRECTORY_SEPARATOR . $item,
                $to . DIRECTORY_SEPARATOR . $item,
                $apply,
                $sourceRoot,
                $moved,
                $leftBehind
            );
        }
        if ($apply) {
            $remaining = @scandir($from);
            $onlyKept = is_array($remaining);
            if ($onlyKept) {
                foreach ($remaining as $item) {
                    if ($item !== '.' && $item !== '..' && $item !== '.htaccess') {
                        $onlyKept = false;
                        break;
                    }
                }
            }
            if ($onlyKept && !is_file($from . DIRECTORY_SEPARATOR . '.htaccess')) {
                @rmdir($from);
            }
        }
        return;
    }
    if (!is_file($from)) {
        $leftBehind[] = $label;
        return;
    }

    $destinationDirectory = dirname($to);
    $isJsonl = substr($from, -6) === '.jsonl';
    if ($isJsonl) {
        $target = $to;
        if (!file_exists($target) && is_dir($destinationDirectory)) {
            $existing = glob($destinationDirectory . DIRECTORY_SEPARATOR . '*.jsonl');
            if (is_array($existing) && count($existing) > 0) {
                sort($existing, SORT_STRING);
                $target = $existing[0];
            }
        }
        if (is_file($target)) {
            if (!dost_merge_jsonl_file($from, $target, $apply)) {
                $leftBehind[] = $label . ' (could not merge)';
                return;
            }
            $moved[] = $label . ' (merged)';
            return;
        }
    }

    if (!file_exists($to)) {
        if ($apply) {
            if (!is_dir($destinationDirectory) && !@mkdir($destinationDirectory, 0700, true) && !is_dir($destinationDirectory)) {
                $leftBehind[] = $label . ' (could not create destination)';
                return;
            }
            if (!@rename($from, $to)) {
                $leftBehind[] = $label . ' (could not move)';
                return;
            }
        }
        $moved[] = $label;
        return;
    }

    $alternate = false;
    $base = basename($to);
    for ($suffix = 1; $suffix <= 100; $suffix++) {
        $candidate = $destinationDirectory . DIRECTORY_SEPARATOR . 'migrated-' . $suffix . '-' . $base;
        if (!file_exists($candidate)) {
            $alternate = $candidate;
            break;
        }
    }
    if ($alternate === false) {
        $leftBehind[] = $label . ' (no free destination name)';
        return;
    }
    if ($apply && !@rename($from, $alternate)) {
        $leftBehind[] = $label . ' (could not move)';
        return;
    }
    $moved[] = $label . ' (renamed)';
}

function dost_migrate_legacy_archive($apply)
{
    $configuredSource = getenv('DOST_LEGACY_ARCHIVE_DIR');
    if (is_string($configuredSource) && trim($configuredSource) !== '') {
        $source = dost_resolved_directory($configuredSource);
    } else {
        $source = dost_legacy_web_archive_directory();
    }
    $destination = conversation_storage_base_directory();
    if ($destination === false) {
        return array('ok' => false, 'error' => 'Destination is missing or inside the web root.');
    }
    if (substr($source, 0, 1) !== '/' || !dost_path_is_inside_web_root($source)) {
        return array('ok' => false, 'error' => 'Legacy source must be an absolute directory inside the web root.');
    }
    if (dost_normalize_path($source) === dost_normalize_path($destination)) {
        return array('ok' => false, 'error' => 'Source and destination are the same directory.');
    }
    if (!is_dir($source)) {
        return array(
            'ok' => true,
            'source' => $source,
            'destination' => $destination,
            'moved' => array(),
            'left_behind' => array(),
            'note' => 'No legacy archive directory exists.'
        );
    }
    if (is_link($source)) {
        return array('ok' => false, 'error' => 'Legacy source is a symlink; refusing to follow it.');
    }

    $prepared = dost_prepare_storage_directory($destination);
    if ($prepared === false) {
        return array('ok' => false, 'error' => 'Could not create the private archive directory.');
    }

    $moved = array();
    $leftBehind = array();
    dost_migrate_archive_entry($source, $prepared, $apply, $source, $moved, $leftBehind);
    $ok = count($leftBehind) === 0;

    return array(
        'ok' => $ok,
        'source' => $source,
        'destination' => $prepared,
        'moved' => $moved,
        'left_behind' => $leftBehind,
        'applied' => $apply ? true : false,
        'error' => $ok ? null : 'Some legacy archive files are still inside the web root.',
        'note' => 'Left .htaccess in the legacy directory. Delete public_html/log after the move if it only contains that file.'
    );
}

if (defined('DOST_MIGRATE_LIBRARY_ONLY')) {
    return;
}

$apply = in_array('--apply', $argv, true);
$result = dost_migrate_legacy_archive($apply);
$moved = isset($result['moved']) ? $result['moved'] : array();
$leftBehind = isset($result['left_behind']) ? $result['left_behind'] : array();
fwrite(STDOUT, ($apply ? 'Moved' : 'Would move') . ' ' . count($moved) . " item(s).\n");
foreach ($moved as $item) {
    fwrite(STDOUT, " - {$item}\n");
}
if (count($leftBehind) > 0) {
    fwrite(STDERR, "Left behind " . count($leftBehind) . " item(s):\n");
    foreach ($leftBehind as $item) {
        fwrite(STDERR, " - {$item}\n");
    }
}
if (!isset($result['ok']) || $result['ok'] !== true || count($leftBehind) > 0) {
    fwrite(STDERR, (isset($result['error']) && is_string($result['error']) ? $result['error'] : 'Migration failed.') . "\n");
    exit(1);
}
if (!$apply) {
    fwrite(STDOUT, "Re-run with --apply to move the files.\n");
}
if (isset($result['note'])) {
    fwrite(STDOUT, $result['note'] . "\n");
}
