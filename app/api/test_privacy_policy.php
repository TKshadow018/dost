<?php

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

/**
 * Checks privacy-policy.txt against the deletion, retention, and audience
 * facts this version of the app actually implements.
 */

$path = dirname(__FILE__) . DIRECTORY_SEPARATOR . 'privacy-policy.txt';
$text = file_get_contents($path);
if (!is_string($text) || trim($text) === '') {
    fwrite(STDERR, "FAIL: privacy-policy.txt is missing or empty.\n");
    exit(1);
}

$failures = array();

$forbidden = array(
    '/all ages/i' => 'claims the app is for all ages',
    '/including children/i' => 'claims the app is for children',
    '/intended for users of all ages/i' => 'states an all-ages audience',
    '/RELEASE DRAFT/i' => 'includes an internal publication status in the public policy',
    '/current build still requires users to be 18 or older/i' => 'claims the app still has the old adult-only age gate',
    '/owner and counsel must decide/i' => 'includes internal owner direction in the public policy',
    '/Counsel must review/i' => 'includes internal counsel direction in the public policy',
    '/The owner must (confirm|verify)/i' => 'includes an owner checklist action in the public policy',
    '/before enabling Circle|before Cipher can be enabled/i' => 'includes internal launch gating in the public policy',
    '/does not currently erase/i' => 'says friend removal keeps local history',
    '/removal does not erase/i' => 'says removal does not erase local history',
    '/does not currently erase that friend/i' => 'says friend removal keeps local chat',
    '/Google Play requires/i' => 'states a third-party store rule in the user-facing policy',
    '/not configured to exclude/i' => 'says chat photos are included in backup',
    '/does not check the value/i' => 'says registration does not validate age',
    '/meter is not turned off because of the age/i' => 'says romance is not age-gated'
);
foreach ($forbidden as $pattern => $reason) {
    if (preg_match($pattern, $text) === 1) {
        $failures[] = $reason;
    }
}

