<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

require_once __DIR__ . '/premium_support.php';

function dost_premium_test_assert(bool $condition, string $message): void
{
    if (!$condition) {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    }
}

$uid = 'firebase-user-123';
$accountId = dost_premium_obfuscated_account_id($uid);
$now = 1790000000;
$productPlans = array(
    'dost_explore_plus' => array('explore_plus', 'subscription', true),
    'dost_explore_plus_lifetime' => array('explore_plus', 'inapp', true),
    'dost_cipher' => array('cipher', 'subscription', false),
    'dost_shield_team' => array('shield_team', 'subscription', false),
    'dost_shield_business' => array('shield_business', 'subscription', false),
    'dost_plus' => array('plus_bundle', 'subscription', true)
);
foreach ($productPlans as $productId => $expected) {
    $product = dost_premium_product($productId);
    dost_premium_test_assert(
        is_array($product) && $product['plan'] === $expected[0]
            && $product['type'] === $expected[1] && $product['adult'] === $expected[2],
        'Each Play product maps to its plan, purchase type, and age requirement.'
    );
}
dost_premium_test_assert(dost_premium_product('dost_premium_monthly') === false, 'Retired Premium products are rejected.');
dost_premium_test_assert(
    dost_premium_plan_enabled('explore_plus', array('plan_explore_plus_enabled' => 'false')) === false
        && dost_premium_plan_enabled('explore_plus', array('plan_explore_plus_enabled' => 'true')) === true
        && dost_premium_plan_enabled('plus_bundle', array(
            'plan_explore_plus_enabled' => 'true',
            'plan_cipher_enabled' => 'false'
        )) === false,
    'Plan switches default off and the bundle requires both component plans.'
);
$adultClaims = array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => $now + 3600, 'dostAgeCohort' => 'adult');
$subscription = array(
    'externalAccountIdentifiers' => array('obfuscatedExternalAccountId' => $accountId),
    'subscriptionState' => 'SUBSCRIPTION_STATE_CANCELED',
    'lineItems' => array(array(
        'productId' => 'dost_explore_plus',
        'offerDetails' => array('basePlanId' => 'monthly'),
        'expiryTime' => gmdate(DATE_ATOM, $now + 3600)
    ))
);
$entitlement = dost_premium_purchase_entitlement(
    'dost_explore_plus',
    'subscription',
    $subscription,
    $uid,
    $now
);
dost_premium_test_assert(
    is_array($entitlement) && $entitlement['expires_at'] === $now + 3600,
    'A canceled subscription remains active through its verified expiry.'
);
dost_premium_test_assert(
    dost_premium_has_adult_eligibility($adultClaims, $now)
        && dost_premium_claims(array('dostAgeCohort' => 'teen'), 'dost_explore_plus', $entitlement, $now) === false
        && !dost_premium_has_adult_eligibility(array(
            'dostAgeEligible' => true,
            'dostAgeEligibleUntil' => $now + 3600,
            'dostAgeCohort' => 'teen'
        ), $now)
        && !dost_premium_has_adult_eligibility(array(
            'dostAgeEligible' => true,
            'dostAgeEligibleUntil' => $now
        ), $now),
    'Only current adult eligibility authorizes age-restricted plans.'
);
$renewedAfterEligibilityExpiry = dost_premium_claims(
    array(
        'dostAgeEligible' => true,
        'dostAgeEligibleUntil' => $now - 1,
        'dostExplorePlusUntil' => $now + 1800,
        'dostExplorePlusProductId' => 'dost_explore_plus',
        'dostExplorePlusToken' => hash('sha256', 'existing-token')
    ),
    'dost_explore_plus',
    $entitlement,
    $now,
    'existing-token',
    $now,
    false
);
dost_premium_test_assert(
    is_array($renewedAfterEligibilityExpiry)
        && $renewedAfterEligibilityExpiry['dostExplorePlusUntil'] === $now + 3600,
    'Verified RTDN renewals keep an already verified plan current without requiring a fresh age attestation.'
);
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_explore_plus', 'subscription', $subscription, 'other-user', $now) === false,
    'A purchase bound to another Firebase account must be rejected.'
);
$cipherPurchase = $subscription;
$cipherPurchase['lineItems'][0]['productId'] = 'dost_cipher';
$cipherPurchase['lineItems'][0]['offerDetails']['basePlanId'] = 'annual';
$cipherEntitlement = dost_premium_purchase_entitlement('dost_cipher', 'subscription', $cipherPurchase, $uid, $now);
$teenCipherClaims = dost_premium_claims(array(), 'dost_cipher', $cipherEntitlement, $now);
dost_premium_test_assert(
    is_array($teenCipherClaims) && $teenCipherClaims['dostCipherUntil'] === $now + 3600,
    'Cipher grants to a teen account without adult eligibility.'
);
$teamPurchase = $subscription;
$teamPurchase['lineItems'][0]['productId'] = 'dost_shield_team';
$teamEntitlement = dost_premium_purchase_entitlement('dost_shield_team', 'subscription', $teamPurchase, $uid, $now);
$teenShieldClaims = dost_premium_claims(
    array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => $now + 3600, 'dostAgeCohort' => 'teen'),
    'dost_shield_team',
    $teamEntitlement,
    $now,
    'teen-shield-token'
);
dost_premium_test_assert(
    is_array($teenShieldClaims) && $teenShieldClaims['dostShieldSeats'] === 10,
    'Shield subscription claims are not adult-gated; org creation remains separately adult-gated.'
);
$teamClaims = dost_premium_claims($adultClaims, 'dost_shield_team', $teamEntitlement, $now, 'team-token');
$businessPurchase = $subscription;
$businessPurchase['lineItems'][0]['productId'] = 'dost_shield_business';
$businessEntitlement = dost_premium_purchase_entitlement(
    'dost_shield_business',
    'subscription',
    $businessPurchase,
    $uid,
    $now
);
$businessClaims = dost_premium_claims($teamClaims, 'dost_shield_business', $businessEntitlement, $now, 'business-token');
$downgradedClaims = dost_premium_claims($businessClaims, 'dost_shield_team', $teamEntitlement, $now, 'team-token');
dost_premium_test_assert(
    is_array($teamClaims) && $teamClaims['dostShieldSeats'] === 10
        && is_array($businessClaims) && $businessClaims['dostShieldSeats'] === 25
        && is_array($downgradedClaims) && $downgradedClaims['dostShieldSeats'] === 10,
    'Shield subscription replacement updates the owner seat entitlement for upgrades and downgrades.'
);
dost_premium_test_assert(
    dost_premium_shield_plan_fields($businessClaims, $now) === array(
        'seatLimit' => 25,
        'activeUntil' => gmdate('Y-m-d\TH:i:s\Z', $now + 3600)
    ) && dost_premium_shield_plan_fields($teamClaims, $now + 3600) === false,
    'Only an active Shield claim is mirrored with its authoritative seat limit and expiry.'
);
$bundlePurchase = $subscription;
$bundlePurchase['lineItems'][0]['productId'] = 'dost_plus';
$bundleEntitlement = dost_premium_purchase_entitlement('dost_plus', 'subscription', $bundlePurchase, $uid, $now);
$bundleClaims = dost_premium_claims($adultClaims, 'dost_plus', $bundleEntitlement, $now, 'bundle-token');
dost_premium_test_assert(
    is_array($bundleClaims)
        && $bundleClaims['dostExplorePlusUntil'] === $now + 3600
        && $bundleClaims['dostCipherUntil'] === $now + 3600,
    'The Dost Plus bundle grants Explore Plus and Cipher together.'
);
$allPaidClaims = dost_premium_claims($bundleClaims, 'dost_shield_business', $businessEntitlement, $now, 'business-token');
$allPaidClaims = dost_premium_intro_claims($allPaidClaims, $now - 100, 90, $now, $now - 86400);
dost_premium_test_assert(
    is_array($allPaidClaims)
        && strlen(json_encode($allPaidClaims, JSON_UNESCAPED_SLASHES)) <= 1000,
    'Combined per-plan entitlements and intro claims stay within Firebase custom-claim limits.'
);
$currentBusiness = dost_premium_claims($adultClaims, 'dost_shield_business', $businessEntitlement, $now, 'new-token');
$staleRevocation = dost_premium_revoke_claims($currentBusiness, 'dost_shield_business', $now, 'old-token');
$refundedBusiness = dost_premium_revoke_claims($currentBusiness, 'dost_shield_business', $now, 'new-token');
dost_premium_test_assert(
    $staleRevocation === $currentBusiness
        && is_array($refundedBusiness) && $refundedBusiness['dostShieldUntil'] === $now
        && $refundedBusiness['dostShieldSeats'] === 0,
    'Refund/revoke updates clear the matching purchase but ignore a stale purchase token.'
);
$validPushClaims = array(
    'aud' => 'https://dost.example/rtdn',
    'email' => 'pubsub@dost.example',
    'email_verified' => 'true',
    'iss' => 'https://accounts.google.com',
    'expires_in' => '3599'
);
dost_premium_test_assert(
    dost_premium_rtdn_token_info_is_valid($validPushClaims, 'https://dost.example/rtdn', 'pubsub@dost.example')
        && !dost_premium_rtdn_token_info_is_valid($validPushClaims, 'https://wrong.example', 'pubsub@dost.example'),
    'RTDN accepts only a verified Google push identity with the configured audience and email.'
);
$mapDirectory = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'dost-premium-test-' . bin2hex(random_bytes(6));
dost_premium_test_assert(
    dost_premium_store_purchase_owner('opaque-play-token', $uid, 'dost_cipher', $mapDirectory)
        && dost_premium_purchase_owner('opaque-play-token', $mapDirectory)
            === array('uid' => $uid, 'product_id' => 'dost_cipher', 'refund_required' => false),
    'RTDN ownership records use token hashes and retain the Firebase UID and product.'
);
dost_premium_store_purchase_owner('teen-purchase-token', $uid, 'dost_explore_plus', $mapDirectory, true);
dost_premium_test_assert(
    dost_premium_purchase_owner('teen-purchase-token', $mapDirectory)['refund_required'] === true,
    'An age-ineligible restricted purchase is recorded for refund follow-up without granting access.'
);
foreach (glob($mapDirectory . DIRECTORY_SEPARATOR . '*') ?: array() as $mapFile) {
    @unlink($mapFile);
}
@rmdir($mapDirectory);

