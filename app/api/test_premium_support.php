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
$lifetimeClaims = dost_premium_claims($claims, 'dost_premium_lifetime', $lifetimeEntitlement, $now);
dost_premium_test_assert(
    is_array($lifetimeClaims) && $lifetimeClaims['dostPremiumLifetime'] === true,
    'A verified lifetime purchase grants a lifetime custom claim.'
);
$keptLifetime = dost_premium_claims($lifetimeClaims, 'dost_premium_monthly', $entitlement, $now);
dost_premium_test_assert(
    is_array($keptLifetime) && $keptLifetime['dostPremiumLifetime'] === true,
    'A later subscription verification must not downgrade lifetime access.'
);

fwrite(STDOUT, "PASS: Premium purchase verification rules passed.\n");