$required = array(
    '1,095' => 'states the 1,095-day server retention period',
    'Delete server archive' => 'describes the in-app server archive deletion control',
    'Settings can export your profile' => 'describes local data export and import',
    'does not include your OpenRouter API key or installation id' => 'states sensitive data is excluded from backup',
    'hashed-installation deletion marker' => 'discloses metadata retained to suppress delayed uploads',
    'chat transcript file' => 'states that friend removal deletes the local transcript',
    'learned memories' => 'states that friend removal deletes local memories',
    'session summaries' => 'states that friend removal deletes local summaries',
    '13 and older' => 'states the app minimum age',
    'Firebase sign-in token and a Play Integrity token' => 'discloses tokens sent for People eligibility',
    'up to six hours' => 'states the short eligibility-claim lifetime',
    'does not independently confirm the source of the age range' => 'does not overstate age verification',
    'Only Firebase project administrators can review reports' => 'identifies who can review social reports',
    'within 72 hours' => 'states the report review target',
    'do not automatically suspend an account or hide a profile' => 'discloses manual report handling',
    'contact local emergency services if someone is in immediate danger' => 'discloses emergency guidance',
    'You may appeal an account or profile action by emailing' => 'provides an appeal route',
    'Dost is not directed at children under 13' => 'states the minimum audience age',
    'neutral age screen' => 'describes the under-13 age screen',
    'no account is created' => 'states that under-13 signup does not create an account',
    'birth information is not sent to Dost or stored' => 'states that under-13 birth information is not retained',
    'non-identifying exclusion flag' => 'limits the local under-13 block to a non-identifying flag',
    'delete the account and associated data' => 'describes deletion if an under-13 account is discovered',
    'parent or guardian can request this' => 'provides a parent contact path',
    'Dost is an Android app for people aged 13 and older' => 'states the current minimum audience age',
    'Teens aged 13–17 may use teen-safe Explore AI' => 'states teen AI access',
    'not stranger discovery or Explore Plus' => 'states teen feature restrictions',
    'Circle is planned and is not available in this build' => 'does not imply Circle has shipped',
    'additional age or parental-consent requirements may apply' => 'discloses regional consent variation to users',
    'private by default' => 'states teen privacy defaults',
    'AI, not a real person' => 'states the teen AI disclosure',
    'Circle is planned' => 'covers the planned Circle mode',
    'QR code, invite, or handle' => 'covers Circle contact methods',
    'read receipts, and typing indicators' => 'covers Circle message status data',
    'OpenRouter and the selected model provider' => 'covers Explore third-party AI processing',
    'Shield is closed, admin-controlled organization messaging' => 'covers the Shield mode',
    'Moving data to a customer-owned server is a documented manual service' => 'covers Shield customer-hosted migration',
    'Cipher is not available' => 'does not imply Cipher is already shipped',
    'Dost will not receive the passphrase and cannot recover content' => 'covers Cipher passphrase loss and recovery',
    'participants, timestamps, message sizes, and delivery tokens' => 'covers Cipher metadata processing',
    'Explore Plus (`dost_explore_plus`, monthly or annual, plus the optional one-time `dost_explore_plus_lifetime`)' => 'covers the Explore Plus plan',
    'Cipher (`dost_cipher`, monthly or annual)' => 'covers the Cipher plan',
    'Shield Team (`dost_shield_team`, up to 10 members, monthly or annual), Shield Business (`dost_shield_business`, up to 25 members, monthly or annual)' => 'covers Shield billing tiers',
    'Dost Plus bundle' => 'covers the combined billing plan',
    'birth month and year' => 'states age data collected for age-appropriate experience',
    'while the account exists' => 'states purchase entitlement retention',
    'child-directed service' => 'states that Dost is not a child-directed service',
    'tushartk018@gmail.com' => 'keeps the archive-request contact address',
    'November 27, 2026' => 'uses the revised effective date',
    'Google Play Billing' => 'states that Google Play handles Premium payments',
    'Explore Plus, its lifetime purchase, Dost Plus, Shield plans, and the 90-day intro grant require server-verified adult eligibility' => 'states age-limited paid plans',
    'Cipher is available to teens' => 'states Cipher is available to teens',
    'invited Shield members may be 13 or older, while the owner must be an adult' => 'states Shield organization age requirements',
    'SHA-256 fingerprint of the purchase token' => 'discloses the RTDN purchase fingerprint',
    'never receives or stores your card number' => 'states that Dost does not receive payment-method details',
    'Premium entitlement claims' => 'discloses stored Premium entitlement claims',
    'Payments & subscriptions > Subscriptions' => 'explains how to cancel subscriptions',
    'Firebase Crashlytics' => 'discloses crash reporting',
    'retains crash stack traces and associated identifiers for 90 days' => 'states crash-report retention',
    'API keys are not written into conversation logs' => 'still says API keys are not logged',
    'chat_images' => 'mentions chat image files',
    'system-friend-photos' => 'says system-friend photos are excluded from backup',
    'Up to six core facts from your saved people and preference profile' => 'names learned facts sent with each turn',
    'up to the seven most recent summaries' => 'names session summaries sent with each turn',
    'friendship score and love score' => 'names relationship scores sent with each turn',
    'does not display the installation id' => 'says the app does not display the installation id',
    'installation id' => 'describes install-id lookup for newer archives',
    'Share anonymous event counts' => 'discloses optional event-only analytics',
    'daily event totals for 90 days' => 'states analytics data retention',
    'does not receive message content, profile details, account data, or installation ids' => 'states analytics excludes conversation and identity data',
    'may see your IP address' => 'discloses IP visibility to the API host',
    'It does not inspect image contents' => 'states the text classifier cannot inspect chat images',
    'without image-content moderation' => 'discloses adult image moderation is not provided',
    'Shield is a closed, admin-controlled organization messaging mode and is disabled by default' => 'discloses that Shield is not enabled by default',
    'legacy Firestore paths `secureOrgs/...`' => 'documents the legacy Shield Firestore paths',
    'member UIDs, display names, roles and status' => 'describes organization membership data',
    'Managers admit members by Firebase UID' => 'describes manager-controlled membership',
    'not end-to-end encrypted' => 'discloses that Shield is not end-to-end encrypted',
    'not used for AI, analytics, product improvement, experiments, or testing' => 'prohibits secondary use of organization data',
    'not sent to Dost\'s AI API, OpenRouter' => 'describes isolation from AI systems',
    'attach message text, member fields, UIDs, organization IDs, or invite details to Crashlytics custom keys or logs' => 'describes Crashlytics restrictions for organization data',
    'Shield Team allows up to 10 active members and Shield Business up to 25' => 'states both Shield seat caps',
    'If a downgrade leaves an organization above its seat cap' => 'describes Shield access while over a downgraded seat cap',
    'export the member list and organization messages as JSON in the app' => 'describes owner export',
    'afterward, the owner and members can still read and export data but cannot send messages' => 'describes Shield grace-period behavior',
    'owner pays for the plan; invited members join for free' => 'describes owner-paid Shield access',
    'request deletion by emailing the Contact Us address' => 'states the org deletion request path',
    'The app\'s private SQLite database files are also excluded' => 'states local cache is excluded from backup'
);
foreach ($required as $needle => $reason) {
    if (strpos($text, $needle) === false) {
        $failures[] = 'missing: ' . $reason;
    }
}

if (count($failures) > 0) {
    fwrite(STDERR, "FAIL: privacy policy does not match shipped behavior:\n");
    foreach ($failures as $failure) {
        fwrite(STDERR, " - {$failure}\n");
    }
    exit(1);
}

fwrite(STDOUT, "PASS: privacy policy matches local deletion, report handling, retention, and age controls.\n");