$expired = $subscription;
$expired['lineItems'][0]['expiryTime'] = gmdate(DATE_ATOM, $now - 1);
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_explore_plus', 'subscription', $expired, $uid, $now) === false,
    'An expired subscription must not grant Premium.'
);
$lifetime = array(
    'obfuscatedExternalAccountId' => $accountId,
    'purchaseStateContext' => array('purchaseState' => 'PURCHASED'),
    'productLineItem' => array(array('productId' => 'dost_explore_plus_lifetime'))
);
$lifetimeEntitlement = dost_premium_purchase_entitlement(
    'dost_explore_plus_lifetime',
    'inapp',
    $lifetime,
    $uid,
    $now
);
dost_premium_test_assert(
    is_array($lifetimeEntitlement) && $lifetimeEntitlement['lifetime'] === true,
    'A verified lifetime product grants a non-expiring entitlement.'
);
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_cipher', 'subscription', $subscription, $uid, $now) === false,
    'A purchase response for another product must not grant the requested plan.'
);
$invalidBasePlan = $subscription;
$invalidBasePlan['lineItems'][0]['offerDetails']['basePlanId'] = 'weekly';
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_explore_plus', 'subscription', $invalidBasePlan, $uid, $now) === false,
    'Only the configured monthly and annual base plans are accepted.'
);
$claimLock = dost_firebase_claim_lock($uid);
dost_premium_test_assert(is_resource($claimLock), 'Custom-claim updates acquire a per-user lock.');
dost_firebase_claim_unlock($claimLock);
dost_premium_test_assert(dost_firebase_claim_lock('invalid uid') === false, 'Custom-claim locks reject invalid user identifiers.');

