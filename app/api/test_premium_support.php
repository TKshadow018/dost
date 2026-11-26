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
$subscription = array(
    'externalAccountIdentifiers' => array('obfuscatedExternalAccountId' => $accountId),
    'subscriptionState' => 'SUBSCRIPTION_STATE_CANCELED',
    'lineItems' => array(array(
        'productId' => 'dost_premium_monthly',
        'expiryTime' => gmdate(DATE_ATOM, $now + 3600)
    ))
);
$entitlement = dost_premium_purchase_entitlement(
    'dost_premium_monthly',
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
    dost_premium_purchase_entitlement('dost_premium_monthly', 'subscription', $subscription, 'other-user', $now) === false,
    'A purchase bound to another Firebase account must be rejected.'
);

$expired = $subscription;
$expired['lineItems'][0]['expiryTime'] = gmdate(DATE_ATOM, $now - 1);
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_premium_monthly', 'subscription', $expired, $uid, $now) === false,
    'An expired subscription must not grant Premium.'
);
$lifetime = array(
    'obfuscatedExternalAccountId' => $accountId,
    'purchaseStateContext' => array('purchaseState' => 'PURCHASED'),
    'productLineItem' => array(array('productId' => 'dost_premium_lifetime'))
);
$lifetimeEntitlement = dost_premium_purchase_entitlement(
    'dost_premium_lifetime',
    'lifetime',
    $lifetime,
    $uid,
    $now
);
dost_premium_test_assert(
    is_array($lifetimeEntitlement) && $lifetimeEntitlement['lifetime'] === true,
    'A verified lifetime product grants a non-expiring entitlement.'
);
dost_premium_test_assert(
    dost_premium_purchase_entitlement('dost_premium_annual', 'subscription', $subscription, $uid, $now) === false,
    'A purchase response for another product must not grant the requested plan.'
);

$claims = dost_premium_claims(
    array('dostAgeEligible' => true, 'dostAgeEligibleUntil' => $now + 21600),
    'dost_premium_monthly',
    $entitlement,
    $now
);
dost_premium_test_assert(
    is_array($claims)
        && $claims['dostAgeEligible'] === true
        && $claims['dostPremiumUntil'] === $now + 3600,
    'Premium claims must preserve unrelated Firebase custom claims.'
);
$introDays = 90;
$launchAt = $now - 86400;
$newAccountCreatedAt = $now - 3600;
$newAccountClaims = dost_premium_intro_claims(
    array('dostAgeEligible' => true),
    $newAccountCreatedAt,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($newAccountClaims)
        && $newAccountClaims['dostPremiumUntil'] === $newAccountCreatedAt + ($introDays * 86400)
        && $newAccountClaims['dostPremiumSource'] === 'intro'
        && $newAccountClaims['dostPremiumIntroGranted'] === true
        && $newAccountClaims['dostPremiumIntroGrantedAt'] === $now
        && $newAccountClaims['dostAgeEligible'] === true
        && !isset($newAccountClaims['dostPremiumProductId']),
    'A new account receives one intro expiry from account creation without a fake Play product.'
);
$existingAccountClaims = dost_premium_intro_claims(
    array('dostAgeEligible' => true),
    $launchAt - 86400,
    $introDays,
    $now,
    $launchAt
);
dost_premium_test_assert(
    is_array($existingAccountClaims)
        && $existingAccountClaims['dostPremiumUntil'] === $now + ($introDays * 86400),
    'An existing account receives the full intro window from its first grant after launch.'
);
$repeatedIntroClaims = dost_premium_intro_claims(
    $existingAccountClaims,
    $now + 100000,
    $introDays,
    $now + 100000,
    $launchAt
);
dost_premium_test_assert(
    $repeatedIntroClaims === $existingAccountClaims,
    'An intro marker makes subsequent grant attempts idempotent.'
);
$paidDuringIntro = dost_premium_claims(
    $existingAccountClaims,
    'dost_premium_monthly',
    $entitlement,
    $now
);
dost_premium_test_assert(
    is_array($paidDuringIntro)
        && $paidDuringIntro['dostPremiumUntil'] === $existingAccountClaims['dostPremiumUntil']
        && $paidDuringIntro['dostPremiumIntroGranted'] === true
        && $paidDuringIntro['dostPremiumProductId'] === 'dost_premium_monthly'
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
$lifetimeClaims = dost_premium_claims($claims, 'dost_premium_lifetime', $lifetimeEntitlement, $now);
dost_premium_test_assert(
    is_array($lifetimeClaims) && $lifetimeClaims['dostPremiumLifetime'] === true,
    'A verified lifetime purchase grants a lifetime custom claim.'
);
$keptLifetime = dost_premium_claims($lifetimeClaims, 'dost_premium_monthly', $entitlement, $now);
dost_premium_test_assert(
    is_array($keptLifetime)
        && $keptLifetime['dostPremiumLifetime'] === true
        && $keptLifetime['dostPremiumUntil'] === 0
        && $keptLifetime['dostPremiumSource'] === 'play',
    'A later subscription verification must record its source without downgrading lifetime access.'
);

fwrite(STDOUT, "PASS: Premium purchase verification rules passed.\n");
