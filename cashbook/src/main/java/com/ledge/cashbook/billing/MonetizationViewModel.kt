package com.ledge.cashbook.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MonetizationViewModel @Inject constructor(
    private val billing: BillingManager
) : ViewModel() {

    val hasRemoveAds: StateFlow<Boolean> = billing.hasRemoveAds
    val removeAdsPrice: StateFlow<String?> = billing.removeAdsPrice

    suspend fun purchaseRemoveAds(activity: Activity): Boolean {
        return billing.launchPurchase(activity)
    }

    fun restore() {
        viewModelScope.launch { billing.restorePurchases() }
    }
}