$claims = dost_premium_claims(
    array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => $now + 21600),
    'dost_explore_plus',
    $entitlement,
    $now
);
dost_premium_test_assert(
    is_array($claims)
        && $claims['dostAgeEligible'] === true
        && $claims['dostExplorePlusUntil'] === $now + 3600
        && $claims['dostPremiumUntil'] === $now + 3600,
    'Premium claims must preserve unrelated Firebase custom claims.'
);
$introDays = 90;
$launchAt = $now - 86400;
$newAccountCreatedAt = $now - 3600;
$newAccountClaims = dost_premium_intro_claims(
    $adultClaims,
    $newAccountCreatedAt,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($newAccountClaims)
        && $newAccountClaims['dostExplorePlusUntil'] === $newAccountCreatedAt + ($introDays * 86400)
        && $newAccountClaims['dostPremiumUntil'] === $newAccountCreatedAt + ($introDays * 86400)
        && $newAccountClaims['dostPremiumSource'] === 'intro'
        && $newAccountClaims['dostPremiumIntroGranted'] === true
        && $newAccountClaims['dostPremiumIntroGrantedAt'] === $now
        && $newAccountClaims['dostAgeEligible'] === true
        && !isset($newAccountClaims['dostPremiumProductId']),
    'A new account receives one intro expiry from account creation without a fake Play product.'
);
dost_premium_test_assert(
    dost_premium_account_creation_epoch((string) (($newAccountCreatedAt * 1000))) === $newAccountCreatedAt,
    'Firebase account creation timestamps in milliseconds are converted to epoch seconds.'
);
$existingAccountClaims = dost_premium_intro_claims(
    $adultClaims,
    $launchAt - 86400,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($existingAccountClaims)
        && $existingAccountClaims['dostExplorePlusUntil'] === $now + ($introDays * 86400)
        && $existingAccountClaims['dostPremiumUntil'] === $now + ($introDays * 86400),
    'An existing account receives the full intro window from its first grant after launch.'
);
$existingWithoutLaunch = dost_premium_intro_claims(
    $adultClaims,
    $now - (2 * 86400),
    $introDays,
    $now
);
dost_premium_test_assert(
    is_array($existingWithoutLaunch)
        && $existingWithoutLaunch['dostPremiumUntil'] === $now + ($introDays * 86400),
    'An older account without a configured launch time receives the full window from its first grant.'
);
$existingPaidClaims = array(
    'dostAgeEligible' => true,
    'dostAgeEligibleUntil' => $now + 21600,
    'dostPremiumUntil' => $now + (120 * 86400),
    'dostPremiumProductId' =>     'dost_explore_plus',
    'dostPremiumSource' => 'play'
);
$introOverPaid = dost_premium_intro_claims(
    $existingPaidClaims,
    $launchAt - 86400,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($introOverPaid)
        && $introOverPaid['dostPremiumUntil'] === $existingPaidClaims['dostPremiumUntil']
        && $introOverPaid['dostPremiumProductId'] === 'dost_explore_plus'
        && $introOverPaid['dostPremiumSource'] === 'play'
        && $introOverPaid['dostPremiumIntroGranted'] === true,
    'An intro marker is recorded without shortening an existing paid entitlement.'
);
$repeatedIntroClaims = dost_premium_intro_claims(
    $existingAccountClaims,
    $now,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    $repeatedIntroClaims === $existingAccountClaims,
    'An intro marker makes subsequent grant attempts idempotent.'
);
$paidDuringIntro = dost_premium_claims(
    $existingAccountClaims,
    'dost_explore_plus',
    $entitlement,
    $now
);
dost_premium_test_assert(
    is_array($paidDuringIntro)
        && $paidDuringIntro['dostPremiumUntil'] === $existingAccountClaims['dostPremiumUntil']
        && $paidDuringIntro['dostPremiumIntroGranted'] === true
        && $paidDuringIntro['dostPremiumProductId'] === 'dost_explore_plus'
        && $paidDuringIntro['dostPremiumSource'] === 'play',
    'A shorter paid subscription records its product and source without reducing or clearing the intro entitlement.'
);
$expiredIntroClaims = array(
    'dostPremiumLifetime' => false,
    'dostPremiumUntil' => $now - 1,
    'dostPremiumSource' => 'intro',
    'dostPremiumIntroGranted' => true
);
dost_premium_test_assert(
    dost_premium_intro_response($expiredIntroClaims, $now)['premium'] === false,
    'An expired intro entitlement is inactive even though its one-time marker remains.'
);
$lifetimeClaims = dost_premium_claims(
    $claims,
    'dost_explore_plus_lifetime',
    $lifetimeEntitlement,
    $now,
    'verified-lifetime-token'
);
dost_premium_test_assert(
    is_array($lifetimeClaims) && $lifetimeClaims['dostExplorePlusLifetime'] === true,
    'A verified lifetime purchase grants a lifetime custom claim.'
);
$keptLifetime = dost_premium_claims($lifetimeClaims, 'dost_explore_plus', $entitlement, $now);
dost_premium_test_assert(
    is_array($keptLifetime)
        && $keptLifetime['dostExplorePlusLifetime'] === true
        && $keptLifetime['dostExplorePlusUntil'] === 0
        && $keptLifetime['dostExplorePlusProductId'] === 'dost_explore_plus_lifetime'
        && $keptLifetime['dostExplorePlusToken'] === hash('sha256', 'verified-lifetime-token')
        && $keptLifetime['dostPremiumLifetime'] === true
        && $keptLifetime['dostPremiumUntil'] === 0
        && $keptLifetime['dostPremiumSource'] === 'play',
    'A later subscription verification must record its source without downgrading lifetime access.'
);
$lifetimeIntro = dost_premium_intro_claims(
    $lifetimeClaims,
    $now - (365 * 86400),
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($lifetimeIntro)
        && $lifetimeIntro['dostPremiumLifetime'] === true
        && $lifetimeIntro['dostPremiumIntroGranted'] === true,
    'A lifetime account is marked as having received the intro without changing its entitlement.'
);
dost_premium_test_assert(
    dost_premium_intro_claims(
        array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => $now + 3600, 'dostAgeCohort' => 'teen'),
        $now - 100,
        $introDays,
        $now
    ) === false,
    'The 90-day intro grant is refused for a teen even with a current eligibility claim.'
);

fwrite(STDOUT, "PASS: Premium purchase verification rules passed.\n");
