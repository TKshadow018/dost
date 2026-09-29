<?php

header('Content-Type: application/json; charset=utf-8');

function respond($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
    exit;
}

function valid_uuid($value)
{
    return is_string($value) && preg_match('/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/i', $value) === 1;
}

function limit_to_single_question($text)
{
    $marks = array('?', '？', '؟');
    $firstPosition = false;
    $firstMark = '';
    foreach ($marks as $mark) {
        $position = strpos($text, $mark);
        if ($position !== false && ($firstPosition === false || $position < $firstPosition)) {
            $firstPosition = $position;
            $firstMark = $mark;
        }
    }
    if ($firstPosition === false) {
        return $text;
    }
    $secondPosition = false;
    $start = $firstPosition + strlen($firstMark);
    foreach ($marks as $mark) {
        $position = strpos($text, $mark, $start);
        if ($position !== false && ($secondPosition === false || $position < $secondPosition)) {
            $secondPosition = $position;
        }
    }
    return $secondPosition === false ? $text : rtrim(substr($text, 0, $start));
}

function safe_log_path_segment($value, $fallback)
{
    if (!is_string($value)) {
        return $fallback;
    }
    $segment = preg_replace('/[^\p{L}\p{M}\p{N}._-]+/u', '_', trim($value));
    if (!is_string($segment)) {
        $segment = preg_replace('/[^A-Za-z0-9._-]+/', '_', trim($value));
    }
    $segment = preg_replace('/^(.{1,64}).*$/us', '$1', $segment);
    $segment = trim($segment, " ._-");
    return $segment === '' ? $fallback : $segment;
}

function conversation_storage_directory($userName, $clientIp, $friendName, $sessionId)
{
    $configured = getenv('DOST_CONVERSATION_STORAGE_DIR');
    $baseDirectory = is_string($configured) && trim($configured) !== ''
        ? rtrim($configured, DIRECTORY_SEPARATOR)
        : dirname(__DIR__) . DIRECTORY_SEPARATOR . 'log';
    $userSegment = safe_log_path_segment($userName, 'unknown-user');
    $ipSegment = safe_log_path_segment(str_replace(':', '-', $clientIp), 'unknown-ip');
    $friendSegment = safe_log_path_segment($friendName, 'unknown-friend');
    $directory = $baseDirectory . DIRECTORY_SEPARATOR . $userSegment . '_' . $ipSegment
        . DIRECTORY_SEPARATOR . $friendSegment . DIRECTORY_SEPARATOR . strtolower($sessionId);
    if (!is_dir($directory) && !@mkdir($directory, 0700, true) && !is_dir($directory)) {
        return false;
    }
    @chmod($directory, 0700);
    return $directory;
}

function save_conversation_image($directory, $messageId, $imageData, $imageMime)
{
    if (!valid_uuid($messageId) || !is_string($imageData) || strlen($imageData) > 500000
        || !in_array($imageMime, array('image/jpeg', 'image/png', 'image/webp'), true)
    ) {
        return false;
    }
    $bytes = base64_decode($imageData, true);
    if (!is_string($bytes) || strlen($bytes) > 375000) {
        return false;
    }
    $detectedMime = detect_image_mime($bytes);
    if ($detectedMime !== $imageMime) {
        return false;
    }
    $extension = $imageMime === 'image/jpeg' ? 'jpg' : substr($imageMime, 6);
    $filename = strtolower($messageId) . '.' . $extension;
    $path = $directory . DIRECTORY_SEPARATOR . $filename;
    if (@file_put_contents($path, $bytes, LOCK_EX) !== strlen($bytes)) {
        return false;
    }
    @chmod($path, 0600);
    return $filename;
}

function detect_image_mime($bytes)
{
    if (class_exists('finfo')) {
        return (new finfo(FILEINFO_MIME_TYPE))->buffer($bytes);
    }
    if (strncmp($bytes, "\x89PNG\r\n\x1a\n", 8) === 0) {
        return 'image/png';
    }
    if (strncmp($bytes, "\xff\xd8\xff", 3) === 0) {
        return 'image/jpeg';
    }
    if (strlen($bytes) >= 12 && substr($bytes, 0, 4) === 'RIFF' && substr($bytes, 8, 4) === 'WEBP') {
        return 'image/webp';
    }
    return false;
}

