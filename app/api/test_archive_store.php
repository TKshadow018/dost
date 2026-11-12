<?php

/**
 * Local checks for the private conversation archive: location, installation id,
 * retention cleanup, and the legacy web-root move.
 */

function dost_test_fail($message)
{
    fwrite(STDERR, "FAIL: {$message}\n");
    exit(1);
}

function dost_test_unset($name)
{
    putenv($name);
    unset($_ENV[$name], $_SERVER[$name]);
}

$root = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'dost-archive-' . bin2hex(random_bytes(4));
$web = $root . '/public_html';
$private = $root . '/dost-private-data/conversation-archive';
if (!mkdir($web, 0700, true)) {
    dost_test_fail('Could not create a temporary web root.');
}

dost_test_unset('DOST_CONVERSATION_STORAGE_DIR');
dost_test_unset('DOST_LEGACY_ARCHIVE_DIR');
putenv('DOST_WEB_ROOT=' . $web);
$_SERVER['DOCUMENT_ROOT'] = '';

require_once __DIR__ . '/archive_store.php';

$defaultDirectory = conversation_storage_base_directory();
if ($defaultDirectory !== $private) {
    dost_test_fail('Default archive directory was ' . var_export($defaultDirectory, true) . " expected {$private}.");
}
if (dost_path_is_inside($defaultDirectory, dost_web_root_directory())) {
    dost_test_fail('Default archive directory is inside the web root.');
}

putenv('DOST_CONVERSATION_STORAGE_DIR=' . $web . '/log');
if (conversation_storage_base_directory() !== false) {
    dost_test_fail('A storage path inside the web root was accepted.');
}

$linked = $root . '/linked-archive';
if (!mkdir($web . '/log', 0700, true) || !symlink($web . '/log', $linked)) {
    dost_test_fail('Could not create a symlink into the web root.');
}
putenv('DOST_CONVERSATION_STORAGE_DIR=' . $linked);
if (dost_prepare_storage_directory(conversation_storage_base_directory()) !== false) {
    dost_test_fail('A symlink into the web root was accepted as archive storage.');
}

putenv('DOST_CONVERSATION_STORAGE_DIR=' . $private);
$installationId = 'AAAAAAAA-BBBB-4CCC-8DDD-EEEEEEEEEEEE';
$friendId = '22222222-2222-4222-8222-222222222222';
$sessionId = '33333333-3333-4333-8333-333333333333';
$messageId = '44444444-4444-4444-8444-444444444444';
$saved = append_conversation_events(
    $installationId,
    'Ada',
    '203.0.113.5',
    'Rafi',
    $friendId,
    $sessionId,
    array(array(
        'message_id' => $messageId,
        'role' => 'user',
        'content' => 'hello archive',
        'timestamp_ms' => 1700000000000,
        'model' => 'openrouter/free'
    ))
);
if ($saved !== true) {
    dost_test_fail('Could not append a conversation event outside the web root.');
}
if (append_conversation_events(
    $installationId, 'Ada', '203.0.113.5', 'Rafi', $friendId, $sessionId,
    array(array(
        'message_id' => $messageId,
        'role' => 'user',
        'content' => 'hello archive',
        'timestamp_ms' => 1700000000000
    ))
) !== true) {
    dost_test_fail('Duplicate message append should succeed without rewriting.');
}

$jsonlFiles = glob($private . '/*/*/*/*.jsonl');
if (!is_array($jsonlFiles) || count($jsonlFiles) !== 1) {
    dost_test_fail('Expected one JSONL session file under the private archive.');
}
$record = json_decode(trim(file_get_contents($jsonlFiles[0])), true);
if (!is_array($record) || !isset($record['installation_id']) || $record['installation_id'] !== strtolower($installationId)) {
    dost_test_fail('JSONL record is missing the lowercase installation id.');
}
if (strpos(file_get_contents($jsonlFiles[0]), 'hello archive') === false) {
    dost_test_fail('JSONL record lost the message content.');
}
$lines = file($jsonlFiles[0], FILE_IGNORE_NEW_LINES);
if (!is_array($lines) || count($lines) !== 1) {
    dost_test_fail('Duplicate message id was written twice.');
}

$indexPath = $private . '/_by_install/' . strtolower($installationId) . '.txt';
$index = is_file($indexPath) ? file($indexPath, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) : false;
if (!is_array($index) || count($index) !== 1 || strpos($index[0], strtolower($sessionId)) === false) {
    dost_test_fail('Install index does not point at the session directory.');
}
if (dost_path_is_inside($private, $web)) {
    dost_test_fail('Written archive is inside the web root.');
}

