<?php

require_once __DIR__ . '/people_call_support.php';

header('Content-Type: application/json; charset=utf-8');

if (PHP_SAPI === 'cli' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    dost_call_respond(404, array('error' => 'Not found'));
}
$request = dost_call_read_request();
if ($request === false) {
    dost_call_respond(400, array('error' => 'Invalid request'));
}
$idToken = dost_call_bearer_token();
if ($idToken === '') {
    dost_call_respond(401, array('error' => 'Sign-in required'));
}
$settings = dost_call_config();
$turnState = dost_call_cloudflare_turn_settings_state($settings);
if ($settings === false || $turnState === 'incomplete'
    || ($turnState !== 'configured' && !dost_call_coturn_configured($settings))
) {
    error_log($turnState === 'incomplete'
        ? 'Dost People call Cloudflare TURN settings are incomplete.'
        : 'Dost People call TURN service is not configured.');
    dost_call_respond(503, array('error' => 'People calling is not configured'));
}
$authorization = dost_call_authorize(
    $settings,
    $idToken,
    $request['chat_id'],
    $request['call_id'],
    false,
    'ice_servers',
    20
);
if ($authorization['status'] !== 200) {
    dost_call_respond($authorization['status'], array('error' => $authorization['message']));
}
$issued = dost_call_issue_ice_servers($settings, $authorization['uid'], time());
if ($issued === false) {
    error_log($turnState === 'configured'
        ? 'Dost People call Cloudflare TURN credential request failed.'
        : 'Dost People call TURN configuration is invalid.');
    dost_call_respond(503, array('error' => 'People calling is unavailable'));
}
dost_call_respond(200, $issued);