function append_conversation_events($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $events)
{
    if (!valid_uuid($installationId) || !valid_uuid($friendId) || !valid_uuid($sessionId) || !is_array($events)) {
        return false;
    }
    foreach ($events as $event) {
        if (!is_array($event) || !valid_uuid(isset($event['message_id']) ? $event['message_id'] : null)
            || !isset($event['role'], $event['content'], $event['timestamp_ms'])
            || !in_array($event['role'], array('user', 'assistant'), true)
            || !is_string($event['content']) || strlen($event['content']) > 25000
            || !is_numeric($event['timestamp_ms'])
            || (isset($event['model']) && (!is_string($event['model']) || strlen($event['model']) > 160))
            || (isset($event['image_data']) && (!isset($event['image_mime']) || !is_string($event['image_mime'])))
        ) {
            return false;
        }
    }

    $directory = conversation_storage_directory($userName, $clientIp, $friendName, $sessionId);
    if ($directory === false) {
        return false;
    }
    $sessionLock = @fopen($directory . DIRECTORY_SEPARATOR . '.archive.lock', 'c+');
    if ($sessionLock === false || !flock($sessionLock, LOCK_EX)) {
        if (is_resource($sessionLock)) {
            fclose($sessionLock);
        }
        return false;
    }
    @chmod($directory . DIRECTORY_SEPARATOR . '.archive.lock', 0600);

    $existingFiles = glob($directory . DIRECTORY_SEPARATOR . '*.jsonl');
    if (is_array($existingFiles) && count($existingFiles) > 0) {
        sort($existingFiles, SORT_STRING);
        $path = $existingFiles[0];
    } else {
        $firstTimestamp = null;
        foreach ($events as $event) {
            $timestamp = (int) $event['timestamp_ms'];
            if ($timestamp > 0 && ($firstTimestamp === null || $timestamp < $firstTimestamp)) {
                $firstTimestamp = $timestamp;
            }
        }
        $timestampSeconds = $firstTimestamp === null ? time() : (int) floor($firstTimestamp / 1000);
        $filename = gmdate('Y-m-d_H-i-s', $timestampSeconds) . '.jsonl';
        $path = $directory . DIRECTORY_SEPARATOR . $filename;
    }
    $handle = @fopen($path, 'c+');
    if ($handle === false || !flock($handle, LOCK_EX)) {
        flock($sessionLock, LOCK_UN);
        fclose($sessionLock);
        if (is_resource($handle)) {
            fclose($handle);
        }
        return false;
    }
    flock($sessionLock, LOCK_UN);
    fclose($sessionLock);

    $knownIds = array();
    rewind($handle);
    while (($line = fgets($handle)) !== false) {
        $saved = json_decode($line, true);
        if (is_array($saved) && isset($saved['message_id'])) {
            $knownIds[$saved['message_id']] = true;
        }
    }

    $newLines = array();
    foreach ($events as $event) {
        if (isset($knownIds[$event['message_id']])) {
            continue;
        }
        $record = array(
            'message_id' => $event['message_id'],
            'role' => $event['role'],
            'content' => $event['content'],
            'timestamp_ms' => (int) $event['timestamp_ms'],
            'saved_at' => gmdate(DATE_ATOM)
        );
        if (isset($event['model']) && is_string($event['model']) && $event['model'] !== '') {
            $record['model'] = $event['model'];
        }
        if (isset($event['image_data'])) {
            $imageMime = isset($event['image_mime']) ? $event['image_mime'] : '';
            $imageFilename = save_conversation_image($directory, $event['message_id'], $event['image_data'], $imageMime);
            if ($imageFilename === false) {
                flock($handle, LOCK_UN);
                fclose($handle);
                return false;
            }
            $record['image_file'] = $imageFilename;
        }
        $encoded = json_encode($record, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);
        if (!is_string($encoded)) {
            flock($handle, LOCK_UN);
            fclose($handle);
            return false;
        }
        $newLines[] = $encoded . "\n";
        $knownIds[$event['message_id']] = true;
    }
    if (count($newLines) === 0) {
        flock($handle, LOCK_UN);
        fclose($handle);
        return true;
    }
    fseek($handle, 0, SEEK_END);
    $savedSuccessfully = true;
    foreach ($newLines as $line) {
        if (fwrite($handle, $line) !== strlen($line)) {
            $savedSuccessfully = false;
            break;
        }
    }
    $savedSuccessfully = fflush($handle) && $savedSuccessfully;
    if (function_exists('fsync')) {
        $savedSuccessfully = fsync($handle) && $savedSuccessfully;
    }
    @chmod($path, 0600);
    flock($handle, LOCK_UN);
    fclose($handle);
    return $savedSuccessfully;
}

function append_conversation_event($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $event)
{
    return append_conversation_events($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, array($event));
}

