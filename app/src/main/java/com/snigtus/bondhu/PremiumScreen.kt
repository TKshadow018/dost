@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.snigtus.dost

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails

@Composable
internal fun PremiumScreen(
    language: AppLanguage,
    uid: String,
    isPremium: Boolean,
    entitlement: PremiumEntitlement,
    authRepository: FirebaseAuthRepository,
    onBack: () -> Unit,
    onEntitlementChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val introExpiry = entitlement.expiresAtEpochSeconds
        ?.takeIf { isPremium && !entitlement.isLifetime && entitlement.source == "intro" }
    val introExpiryText = introExpiry?.let {
        LocalizedText.FREE_PREMIUM_UNTIL.resolve(language, localizedPremiumDate(it, language))
    }
    val introNearExpiry = premiumIntroIsNearExpiry(entitlement, System.currentTimeMillis() / 1000)
    if (!BuildConfig.DOST_PREMIUM_ENABLED) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(premiumText(language, "Dost Premium", "দোস্ত প্রিমিয়াম", "दोस्त प्रीमियम")) },
                    navigationIcon = { DostBackButton(language, onBack) }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (isPremium) premiumText(
                        language,
                        "Premium is active. New purchases are temporarily disabled.",
                        "প্রিমিয়াম চালু আছে। নতুন কেনাকাটা সাময়িকভাবে বন্ধ।",
                        "प्रीमियम सक्रिय है। नई खरीदारी अस्थायी रूप से बंद है।"
                    ) else LocalizedText.PREMIUM_PURCHASES_UNAVAILABLE.resolve(language),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(LocalizedText.PREMIUM_LIMITS_BENEFIT.resolve(language))
                if (introExpiryText != null) {
                    Text(introExpiryText, style = MaterialTheme.typography.titleMedium)
                }
                if (introNearExpiry) {
                    Text(LocalizedText.PREMIUM_INTRO_EXPIRING.resolve(language))
                    Text(LocalizedText.PREMIUM_PURCHASES_UNAVAILABLE.resolve(language))
                }
            }
        }
        return
    }
    val activity = context.findActivity()
    val manager = remember(context, uid) {
        PremiumBillingManager(context, authRepository, onEntitlementChanged)
    }
    val state by manager.state.collectAsState()
    DisposableEffect(manager) {
        manager.start()
        onDispose { manager.close() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(premiumText(language, "Dost Premium", "দোস্ত প্রিমিয়াম", "दोस्त प्रीमियम")) },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isPremium) {
                                premiumText(language, "Premium is active", "প্রিমিয়াম চালু আছে", "प्रीमियम सक्रिय है")
                            } else {
                                premiumText(language, "More room for AI friends", "AI বন্ধুর জন্য আরও সুযোগ", "AI दोस्तों के लिए अधिक जगह")
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(LocalizedText.PREMIUM_LIMITS_BENEFIT.resolve(language))
                    }
                }
            }
            if (introExpiryText != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            introExpiryText,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            if (introNearExpiry) {
                item { Text(LocalizedText.PREMIUM_INTRO_EXPIRING.resolve(language)) }
            }
            if (uid.isBlank()) {
                item {
                    Text(
                        premiumText(language, "Sign in to purchase and restore Premium.", "প্রিমিয়াম কিনতে ও পুনরুদ্ধার করতে সাইন ইন করুন।", "प्रीमियम खरीदने और पुनर्स्थापित करने के लिए साइन इन करें।"),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                if (state.isLoading) {
                    item { Text(premiumText(language, "Loading plans…", "প্ল্যান লোড হচ্ছে…", "प्लान लोड हो रहे हैं…")) }
                }
                if (state.products.isNotEmpty()) {
                    items(state.products, key = { it.productId }) { product ->
                        PremiumPlanCard(
                            language = language,
                            product = product,
                            enabled = !state.isBusy && activity != null,
                            onPurchase = {
                                val currentActivity = activity
                                if (currentActivity != null) manager.purchase(currentActivity, product.productId)
                            }
                        )
                    }
                }
                if (state.message != null) {
                    item {
                        Text(
                            state.message.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = manager::restorePurchases,
                        enabled = !state.isBusy && !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isBusy) {
                            premiumText(language, "Checking purchases…", "কেনাকাটা যাচাই হচ্ছে…", "खरीदारी जाँची जा रही है…")
                        } else {
                            premiumText(language, "Restore purchases", "কেনাকাটা পুনরুদ্ধার করুন", "खरीदारी पुनर्स्थापित करें")
                        })
                    }
                }
                item {
                    Text(
                        premiumText(
                            language,
                            "Subscriptions renew through Google Play until canceled. The lifetime plan is a one-time purchase. Prices and billing periods are shown by Google Play before you confirm.",
                            "সাবস্ক্রিপশন বাতিল না করা পর্যন্ত Google Play-এর মাধ্যমে নবায়ন হবে। লাইফটাইম প্ল্যান একবারের কেনাকাটা। নিশ্চিত করার আগে Google Play মূল্য ও বিলিং সময় দেখাবে।",
                            "सदस्यता रद्द होने तक Google Play से नवीनीकृत होती है। लाइफटाइम प्लान एक बार की खरीद है। पुष्टि से पहले Google Play कीमत और बिलिंग अवधि दिखाएगा।"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumPlanCard(
    language: AppLanguage,
    product: ProductDetails,
    enabled: Boolean,
    onPurchase: () -> Unit
) {
    val title = when (product.productId) {
        "dost_premium_monthly" -> premiumText(language, "Monthly", "মাসিক", "मासिक")
        "dost_premium_annual" -> premiumText(language, "Annual", "বার্ষিক", "वार्षिक")
        else -> premiumText(language, "Lifetime", "লাইফটাইম", "लाइफटाइम")
    }
    val price = if (product.productType == BillingClient.ProductType.SUBS) {
        product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.lastOrNull()
            ?.formattedPrice
    } else {
        product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(price ?: premiumText(language, "Price unavailable", "মূল্য পাওয়া যাচ্ছে না", "कीमत उपलब्ध नहीं है"))
            Button(onClick = onPurchase, enabled = enabled && price != null, modifier = Modifier.fillMaxWidth()) {
                Text(premiumText(language, "Continue with Google Play", "Google Play দিয়ে চালিয়ে যান", "Google Play से जारी रखें"))
            }
        }
    }
}

private fun premiumText(language: AppLanguage, english: String, bangla: String, hindi: String): String =
    when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.BANGLA -> bangla
        AppLanguage.HINDI -> hindi
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
