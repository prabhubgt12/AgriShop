package com.fertipos.agroshop.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.fertipos.agroshop.data.local.MonetizationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
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

    init {
        connectAndQuery()
    }

    private fun connectAndQuery() {
        if (billingClient.isReady) {
            scope.launch { queryAndAcknowledgeOwned() }
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryAndAcknowledgeOwned() }
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

    suspend fun launchPurchase(activity: Activity) {
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
        val pd = detailsResult.productDetailsList?.firstOrNull() ?: return
        val offer = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(offer))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
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

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val ownsRemoveAds = purchases.any { it.products.contains(PRODUCT_REMOVE_ADS) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (ownsRemoveAds) {
            prefs.setRemoveAds(true)
        }
        // Acknowledge any unacknowledged purchases
        purchases.filter { it.products.contains(PRODUCT_REMOVE_ADS) && !it.isAcknowledged }
            .forEach { p ->
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                billingClient.acknowledgePurchase(params)
            }
    }
}
