package com.snigtus.dost

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class PremiumBillingState(
    val products: List<ProductDetails> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isPremium: Boolean = false,
    val message: String? = null
)

internal data class PremiumEntitlement(
    val isActive: Boolean,
    val isLifetime: Boolean,
    val expiresAtEpochSeconds: Long?
)

internal class PremiumBillingManager(
    context: Context,
    private val authRepository: FirebaseAuthRepository,
    private val onEntitlementChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PremiumBillingState())
    val state: StateFlow<PremiumBillingState> = mutableState.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        scope.launch {
            try {
                val active = authRepository.hasPremiumEntitlement()
                mutableState.value = mutableState.value.copy(isPremium = active)
                onEntitlementChanged(active)
                connect()
                val products = queryProducts()
                mutableState.value = mutableState.value.copy(
                    products = products,
                    isLoading = false,
                    isPremium = active,
                    message = if (products.isEmpty()) "Premium plans are not available right now." else null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    message = error.message ?: "Could not connect to Google Play."
                )
            }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        if (!BuildConfig.DOST_PREMIUM_ENABLED) {
            mutableState.value = mutableState.value.copy(message = "Premium purchases are not available yet.")
            return
        }
        val product = mutableState.value.products.firstOrNull { it.productId == productId }
        if (product == null) {
            mutableState.value = mutableState.value.copy(message = "This Premium plan is unavailable.")
            return
        }
        val uid = authRepository.currentAccount?.uid
        if (uid.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(message = "Sign in before purchasing Premium.")
            return
        }
        val offerToken = if (product.productType == BillingClient.ProductType.SUBS) {
            product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        } else {
            null
        }
        if (product.productType == BillingClient.ProductType.SUBS && offerToken.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(message = "This subscription offer is unavailable.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { offerToken?.let(::setOfferToken) }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setObfuscatedAccountId(obfuscatedAccountId(uid))
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(
                message = result.debugMessage.ifBlank { "Google Play could not start the purchase." }
            )
        }
    }

    fun restorePurchases() {
        scope.launch {
            mutableState.value = mutableState.value.copy(isBusy = true, message = null)
            try {
                val purchases = queryPurchases(BillingClient.ProductType.SUBS) +
                    queryPurchases(BillingClient.ProductType.INAPP)
                if (purchases.isEmpty()) {
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        message = "No Premium purchases were found for this Google Play account."
                    )
                } else {
                    verifyAndAcknowledge(purchases.distinctBy { it.purchaseToken })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isBusy = false,
                    message = error.message ?: "Could not restore purchases."
                )
            }
        }
    }

    fun close() {
        scope.cancel()
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val verifiedPurchases = purchases.orEmpty()
                if (verifiedPurchases.isEmpty()) {
                    mutableState.value = mutableState.value.copy(message = "Google Play returned no purchase.")
                } else {
                    scope.launch {
                        try {
                            verifyAndAcknowledge(verifiedPurchases)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            mutableState.value = mutableState.value.copy(
                                isBusy = false,
                                message = error.message ?: "Purchase verification failed. Try Restore purchases."
                            )
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> mutableState.value = mutableState.value.copy(
                message = result.debugMessage.ifBlank { "Google Play could not complete the purchase." }
            )
        }
    }

    private suspend fun connect() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine<Unit> { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!continuation.isActive) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            IOException(result.debugMessage.ifBlank { "Google Play Billing is unavailable." })
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("Google Play disconnected. Try again."))
                    }
                }
            })
            continuation.invokeOnCancellation {
                if (billingClient.isReady) billingClient.endConnection()
            }
        }
    }

    private suspend fun queryProducts(): List<ProductDetails> =
        suspendCancellableCoroutine { continuation ->
            val products = listOf(
                BillingClient.ProductType.SUBS to PREMIUM_MONTHLY_PRODUCT_ID,
                BillingClient.ProductType.SUBS to PREMIUM_ANNUAL_PRODUCT_ID,
                BillingClient.ProductType.INAPP to PREMIUM_LIFETIME_PRODUCT_ID
            ).map { (type, id) ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(type)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(detailsResult.productDetailsList)
                } else {
                    continuation.resumeWithException(
                        IOException(result.debugMessage.ifBlank { "Could not load Premium plans." })
                    )
                }
            }
        }

    private suspend fun queryPurchases(productType: String): List<Purchase> =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases)
                } else {
                    continuation.resumeWithException(
                        IOException(result.debugMessage.ifBlank { "Could not query Google Play purchases." })
                    )
                }
            }
        }

    private suspend fun verifyAndAcknowledge(purchases: List<Purchase>) {
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        val uid = authRepository.currentAccount?.uid
            ?: throw IOException("Sign in again to restore Premium.")
        var verified = false
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        message = "Payment is pending. Premium starts after Google confirms payment."
                    )
                }
                Purchase.PurchaseState.PURCHASED -> {
                    val productId = purchase.products.firstOrNull { it in PREMIUM_PRODUCT_IDS }
                        ?: throw IOException("Google Play returned an unrecognized Premium product.")
                    verifyPurchase(uid, productId, purchase.purchaseToken)
                    if (!purchase.isAcknowledged) acknowledge(purchase.purchaseToken)
                    verified = true
                }
                else -> Unit
            }
        }
        if (!verified) return
        val active = authRepository.hasPremiumEntitlement(forceRefresh = true)
        if (!active) throw IOException("The verified Premium entitlement was not returned by Firebase.")
        mutableState.value = mutableState.value.copy(isBusy = false, isPremium = true, message = null)
        onEntitlementChanged(true)
    }

    private suspend fun verifyPurchase(uid: String, productId: String, purchaseToken: String) {
        val idToken = authRepository.currentIdToken()
        withContext(Dispatchers.IO) {
            val connection = (URL("$PREMIUM_API_ROOT/premium_verify.php").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            try {
                val body = JSONObject()
                    .put("uid", uid)
                    .put("product_id", productId)
                    .put("purchase_token", purchaseToken)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText().take(MAX_RESPONSE_CHARS) }
                    .orEmpty()
                val json = try {
                    JSONObject(response)
                } catch (error: Exception) {
                    throw IOException("Premium verification returned an invalid response.", error)
                }
                if (status !in 200..299 || json.optBoolean("premium") != true) {
                    throw IOException(json.optString("error").ifBlank {
                        "Premium purchase could not be verified (HTTP $status)."
                    })
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun acknowledge(purchaseToken: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (!continuation.isActive) return@acknowledgePurchase
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IOException(result.debugMessage.ifBlank { "Google Play could not acknowledge the purchase." })
                    )
                }
            }
        }
    }

    private fun obfuscatedAccountId(uid: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("dost-premium-v1\n$uid".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREMIUM_MONTHLY_PRODUCT_ID = "dost_premium_monthly"
        const val PREMIUM_ANNUAL_PRODUCT_ID = "dost_premium_annual"
        const val PREMIUM_LIFETIME_PRODUCT_ID = "dost_premium_lifetime"
        val PREMIUM_PRODUCT_IDS = setOf(
            PREMIUM_MONTHLY_PRODUCT_ID,
            PREMIUM_ANNUAL_PRODUCT_ID,
            PREMIUM_LIFETIME_PRODUCT_ID
        )
        const val PREMIUM_API_ROOT = "https://bisque-bear-900175.hostingersite.com/api"
        const val MAX_RESPONSE_CHARS = 32_768
    }
}
