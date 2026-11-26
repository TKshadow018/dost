<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/age_eligibility_support.php';

function dost_age_test_assert(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$uid = 'firebase-user-123';
$nonce = str_repeat('a', 64);
$now = 1770000000;
$expectedHash = rtrim(strtr(base64_encode(hash(
    'sha256',
    "dost-age-v1\n{$uid}\n1\n{$nonce}",
    true
)), '+/', '-_'), '=');

dost_age_test_assert(
    dost_age_request_hash($uid, true, $nonce) === $expectedHash,
    'Request hash must bind the Firebase UID, eligibility, and nonce.'
);
dost_age_test_assert(
    dost_age_request_hash($uid, true, 'not-a-nonce') === '',
    'Malformed request nonce must be rejected.'
);

$integrityPayload = array(
    'tokenPayloadExternal' => array(
        'requestDetails' => array(
            'requestPackageName' => 'com.snigtus.dost',
            'requestHash' => $expectedHash,
            'timestampMillis' => (string) ($now * 1000)
        ),
        'appIntegrity' => array('appRecognitionVerdict' => 'PLAY_RECOGNIZED'),
        'accountDetails' => array('appLicensingVerdict' => 'LICENSED'),
        'deviceIntegrity' => array('deviceRecognitionVerdict' => array('MEETS_DEVICE_INTEGRITY'))
    )
);

dost_age_test_assert(
    dost_age_integrity_allows($integrityPayload, $uid, true, $nonce, $now),
    'A fresh, licensed, recognized app/device with the matching hash should pass.'
);
dost_age_test_assert(
    !dost_age_integrity_allows($integrityPayload, 'different-user', true, $nonce, $now),
    'Integrity token must be bound to the authenticated user.'
);
dost_age_test_assert(
    !dost_age_integrity_allows($integrityPayload, $uid, false, $nonce, $now),
    'Integrity token must be bound to the requested eligibility value.'
);
dost_age_test_assert(
    !dost_age_integrity_allows($integrityPayload, $uid, true, str_repeat('b', 64), $now),
    'Integrity token must be bound to the request nonce.'
);

$wrongPackage = $integrityPayload;
$wrongPackage['tokenPayloadExternal']['requestDetails']['requestPackageName'] = 'com.example.other';
dost_age_test_assert(
    !dost_age_integrity_allows($wrongPackage, $uid, true, $nonce, $now),
    'Unrecognized package must fail verification.'
);

$expiredToken = $integrityPayload;
$expiredToken['tokenPayloadExternal']['requestDetails']['timestampMillis'] = (string) (($now - 301) * 1000);
dost_age_test_assert(
    !dost_age_integrity_allows($expiredToken, $uid, true, $nonce, $now),
    'Integrity token outside the accepted five-minute window must fail.'
);

$unlicensed = $integrityPayload;
$unlicensed['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'] = 'UNLICENSED';
dost_age_test_assert(
    !dost_age_integrity_allows($unlicensed, $uid, true, $nonce, $now),
    'Unlicensed app must fail verification.'
);

$unrecognizedDevice = $integrityPayload;
$unrecognizedDevice['tokenPayloadExternal']['deviceIntegrity']['deviceRecognitionVerdict'] = array();
dost_age_test_assert(
    !dost_age_integrity_allows($unrecognizedDevice, $uid, true, $nonce, $now),
    'Device without the required integrity verdict must fail verification.'
);

$attributes = json_decode(dost_age_custom_attributes(true, $now, 21600), true);
dost_age_test_assert(
    is_array($attributes)
    && $attributes['dostAgeEligible'] === true
    && $attributes['dostAgeEligibleUntil'] === $now + 21600,
    'Custom claim must carry the eligibility result and bounded expiry.'
);
dost_age_test_assert(
    dost_age_custom_attributes(true, $now, 90000) === '',
    'Custom-claim lifetime must remain bounded to one day.'
);

fwrite(STDOUT, "PASS: Age-eligibility attestation checks passed.\n");