define('DOST_CLEANUP_LIBRARY_ONLY', true);
require_once __DIR__ . '/cleanup_logs.php';
if (!defined('DOST_RETENTION_DAYS') || DOST_RETENTION_DAYS !== 3650) {
    dost_test_fail('Retention is no longer 3,650 days.');
}

$freshDir = $private . '/Fresh_203.0.113.9/Mina/' . $sessionId;
$expiredDir = $private . '/Expired_198.51.100.4/Mina/55555555-5555-4555-8555-555555555555';
foreach (array($freshDir, $expiredDir) as $directory) {
    if (!mkdir($directory, 0700, true)) {
        dost_test_fail('Could not create a cleanup fixture.');
    }
}
$freshFile = $freshDir . '/2026-01-01_00-00-00.jsonl';
$expiredFile = $expiredDir . '/2000-01-01_00-00-00.jsonl';
file_put_contents($freshFile, "{\"installation_id\":\"fresh\"}\n");
file_put_contents($expiredFile, "{\"installation_id\":\"expired\"}\n");
touch($freshFile, time() - 86400);
touch($expiredFile, time() - (3651 * 86400));

$legacyDir = $web . '/log/Legacy_203.0.113.8/Old/66666666-6666-4666-8666-666666666666';
if (!mkdir($legacyDir, 0700, true)) {
    dost_test_fail('Could not create a legacy web-root archive.');
}
$legacyFile = $legacyDir . '/2001-01-01_00-00-00.jsonl';
file_put_contents($legacyFile, "{\"message_id\":\"legacy\"}\n");
touch($legacyFile, time() - (3651 * 86400));
file_put_contents($web . '/log/.htaccess', "Require all denied\n");

$primaryCleanup = run_conversation_archive_cleanup($private, true);
$legacyCleanup = run_conversation_archive_cleanup($web . '/log', false);
if (empty($primaryCleanup['ok']) || empty($legacyCleanup['ok'])) {
    dost_test_fail('Cleanup reported failure.');
}
if (is_dir($expiredDir) || is_dir($legacyDir)) {
    dost_test_fail('Expired sessions were not removed from the private archive and the legacy web directory.');
}
if (!is_file($freshFile) || !is_file($jsonlFiles[0])) {
    dost_test_fail('Cleanup removed a session that is still inside the retention window.');
}
$heartbeatPath = $private . '/.cleanup-heartbeat.json';
$heartbeat = is_file($heartbeatPath) ? json_decode(file_get_contents($heartbeatPath), true) : null;
if (!is_array($heartbeat) || !isset($heartbeat['retention_days']) || $heartbeat['retention_days'] !== 3650) {
    dost_test_fail('Cleanup heartbeat does not record 3,650-day retention.');
}
if (is_file($web . '/log/.cleanup-heartbeat.json')) {
    dost_test_fail('Cleanup wrote the heartbeat into the web root.');
}

define('DOST_MIGRATE_LIBRARY_ONLY', true);
require_once __DIR__ . '/migrate_conversation_archive.php';
$remain = $web . '/log/Keep_203.0.113.7/Nila/77777777-7777-4777-8777-777777777777';
if (!mkdir($remain, 0700, true)) {
    dost_test_fail('Could not create a migration fixture.');
}
file_put_contents($remain . '/2024-01-01_00-00-00.jsonl', "{\"installation_id\":\"move-me\"}\n");
putenv('DOST_LEGACY_ARCHIVE_DIR=' . $web . '/log');
$preview = dost_migrate_legacy_archive(false);
if (empty($preview['ok']) || !in_array('Keep_203.0.113.7', isset($preview['moved']) ? $preview['moved'] : array(), true)) {
    dost_test_fail('Dry-run did not plan to move the legacy archive out of the web root.');
}
if (!is_dir($remain)) {
    dost_test_fail('Dry-run moved files.');
}
$applied = dost_migrate_legacy_archive(true);
if (empty($applied['ok']) || is_dir($remain) || !is_dir($private . '/Keep_203.0.113.7')) {
    dost_test_fail('Apply did not move the legacy archive out of the web root.');
}
if (!is_file($web . '/log/.htaccess')) {
    dost_test_fail('Migration removed the legacy .htaccess deny rule.');
}
if (dost_path_is_inside($private . '/Keep_203.0.113.7', $web)) {
    dost_test_fail('Moved archive is still inside the web root.');
}

$note = file_get_contents(__DIR__ . '/deletion-lookup.txt');
if (!is_string($note)
    || strpos($note, 'installation_id') === false
    || strpos($note, '_by_install') === false
    || strpos($note, '3,650') === false
) {
    dost_test_fail('deletion-lookup.txt does not explain install-id lookup.');
}

fwrite(STDOUT, "PASS: archives default outside the web root, JSONL stores the installation id, and 3,650-day cleanup covers the private directory.\n");
