<?php

define('DOST_TELEMETRY_RETENTION_DAYS', 90);

function dost_telemetry_retention_cutoff($now = null)
{
    $currentTime = is_int($now) ? $now : time();
    return gmdate('Y-m-d', $currentTime - ((DOST_TELEMETRY_RETENTION_DAYS - 1) * 86400));
}

function dost_telemetry_allowed_events()
{
    return array(
        'friend_profile_opened',
        'memory_saved',
        'memory_forgotten',
        'failed_send_retried',
        'first_message_to_friend',
        'shared_ritual_saved',
        'proactive_messages_disabled',
        'proactive_messages_paused',
        'proactive_reply_completed'
    );
}

function dost_telemetry_is_allowed_event($event)
{
    return is_string($event) && in_array($event, dost_telemetry_allowed_events(), true);
}

function dost_telemetry_record($directory, $event, $day)
{
    if (!dost_telemetry_is_allowed_event($event) || !is_string($day) || preg_match('/\A\d{4}-\d{2}-\d{2}\z/', $day) !== 1) {
        return false;
    }
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }

    $path = $directory . DIRECTORY_SEPARATOR . $day . '.json';
    $handle = @fopen($path, 'c+');
    if ($handle === false) {
        return false;
    }

    $success = false;
    if (@flock($handle, LOCK_EX)) {
        $contents = stream_get_contents($handle);
        $counts = $contents === '' ? array() : json_decode($contents, true);
        if (is_array($counts)) {
            $current = isset($counts[$event]) && is_int($counts[$event]) ? $counts[$event] : 0;
            $counts[$event] = min($current + 1, 1000000000);
            $encoded = json_encode($counts);
            if (is_string($encoded) && ftruncate($handle, 0) && rewind($handle) && fwrite($handle, $encoded) === strlen($encoded) && fflush($handle)) {
                @chmod($path, 0600);
                $success = true;
            }
        }
        flock($handle, LOCK_UN);
    }
    fclose($handle);
    return $success;
}

function dost_telemetry_prune($directory, $cutoffDay)
{
    if (!is_dir($directory) || !is_string($cutoffDay) || preg_match('/\A\d{4}-\d{2}-\d{2}\z/', $cutoffDay) !== 1) {
        return;
    }
    foreach (scandir($directory) as $name) {
        if (preg_match('/\A(\d{4}-\d{2}-\d{2})\.json\z/', $name, $matches) === 1 && $matches[1] < $cutoffDay) {
            @unlink($directory . DIRECTORY_SEPARATOR . $name);
        }
    }
}
