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
    authRepository: FirebaseAuthRepository,
    onBack: () -> Unit,
    onEntitlementChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
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
                    premiumText(
                        language,
                        if (isPremium) "Premium is active. New purchases are temporarily disabled."
                        else "Premium purchases are not available yet. The selected benefits are being prepared.",
                        if (isPremium) "প্রিমিয়াম চালু আছে। নতুন কেনাকাটা সাময়িকভাবে বন্ধ।"
                        else "প্রিমিয়াম কেনাকাটা এখনো চালু হয়নি। নির্বাচিত সুবিধাগুলো প্রস্তুত করা হচ্ছে।",
                        if (isPremium) "प्रीमियम सक्रिय है। नई खरीदारी अस्थायी रूप से बंद है।"
                        else "प्रीमियम खरीदारी अभी उपलब्ध नहीं है। चुनी गई सुविधाएँ तैयार की जा रही हैं।"
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
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
                            if (state.isPremium) {
                                premiumText(language, "Explore Plus is active", "Explore Plus চালু আছে", "Explore Plus सक्रिय है")
                            } else {
                                premiumText(language, "Plans for Explore, Cipher, and Shield", "Explore, Cipher ও Shield-এর প্ল্যান", "Explore, Cipher और Shield के प्लान")
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(premiumText(
                            language,
                            "Choose an optional plan for each mode. Explore Plus and Dost Plus require verified 18+ eligibility. Creating a Shield organization requires an adult owner; invited members may be 13 or older. Cipher is available to teens.",
                            "প্রতিটি মোডের জন্য ঐচ্ছিক প্ল্যান বেছে নিন। Explore Plus ও Dost Plus-এর জন্য যাচাইকৃত ১৮+ যোগ্যতা দরকার। Shield সংস্থা তৈরি করতে মালিককে প্রাপ্তবয়স্ক হতে হবে; আমন্ত্রিত সদস্যদের বয়স ১৩ বা তার বেশি হতে পারে। কিশোরদের জন্য Cipher উপলভ্য।",
                            "हर मोड के लिए वैकल्पिक प्लान चुनें। Explore Plus और Dost Plus के लिए सत्यापित 18+ पात्रता चाहिए। Shield संगठन बनाने के लिए मालिक का वयस्क होना ज़रूरी है; आमंत्रित सदस्य 13 वर्ष या उससे अधिक आयु के हो सकते हैं। Cipher किशोरों के लिए उपलब्ध है।"
                        ))
                    }
                }
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
                            onPurchase = { offerToken ->
                                val currentActivity = activity
                                if (currentActivity != null) {
                                    manager.purchase(currentActivity, product.productId, offerToken)
                                }
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
    onPurchase: (String?) -> Unit
) {
    val title = when (product.productId) {
        "dost_explore_plus" -> "Explore Plus"
        "dost_explore_plus_lifetime" -> "Explore Plus lifetime"
        "dost_cipher" -> "Cipher"
        "dost_shield_team" -> "Shield Team"
        "dost_shield_business" -> "Shield Business"
        "dost_plus" -> "Dost Plus"
        else -> premiumText(language, "Plan", "প্ল্যান", "प्लान")
    }
    val offers: List<Triple<String?, String?, String?>> = if (product.productType == BillingClient.ProductType.SUBS) {
        product.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .values
            .map { basePlanOffers ->
                val offer = basePlanOffers.firstOrNull {
                    it.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L
                } ?: basePlanOffers.first()
                Triple(
                    offer.basePlanId,
                    offer.offerToken,
                    offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                )
            }
    } else {
        listOf(Triple(null, null, product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            offers.forEach { (basePlanId, offerToken, price) ->
                Text(
                    when (basePlanId) {
                        "monthly" -> premiumText(language, "Monthly", "মাসিক", "मासिक")
                        "annual" -> premiumText(language, "Annual", "বার্ষিক", "वार्षिक")
                        else -> premiumText(language, "One-time purchase", "একবারের কেনাকাটা", "एक बार की खरीद")
                    }
                )
                Text(price ?: premiumText(language, "Price unavailable", "মূল্য পাওয়া যাচ্ছে না", "कीमत उपलब्ध नहीं है"))
                Button(
                    onClick = { onPurchase(offerToken) },
                    enabled = enabled && price != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(premiumText(language, "Continue with Google Play", "Google Play দিয়ে চালিয়ে যান", "Google Play से जारी रखें"))
                }
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
