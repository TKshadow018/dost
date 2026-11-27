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
    isShieldPlansOnly: Boolean = false,
    authRepository: FirebaseAuthRepository,
    onBack: () -> Unit,
    onEntitlementChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val purchaseFlowEnabled = if (isShieldPlansOnly) {
        BuildConfig.DOST_SUPER_SECURE_ENABLED
    } else {
        BuildConfig.DOST_PREMIUM_ENABLED
    }
    if (!purchaseFlowEnabled) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (isShieldPlansOnly) "Shield plans"
                            else premiumText(language, "Explore Plus", "এক্সপ্লোর প্লাস", "एक्सप्लोर प्लस")
                        )
                    },
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
                        if (isShieldPlansOnly) "The organisation owner pays. Members join for free."
                        else if (isPremium) "Explore Plus is active. New purchases are temporarily disabled."
                        else "Explore Plus purchases are not available yet. The selected benefits are being prepared.",
                        if (isShieldPlansOnly) "সংস্থার মালিক অর্থ প্রদান করেন। সদস্যরা বিনামূল্যে যোগ দেন।"
                        else if (isPremium) "এক্সপ্লোর প্লাস চালু আছে। নতুন কেনাকাটা সাময়িকভাবে বন্ধ।"
                        else "এক্সপ্লোর প্লাস কেনাকাটা এখনো চালু হয়নি। নির্বাচিত সুবিধাগুলো প্রস্তুত করা হচ্ছে।",
                        if (isShieldPlansOnly) "संगठन का मालिक भुगतान करता है। सदस्य मुफ़्त में जुड़ते हैं।"
                        else if (isPremium) "एक्सप्लोर प्लस सक्रिय है। नई खरीदारी अस्थायी रूप से बंद है।"
                        else "एक्सप्लोर प्लस खरीदारी अभी उपलब्ध नहीं है। चुनी गई सुविधाएँ तैयार की जा रही हैं।"
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
                title = {
                    Text(
                        if (isShieldPlansOnly) "Shield plans"
                        else premiumText(language, "Explore Plus", "এক্সপ্লোর প্লাস", "एक्सप्लोर प्लस")
                    )
                },
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
                            if (isShieldPlansOnly && state.isShieldActive) {
                                "Shield is active for this organisation owner."
                            } else if (state.isPremium && !isShieldPlansOnly) {
                                premiumText(language, "Premium is active", "প্রিমিয়াম চালু আছে", "प्रीमियम सक्रिय है")
                            } else if (isShieldPlansOnly) {
                                "Closed, admin-controlled organisation messaging, isolated from AI and analytics. Encrypted in transit and at rest by Google Cloud; not end-to-end encrypted."
                            } else {
                                premiumText(language, "More room for Explore", "Explore-এর জন্য আরও সুবিধা", "Explore के लिए अधिक सुविधाएँ")
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!isShieldPlansOnly) {
                            Text(premiumText(
                                language,
                                "Unlimited AI companions and People likes, one daily 30-minute profile boost, chat themes, and expanded AI-friend customization.",
                                "সীমাহীন AI বন্ধু ও People লাইক, প্রতিদিন ৩০ মিনিটের একটি প্রোফাইল বুস্ট, চ্যাট থিম এবং AI বন্ধুকে আরও কাস্টমাইজ করার সুবিধা।",
                                "असीमित AI दोस्त और People लाइक, रोज़ एक 30-मिनट प्रोफ़ाइल बूस्ट, चैट थीम और AI दोस्त को अधिक कस्टमाइज़ करने की सुविधा।"
                            ))
                        }
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
                val visibleProducts = state.products.filter {
                    if (isShieldPlansOnly) {
                        it.productId in setOf("dost_shield_team", "dost_shield_business")
                    } else {
                        it.productId !in setOf("dost_shield_team", "dost_shield_business")
                    }
                }
                if (visibleProducts.isNotEmpty()) {
                    items(visibleProducts, key = { it.productId }) { product ->
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
                            if (isShieldPlansOnly) "Subscriptions renew through Google Play until canceled. The owner pays; invited members join free. Prices and billing periods are shown by Google Play before confirmation."
                            else "Subscriptions renew through Google Play until canceled. The lifetime plan is a one-time purchase. Prices and billing periods are shown by Google Play before you confirm.",
                            if (isShieldPlansOnly) "সাবস্ক্রিপশন বাতিল না করা পর্যন্ত Google Play-এর মাধ্যমে নবায়ন হবে। মালিক অর্থ দেন; আমন্ত্রিত সদস্যরা বিনামূল্যে যোগ দেন। নিশ্চিত করার আগে Google Play মূল্য ও বিলিং সময় দেখায়।"
                            else "সাবস্ক্রিপশন বাতিল না করা পর্যন্ত Google Play-এর মাধ্যমে নবায়ন হবে। লাইফটাইম প্ল্যান একবারের কেনাকাটা। নিশ্চিত করার আগে Google Play মূল্য ও বিলিং সময় দেখাবে।",
                            if (isShieldPlansOnly) "सदस्यता रद्द होने तक Google Play से नवीनीकृत होती है। मालिक भुगतान करता है; आमंत्रित सदस्य मुफ़्त में जुड़ते हैं। पुष्टि से पहले Google Play कीमत और अवधि दिखाता है।"
                            else "सदस्यता रद्द होने तक Google Play से नवीनीकृत होती है। लाइफटाइम प्लान एक बार की खरीद है। पुष्टि से पहले Google Play कीमत और बिलिंग अवधि दिखाएगा।"
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
        "dost_shield_team" -> "Shield Team · up to 10 members"
        "dost_shield_business" -> "Shield Business · up to 25 members"
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