function enforce_rate_limit($bucket, $maximum)
{
    $rateLimitDirectory = rtrim(sys_get_temp_dir(), DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR
        . 'dost-ai-rate-limits-' . substr(hash('sha256', __DIR__), 0, 16);
    if (!is_dir($rateLimitDirectory) && !@mkdir($rateLimitDirectory, 0700, true) && !is_dir($rateLimitDirectory)) {
        respond(503, array('error' => 'AI service is temporarily unavailable'));
    }
    $remoteAddress = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : 'unknown';
    $rateFile = $rateLimitDirectory . DIRECTORY_SEPARATOR . hash('sha256', $remoteAddress . "\0" . $bucket) . '.json';
    $rateHandle = @fopen($rateFile, 'c+');
    if ($rateHandle === false || !flock($rateHandle, LOCK_EX)) {
        respond(503, array('error' => 'AI service is temporarily unavailable'));
    }
    $rateData = json_decode(stream_get_contents($rateHandle), true);
    $window = (int) floor(time() / 60);
    if (!is_array($rateData) || !isset($rateData['window']) || $rateData['window'] !== $window) {
        $rateData = array('window' => $window, 'count' => 0);
    }
    $rateData['count']++;
    ftruncate($rateHandle, 0);
    rewind($rateHandle);
    fwrite($rateHandle, json_encode($rateData));
    flock($rateHandle, LOCK_UN);
    fclose($rateHandle);
    if ($rateData['count'] > $maximum) {
        header('Retry-After: 60');
        respond(429, array('error' => 'Too many requests; try again shortly'));
    }
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, array('error' => 'POST requests only'));
}

$contentLength = isset($_SERVER['CONTENT_LENGTH']) ? (int) $_SERVER['CONTENT_LENGTH'] : 0;
if ($contentLength > 1572864) {
    respond(413, array('error' => 'Request is too large'));
}

$body = file_get_contents('php://input');
if ($body === false || $body === '' || strlen($body) > 1572864) {
    respond(400, array('error' => 'A valid JSON request body is required'));
}

$request = json_decode($body, true);
if (!is_array($request) || json_last_error() !== JSON_ERROR_NONE) {
    respond(400, array('error' => 'Malformed JSON request'));
}

$operation = isset($request['operation']) && is_string($request['operation']) ? $request['operation'] : 'completion';
enforce_rate_limit($operation === 'log_message' ? 'logs' : 'ai', $operation === 'log_message' ? 120 : 30);
if ($operation === 'log_message') {
    $installationId = isset($_SERVER['HTTP_X_DOST_INSTALL_ID']) ? $_SERVER['HTTP_X_DOST_INSTALL_ID'] : '';
    $userName = isset($request['user_name']) && is_string($request['user_name']) ? $request['user_name'] : '';
    $friendName = isset($request['friend_name']) && is_string($request['friend_name']) ? $request['friend_name'] : '';
    $clientIp = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '';
    $friendId = isset($request['friend_id']) ? $request['friend_id'] : '';
    $sessionId = isset($request['session_id']) ? $request['session_id'] : '';
    $event = array(
        'message_id' => isset($request['message_id']) ? $request['message_id'] : null,
        'role' => isset($request['role']) ? $request['role'] : null,
        'content' => isset($request['content']) ? $request['content'] : null,
        'timestamp_ms' => isset($request['timestamp_ms']) ? $request['timestamp_ms'] : null,
        'model' => isset($request['model']) ? $request['model'] : ''
    );
    if (isset($request['image_data'])) {
        $event['image_data'] = $request['image_data'];
        $event['image_mime'] = isset($request['image_mime']) ? $request['image_mime'] : '';
    }
    if (!valid_uuid($installationId) || !valid_uuid($friendId) || !valid_uuid($sessionId)
        || !valid_uuid($event['message_id']) || !in_array($event['role'], array('user', 'assistant'), true)
        || !is_string($event['content']) || strlen($event['content']) > 25000 || !is_numeric($event['timestamp_ms'])
        || !is_string($event['model']) || strlen($event['model']) > 160
    ) {
        respond(400, array('error' => 'Invalid conversation log event'));
    }
    if (!append_conversation_event($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $event)) {
        respond(503, array('error' => 'Could not persist conversation message'));
    }
    respond(200, array('saved' => true));
}

$config = array();
$configPath = __DIR__ . '/config.php';
if (is_file($configPath)) {
    $loadedConfig = require $configPath;
    if (is_array($loadedConfig)) {
        $config = $loadedConfig;
    }
}
$clientApiKey = isset($_SERVER['HTTP_X_OPENROUTER_KEY']) && is_string($_SERVER['HTTP_X_OPENROUTER_KEY'])
    ? trim($_SERVER['HTTP_X_OPENROUTER_KEY'])
    : '';
