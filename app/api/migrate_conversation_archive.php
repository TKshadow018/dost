<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/archive_store.php';

function dost_migrate_legacy_archive($apply)
{
    $configuredSource = getenv('DOST_LEGACY_ARCHIVE_DIR');
    if (is_string($configuredSource) && trim($configuredSource) !== '') {
        $source = dost_normalize_path($configuredSource);
    } else {
        $source = dost_legacy_web_archive_directory();
    }
    $destination = conversation_storage_base_directory();
    if ($destination === false) {
        return array('ok' => false, 'error' => 'Destination is missing or inside the web root.');
    }
    if (substr($source, 0, 1) !== '/' || !dost_path_is_inside($source, dost_web_root_directory())) {
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
            'skipped' => array(),
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
    $skipped = array();
    $items = scandir($source);
    if (!is_array($items)) {
        return array('ok' => false, 'error' => 'Could not read the legacy archive directory.');
    }
    foreach ($items as $item) {
        if ($item === '.' || $item === '..' || $item === '.htaccess') {
            continue;
        }
        $from = $source . DIRECTORY_SEPARATOR . $item;
        $to = $prepared . DIRECTORY_SEPARATOR . $item;
        if (is_link($from)) {
            $skipped[] = $item . ' (symlink)';
            continue;
        }
        if (file_exists($to)) {
            $skipped[] = $item . ' (already at destination)';
            continue;
        }
        if ($apply) {
            if (!@rename($from, $to)) {
                return array(
                    'ok' => false,
                    'error' => 'Could not move ' . $item . '. Destination filesystem may differ from the web root.',
                    'moved' => $moved,
                    'skipped' => $skipped
                );
            }
        }
        $moved[] = $item;
    }

    return array(
        'ok' => true,
        'source' => $source,
        'destination' => $prepared,
        'moved' => $moved,
        'skipped' => $skipped,
        'applied' => $apply ? true : false,
        'note' => 'Left .htaccess in the legacy directory. Delete public_html/log after the move if it only contains that file.'
    );
}

if (defined('DOST_MIGRATE_LIBRARY_ONLY')) {
    return;
}

$apply = in_array('--apply', $argv, true);
$result = dost_migrate_legacy_archive($apply);
if (!isset($result['ok']) || $result['ok'] !== true) {
    fwrite(STDERR, (isset($result['error']) ? $result['error'] : 'Migration failed.') . "\n");
    exit(1);
}

$moved = isset($result['moved']) ? $result['moved'] : array();
$skipped = isset($result['skipped']) ? $result['skipped'] : array();
fwrite(STDOUT, ($apply ? 'Moved' : 'Would move') . ' ' . count($moved) . " item(s).\n");
foreach ($moved as $item) {
    fwrite(STDOUT, " - {$item}\n");
}
if (count($skipped) > 0) {
    fwrite(STDOUT, "Skipped " . count($skipped) . " item(s).\n");
    foreach ($skipped as $item) {
        fwrite(STDOUT, " - {$item}\n");
    }
}
if (!$apply) {
    fwrite(STDOUT, "Re-run with --apply to move the files.\n");
}
if (isset($result['note'])) {
    fwrite(STDOUT, $result['note'] . "\n");
}
