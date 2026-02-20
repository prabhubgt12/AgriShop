package com.ledge.splitbook.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.ProductDetails
import com.ledge.splitbook.data.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {

    companion object { const val PRODUCT_REMOVE_ADS = "remove_ads" }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    @Volatile private var productDetails: ProductDetails? = null

    fun start() {
        if (billingClient.isReady) {
            queryProduct()
            queryOwnedPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() { /* will reconnect next call */ }
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryOwnedPurchases()
                }
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_REMOVE_ADS)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build())
            ).build()
        billingClient.queryProductDetailsAsync(params) { _, list ->
            productDetails = list.firstOrNull()
        }
    }

    private fun queryOwnedPurchases() {
        billingClient.queryPurchasesAsync(BillingClient.ProductType.INAPP) { _, purchases ->
            handlePurchases(purchases)
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            ))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val ownsRemoveAds = purchases.any { p ->
            p.products.contains(PRODUCT_REMOVE_ADS) && p.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        // Persist entitlement state (including clearing it after refunds)
        CoroutineScope(Dispatchers.IO).launch { settingsRepository.setRemoveAds(ownsRemoveAds) }

        // Acknowledge any unacknowledged remove_ads purchases
        purchases
            .filter { p -> p.products.contains(PRODUCT_REMOVE_ADS) && !p.isAcknowledged }
            .forEach { p ->
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                billingClient.acknowledgePurchase(ack) { /* ignore */ }
            }
    }
}
