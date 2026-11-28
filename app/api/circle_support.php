<?php

require_once __DIR__ . '/people_call_support.php';

function dost_circle_handle_is_valid($handle)
{
    return is_string($handle) && preg_match('/\A[a-z][a-z0-9_]{2,19}\z/', $handle) === 1;
}

function dost_circle_invite_token_is_valid($token)
{
    return is_string($token) && preg_match('/\A[a-f0-9]{64}\z/', $token) === 1;
}

function dost_circle_invite_url($token)
{
    return 'https://bisque-bear-900175.hostingersite.com/api/circle_invite.php?t=' . rawurlencode($token);
}

function dost_circle_invite_deep_link($token)
{
    return 'dost://circle/invite/' . $token;
}

function dost_circle_normalize_handle($value)
{
    if (!is_string($value)) {
        return '';
    }
    $handle = strtolower(trim($value));
    if (strpos($handle, '@') === 0) {
        $handle = substr($handle, 1);
    }
    return $handle;
}

function dost_circle_profile_allows_lookup($profile)
{
    if (!is_array($profile) || !isset($profile['uid'], $profile['handleLookupEnabled'])) {
        return false;
    }
    if ($profile['handleLookupEnabled'] !== true) {
        return false;
    }
    $cohort = isset($profile['ageCohort']) && is_string($profile['ageCohort'])
        ? $profile['ageCohort']
        : '';
    return in_array($cohort, array('teen', 'adult'), true);
}