if (strlen($clientApiKey) > 512 || preg_match('/[\r\n]/', $clientApiKey)) {
    respond(400, array('error' => 'Invalid OpenRouter API key header'));
}
$serverApiKey = getenv('OPENROUTER_API_KEY');
if (!is_string($serverApiKey) || trim($serverApiKey) === '') {
    $serverApiKey = isset($config['openrouter_api_key']) ? $config['openrouter_api_key'] : '';
}
$usesClientKey = $clientApiKey !== '';
$apiKey = $usesClientKey ? $clientApiKey : $serverApiKey;
if (!is_string($apiKey) || trim($apiKey) === '') {
    respond(503, array('error' => 'AI service is not configured on the server'));
}

$allowedModels = array(
    'openrouter/free',
    'google/gemma-4-31b-it:free',
    'google/gemma-4-26b-a4b-it:free',
    'nvidia/nemotron-3-ultra-550b-a55b:free',
    'qwen/qwen3.8-27b:free',
    'poolside/laguna-s-2.1:free',
    'inclusionai/ling-3.0-flash-sante:free'
);
$model = isset($request['model']) && is_string($request['model']) ? $request['model'] : '';
$messages = isset($request['messages']) && is_array($request['messages']) ? $request['messages'] : array();
$validCustomModel = is_string($model)
    && preg_match('/\A[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+(?::[A-Za-z0-9._-]+)?\z/', $model) === 1;
if (!$validCustomModel || (!$usesClientKey && !in_array($model, $allowedModels, true))) {
    respond(400, array('error' => 'Requested model is not allowed'));
}
if (count($messages) < 1 || count($messages) > 100) {
    respond(400, array('error' => 'The request must contain between 1 and 100 messages'));
}
foreach ($messages as $message) {
    if (!is_array($message)
        || !isset($message['role'], $message['content'])
        || !in_array($message['role'], array('system', 'user', 'assistant'), true)
        || !is_string($message['content'])
        || strlen($message['content']) > 25000
        || (isset($message['image_data']) && (!is_string($message['image_data']) || strlen($message['image_data']) > 500000))
        || (isset($message['model']) && (!is_string($message['model']) || strlen($message['model']) > 160))
    ) {
        respond(400, array('error' => 'A message is invalid or too large'));
    }
}

