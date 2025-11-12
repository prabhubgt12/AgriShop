package com.ledge.cashbook.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.ledge.cashbook.data.local.MonetizationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MonetizationPreferences
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_REMOVE_ADS = "remove_ads"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    val hasRemoveAds: StateFlow<Boolean> = prefs.removeAdsFlow(false)
        .stateIn(scope, SharingStarted.Eagerly, false)

    // Formatted price for remove_ads (e.g., ₹150.00). Null until queried.
    private val _removeAdsPrice = MutableStateFlow<String?>(null)
    val removeAdsPrice: StateFlow<String?> = _removeAdsPrice

    init {
        connectAndQuery()
    }

    private fun connectAndQuery() {
        if (billingClient.isReady) {
            scope.launch {
                queryAndAcknowledgeOwned()
                queryRemoveAdsPrice()
            }
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryAndAcknowledgeOwned()
                        queryRemoveAdsPrice()
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                // Retry later
            }
        })
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { handlePurchases(purchases) }
        }
    }

    suspend fun launchPurchase(activity: Activity): Boolean {
        if (!billingClient.isReady) {
            // Try to connect; caller can retry after a short while
            connectAndQuery()
            return false
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        val detailsResult = billingClient.queryProductDetails(params)
        val pd = detailsResult.productDetailsList?.firstOrNull() ?: return false
        _removeAdsPrice.value = pd.oneTimePurchaseOfferDetails?.formattedPrice
        val offer = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(offer))
            .build()
        val br = billingClient.launchBillingFlow(activity, flowParams)
        return br.responseCode == BillingClient.BillingResponseCode.OK
    }

    suspend fun restorePurchases() {
        queryAndAcknowledgeOwned()
    }

    private suspend fun queryAndAcknowledgeOwned() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        handlePurchases(result.purchasesList ?: emptyList())
    }

    private suspend fun queryRemoveAdsPrice() {
        try {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_REMOVE_ADS)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            val detailsResult = billingClient.queryProductDetails(params)
            val pd = detailsResult.productDetailsList?.firstOrNull()
            _removeAdsPrice.value = pd?.oneTimePurchaseOfferDetails?.formattedPrice
        } catch (_: Exception) {
            // ignore
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val ownsRemoveAds = purchases.any { it.products.contains(PRODUCT_REMOVE_ADS) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Update local entitlement based on current ownership snapshot
        prefs.setRemoveAds(ownsRemoveAds)
        // Acknowledge any unacknowledged purchases
        purchases.filter { it.products.contains(PRODUCT_REMOVE_ADS) && !it.isAcknowledged }
            .forEach { p ->
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                billingClient.acknowledgePurchase(params)
            }
    }
}
