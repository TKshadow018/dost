<?php

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "CLI only.\n");
    exit(1);
}

require_once dirname(__FILE__) . DIRECTORY_SEPARATOR . 'telemetry_store.php';

$directory = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'dost-telemetry-test-' . bin2hex(random_bytes(8));
if (!mkdir($directory, 0700)) {
    fwrite(STDERR, "Could not create temporary telemetry test directory.\n");
    exit(1);
}

$failures = array();
$today = '2026-03-04';
$oldDay = '2025-11-30';
if (!dost_telemetry_is_allowed_event('memory_saved') || dost_telemetry_is_allowed_event('chat_content')) {
    $failures[] = 'event allowlist accepts only approved event names';
}
if (!dost_telemetry_record($directory, 'memory_saved', $today) || !dost_telemetry_record($directory, 'memory_saved', $today)) {
    $failures[] = 'approved events are recorded';
}
$counts = json_decode((string) file_get_contents($directory . DIRECTORY_SEPARATOR . $today . '.json'), true);
if (!is_array($counts) || ($counts['memory_saved'] ?? null) !== 2) {
    $failures[] = 'daily event counts are aggregated';
}
file_put_contents($directory . DIRECTORY_SEPARATOR . $oldDay . '.json', '{}');
dost_telemetry_prune($directory, '2025-12-04');
if (file_exists($directory . DIRECTORY_SEPARATOR . $oldDay . '.json') || !file_exists($directory . DIRECTORY_SEPARATOR . $today . '.json')) {
    $failures[] = 'retention removes old aggregates and preserves recent ones';
}

foreach (scandir($directory) as $name) {
    if (preg_match('/\A\d{4}-\d{2}-\d{2}\.json\z/', $name) === 1) {
        unlink($directory . DIRECTORY_SEPARATOR . $name);
    }
}
rmdir($directory);

if (count($failures) > 0) {
    fwrite(STDERR, "FAIL: " . implode('; ', $failures) . "\n");
    exit(1);
}

fwrite(STDOUT, "PASS: anonymous telemetry aggregates allowlisted events and prunes old daily counts.\n");