$chatLogContext = null;
if ($operation === 'chat_reply') {
    $installationId = isset($_SERVER['HTTP_X_DOST_INSTALL_ID']) ? $_SERVER['HTTP_X_DOST_INSTALL_ID'] : '';
    $friendId = isset($_SERVER['HTTP_X_DOST_FRIEND_ID']) ? $_SERVER['HTTP_X_DOST_FRIEND_ID'] : '';
    $sessionId = isset($_SERVER['HTTP_X_DOST_SESSION_ID']) ? $_SERVER['HTTP_X_DOST_SESSION_ID'] : '';
    $userName = isset($request['user_name']) && is_string($request['user_name']) ? $request['user_name'] : '';
    $friendName = isset($request['friend_name']) && is_string($request['friend_name']) ? $request['friend_name'] : '';
    $clientIp = isset($_SERVER['REMOTE_ADDR']) && is_string($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '';
    if (!valid_uuid($installationId) || !valid_uuid($friendId) || !valid_uuid($sessionId)) {
        respond(400, array('error' => 'Conversation identifiers are missing or invalid'));
    }
    $chatLogContext = array($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId);
    $chatEvents = array();
    foreach ($messages as $message) {
        if (!in_array($message['role'], array('user', 'assistant'), true)) {
            continue;
        }
        $chatEvent = array(
            'message_id' => isset($message['message_id']) ? $message['message_id'] : null,
            'role' => $message['role'],
            'content' => $message['content'],
            'timestamp_ms' => isset($message['timestamp_ms']) ? $message['timestamp_ms'] : null
        );
        if (isset($message['model']) && is_string($message['model']) && $message['model'] !== '') {
            $chatEvent['model'] = $message['model'];
        }
        if (isset($message['image_data'])) {
            $chatEvent['image_data'] = $message['image_data'];
            $chatEvent['image_mime'] = isset($message['image_mime']) ? $message['image_mime'] : '';
        }
        $chatEvents[] = $chatEvent;
    }
    if (!append_conversation_events($installationId, $userName, $clientIp, $friendName, $friendId, $sessionId, $chatEvents)) {
        respond(503, array('error' => 'Could not persist conversation history'));
    }
}

$temperature = isset($request['temperature']) && is_numeric($request['temperature'])
    ? max(0, min(2, (float) $request['temperature']))
    : 0.7;
$providerMessages = array();
foreach ($messages as $message) {
    $providerMessage = array('role' => $message['role']);
    if (isset($message['image_data'])) {
        $mime = isset($message['image_mime']) ? $message['image_mime'] : '';
        if (!in_array($mime, array('image/jpeg', 'image/png', 'image/webp'), true)
            || !is_string($message['image_data']) || strlen($message['image_data']) > 500000
        ) {
            respond(400, array('error' => 'Invalid image attachment'));
        }
        $imageBytes = base64_decode($message['image_data'], true);
        if (!is_string($imageBytes) || strlen($imageBytes) > 375000
            || detect_image_mime($imageBytes) !== $mime
        ) {
            respond(400, array('error' => 'Invalid image attachment'));
        }
        $parts = array();
        if ($message['content'] !== '') {
            $parts[] = array('type' => 'text', 'text' => $message['content']);
        }
        $parts[] = array('type' => 'image_url', 'image_url' => array('url' => 'data:' . $mime . ';base64,' . $message['image_data']));
        $providerMessage['content'] = $parts;
    } else {
        $providerMessage['content'] = $message['content'];
    }
    $providerMessages[] = $providerMessage;
}
$upstreamBody = json_encode(array(
    'model' => $model,
    'messages' => $providerMessages,
    'temperature' => $temperature
), JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE);

$curl = curl_init('https://openrouter.ai/api/v1/chat/completions');
curl_setopt_array($curl, array(
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $upstreamBody,
    CURLOPT_HTTPHEADER => array(
        'Authorization: Bearer ' . trim($apiKey),
        'Content-Type: application/json',
        'HTTP-Referer: https://bisque-bear-900175.hostingersite.com',
        'X-Title: Dost'
    ),
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 10,
    CURLOPT_TIMEOUT => 90
));
$upstreamResponse = curl_exec($curl);
$upstreamStatus = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
$curlError = curl_errno($curl);
curl_close($curl);

if ($upstreamResponse === false || $curlError !== 0) {
    respond(502, array('error' => 'Could not reach the AI provider'));
}
if ($chatLogContext !== null && $upstreamStatus >= 200 && $upstreamStatus < 300) {
    $decodedResponse = json_decode($upstreamResponse, true);
    $assistantContent = isset($decodedResponse['choices'][0]['message']['content'])
        && is_string($decodedResponse['choices'][0]['message']['content'])
        ? $decodedResponse['choices'][0]['message']['content']
        : null;
    if ($assistantContent === null) {
        respond(502, array('error' => 'AI provider returned an invalid response'));
    }
    $cleanAssistantContent = preg_replace('/\A```(?:json)?\s*|\s*```\z/i', '', trim($assistantContent));
    $visibleContent = $assistantContent;
    $decodedAssistant = json_decode($cleanAssistantContent, true);
    if (is_array($decodedAssistant) && isset($decodedAssistant['reply']) && is_string($decodedAssistant['reply'])) {
        $parts = array(trim($decodedAssistant['reply']));
        if ((!isset($decodedAssistant['follow_up_delay_minutes']) || $decodedAssistant['follow_up_delay_minutes'] === null)
            && isset($decodedAssistant['follow_up_question']) && is_string($decodedAssistant['follow_up_question'])
            && trim($decodedAssistant['follow_up_question']) !== ''
        ) {
            // Limit only the follow-up to a single question so an early rhetorical
            // question in the reply does not truncate the rest of the message.
            $parts[] = limit_to_single_question(trim($decodedAssistant['follow_up_question']));
        }
        $visibleContent = implode("\n\n", array_filter($parts, 'strlen'));
    }
    $assistantMessageId = bin2hex(random_bytes(4)) . '-' . bin2hex(random_bytes(2)) . '-4' . substr(bin2hex(random_bytes(2)), 1)
        . '-a' . substr(bin2hex(random_bytes(2)), 1) . '-' . bin2hex(random_bytes(6));
    $assistantEvent = array(
        'message_id' => $assistantMessageId,
        'role' => 'assistant',
        'content' => $visibleContent,
        'timestamp_ms' => (int) floor(microtime(true) * 1000),
        'model' => $model
    );
    if (!append_conversation_event(
        $chatLogContext[0], $chatLogContext[1], $chatLogContext[2],
        $chatLogContext[3], $chatLogContext[4], $chatLogContext[5], $assistantEvent
    )) {
        respond(503, array('error' => 'Could not persist AI response'));
    }
    header('X-Dost-Message-ID: ' . $assistantMessageId);
}
http_response_code($upstreamStatus > 0 ? $upstreamStatus : 502);
echo $upstreamResponse;