<?php

require_once __DIR__ . '/circle_support.php';

$token = isset($_GET['t']) && is_string($_GET['t']) ? strtolower(trim($_GET['t'])) : '';
$accept = isset($_SERVER['HTTP_ACCEPT']) && is_string($_SERVER['HTTP_ACCEPT'])
    ? $_SERVER['HTTP_ACCEPT']
    : '';
$wantsJson = PHP_SAPI !== 'cli'
    && (strpos($accept, 'application/json') !== false || (isset($_GET['format']) && $_GET['format'] === 'json'));

if (!dost_circle_invite_token_is_valid($token)) {
    if ($wantsJson) {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code(400);
        echo json_encode(array('error' => 'This invite link is invalid.'));
        exit;
    }
    http_response_code(400);
    header('Content-Type: text/html; charset=utf-8');
    echo '<!doctype html><meta charset="utf-8"><title>Dost invite</title><p>This invite link is invalid.</p>';
    exit;
}

$deepLink = dost_circle_invite_deep_link($token);
$webUrl = dost_circle_invite_url($token);
$payload = array(
    'token' => $token,
    'deep_link' => $deepLink,
    'web_url' => $webUrl,
);

if ($wantsJson) {
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($payload);
    exit;
}

header('Content-Type: text/html; charset=utf-8');
$safeLink = htmlspecialchars($deepLink, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
echo '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Join me on Dost</title></head><body style="font-family:sans-serif;max-width:32rem;margin:2rem auto;padding:0 1rem">';
echo '<h1>Join me on Dost</h1>';
echo '<p>Dost is a messenger for people you already know. Open this invite in the Dost app to add a contact. You will not be added until you accept it on your device.</p>';
echo '<p><a href="' . $safeLink . '">Open in Dost</a></p>';
echo '<p>If the app does not open, install Dost and then open this page again.</p>';
echo '</body></html>';
