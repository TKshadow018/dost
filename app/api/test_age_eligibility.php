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
dost_age_test_assert(
    !dost_age_test_mode_enabled(false, false)
    && !dost_age_test_mode_enabled('TRUE', false)
    && !dost_age_test_mode_enabled('1', true),
    'Test mode must remain off unless its configured value is exactly true.'
);
dost_age_test_assert(
    dost_age_test_mode_enabled('true', false)
    && dost_age_test_mode_enabled(false, true),
    'Test mode must accept the exact environment or server-config true value.'
);
dost_age_test_assert(
    dost_age_test_uids('', array()) === array()
    && !dost_age_test_uid_is_allowlisted($uid, dost_age_test_uids('', array())),
    'An empty test UID list must not allow anyone.'
);
dost_age_test_assert(
    dost_age_test_uid_is_allowlisted($uid, dost_age_test_uids(false, array($uid)))
    && !dost_age_test_uid_is_allowlisted($uid, dost_age_test_uids(false, array($uid . '-other'))),
    'Allowlist matching must use exact Firebase UIDs.'
);
dost_age_test_assert(
    dost_age_test_uid_is_allowlisted($uid, dost_age_test_uids($uid . ', another-user', array()))
    && !dost_age_test_uid_is_allowlisted($uid . '-other', array($uid)),
    'Environment allowlists must parse comma-separated UIDs without partial matches.'
);
dost_age_test_assert(
    dost_age_authenticated_uid_matches($uid, $uid)
    && !dost_age_authenticated_uid_matches($uid, 'different-user'),
    'A request UID must match the authenticated Firebase UID.'
);
dost_age_test_assert(
    dost_age_from_birth_month_year(10, 2013, 11, 2026) === 13
    && dost_age_from_birth_month_year(11, 2013, 11, 2026) === 12
    && dost_age_from_birth_month_year(13, 2013, 11, 2026) === false,
    'Birth month and year must derive completed age conservatively and reject invalid months.'
);
$teenClaims = dost_age_cohort_claims(array(), 10, 2009, 11, 2026);
dost_age_test_assert(
    is_array($teenClaims)
    && $teenClaims['dostAgeCohort'] === 'teen'
    && $teenClaims['dostBirthMonth'] === 10
    && $teenClaims['dostBirthYear'] === 2009,
    'The trusted cohort helper must attach a teen cohort and immutable month/year claims.'
);
dost_age_test_assert(
    dost_age_cohort_claims(array(), 11, 2013, 11, 2026) === false
    && dost_age_cohort_claims($teenClaims, 10, 2008, 11, 2026) === false,
    'Under-13 cohort assignment and client changes to a stored birth date must fail.'
);
$turnedAdultClaims = dost_age_cohort_claims($teenClaims, 10, 2009, 11, 2027);
dost_age_test_assert(
    is_array($turnedAdultClaims) && $turnedAdultClaims['dostAgeCohort'] === 'adult',
    'A teen cohort may move to adult only when its immutable stored birth month/year reaches 18.'
);
$adultClaims = dost_age_cohort_claims(array(), 10, 2008, 11, 2026);
dost_age_test_assert(
    is_array($adultClaims) && $adultClaims['dostAgeCohort'] === 'adult'
    && dost_age_cohort_claims($adultClaims, 10, 2009, 11, 2026) === false,
    'Adult cohort assignment must reject a later attempt to move the stored birth date backwards.'
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

$unrecognizedApp = $integrityPayload;
$unrecognizedApp['tokenPayloadExternal']['appIntegrity']['appRecognitionVerdict'] = 'UNRECOGNIZED_VERSION';
$unlicensedApp = $integrityPayload;
$unlicensedApp['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'] = 'UNLICENSED';
$testVerdictPayload = $unrecognizedApp;
$testVerdictPayload['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'] = 'UNLICENSED';
$testerAllowed = dost_age_test_mode_enabled('true', false)
    && dost_age_test_uid_is_allowlisted($uid, array($uid));
dost_age_test_assert(
    !dost_age_integrity_allows($testVerdictPayload, 'different-user', true, $nonce, $now, $testerAllowed),
    'A mismatched authenticated UID must not use the tester path.'
);
dost_age_test_assert(
    !dost_age_integrity_allows($unrecognizedApp, $uid, true, $nonce, $now)
    && !dost_age_integrity_allows($unlicensedApp, $uid, true, $nonce, $now),
    'UNRECOGNIZED_VERSION and UNLICENSED must fail when test mode is off.'
);
dost_age_test_assert(
    !dost_age_integrity_allows(
        $unrecognizedApp,
        $uid,
        true,
        $nonce,
        $now,
        dost_age_test_mode_enabled('true', false)
            && dost_age_test_uid_is_allowlisted($uid, array())
    )
    && !dost_age_integrity_allows(
        $unlicensedApp,
        $uid,
        true,
        $nonce,
        $now,
        dost_age_test_mode_enabled('true', false)
            && dost_age_test_uid_is_allowlisted($uid, array())
    ),
    'An empty allowlist must not relax app recognition or licensing.'
);
dost_age_test_assert(
    dost_age_integrity_allows($unrecognizedApp, $uid, true, $nonce, $now, $testerAllowed)
    && dost_age_integrity_allows($unlicensedApp, $uid, true, $nonce, $now, $testerAllowed),
    'Allowlisted testers may pass UNRECOGNIZED_VERSION and UNLICENSED verdicts.'
);
dost_age_test_assert(
    !dost_age_integrity_allows(
        $testVerdictPayload,
        $uid,
        true,
        $nonce,
        $now,
        dost_age_test_mode_enabled('true', false)
            && dost_age_test_uid_is_allowlisted($uid, array('different-user'))
    ),
    'A UID outside the allowlist must still fail with test mode enabled.'
);

$integrityFailure = '';
$unknownAppVerdict = $testVerdictPayload;
$unknownAppVerdict['tokenPayloadExternal']['appIntegrity']['appRecognitionVerdict'] = 'UNEVALUATED';
$unknownLicensingVerdict = $integrityPayload;
$unknownLicensingVerdict['tokenPayloadExternal']['accountDetails']['appLicensingVerdict'] = 'UNEVALUATED';
dost_age_test_assert(
    !dost_age_integrity_allows($unknownAppVerdict, $uid, true, $nonce, $now, $testerAllowed, $integrityFailure)
    && $integrityFailure === 'app_recognition',
    'Test mode must relax only UNRECOGNIZED_VERSION, not other app verdicts.'
);
dost_age_test_assert(
    !dost_age_integrity_allows(
        $unknownLicensingVerdict,
        $uid,
        true,
        $nonce,
        $now,
        $testerAllowed,
        $integrityFailure
    ) && $integrityFailure === 'licensing',
    'Test mode must relax only UNLICENSED, not other licensing verdicts.'
);
dost_age_test_assert(
    dost_age_integrity_reason_code('app_recognition', $testVerdictPayload) === 'integrity_unrecognized_app'
    && dost_age_integrity_reason_code('licensing', $testVerdictPayload) === 'integrity_unlicensed_app'
    && dost_age_integrity_reason_code('device', $integrityPayload) === 'integrity_device',
    'Tester reason codes must be stable, non-sensitive, and tied to the failed check.'
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
dost_age_test_assert(
    !dost_age_integrity_allows($wrongPackage, $uid, true, $nonce, $now, true, $integrityFailure)
    && $integrityFailure === 'package',
    'The package check must remain mandatory in test mode and identify its failure field.'
);

$wrongHash = $testVerdictPayload;
$wrongHash['tokenPayloadExternal']['requestDetails']['requestHash'] = 'not-the-request-hash';
dost_age_test_assert(
    !dost_age_integrity_allows($wrongHash, $uid, true, $nonce, $now, true, $integrityFailure)
    && $integrityFailure === 'hash',
    'The request hash check must remain mandatory in test mode and identify its failure field.'
);

$expiredToken = $integrityPayload;
$expiredToken['tokenPayloadExternal']['requestDetails']['timestampMillis'] = (string) (($now - 301) * 1000);
dost_age_test_assert(
    !dost_age_integrity_allows($expiredToken, $uid, true, $nonce, $now),
    'Integrity token outside the accepted five-minute window must fail.'
);
dost_age_test_assert(
    !dost_age_integrity_allows($expiredToken, $uid, true, $nonce, $now, true, $integrityFailure)
    && $integrityFailure === 'timestamp',
    'The fresh timestamp check must remain mandatory in test mode and identify its failure field.'
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
dost_age_test_assert(
    !dost_age_integrity_allows($unrecognizedDevice, $uid, true, $nonce, $now, true, $integrityFailure)
    && $integrityFailure === 'device',
    'Device integrity must remain mandatory in test mode and identify its failure field.'
);

$attributes = json_decode(dost_age_custom_attributes(true, $now, 21600), true);
dost_age_test_assert(
    is_array($attributes)
    && $attributes['dostAgeEligible'] === true
    && $attributes['dostAgeEligibleUntil'] === $now + 21600
    && $attributes['dostAgeCohort'] === 'adult',
    'Verified adult claims must carry the cohort, eligibility result, and bounded expiry.'
);
$ineligibleAttributes = json_decode(dost_age_custom_attributes(false, $now, 21600), true);
dost_age_test_assert(
    is_array($ineligibleAttributes) && !isset($ineligibleAttributes['dostAgeCohort']),
    'A failed adult-eligibility check must not issue an adult cohort.'
);
dost_age_test_assert(
    dost_age_custom_attributes(true, $now, 90000) === '',
    'Custom-claim lifetime must remain bounded to one day.'
);

fwrite(STDOUT, "PASS: Age-eligibility attestation checks passed.\n");
