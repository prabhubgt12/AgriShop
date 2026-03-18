package com.ledge.ledgerbook.billing

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
    val productPrice: StateFlow<String?> = billing.productPrice
    val isLoading: StateFlow<Boolean> = billing.isLoading

    fun purchaseRemoveAds(activity: Activity) {
        viewModelScope.launch { billing.launchPurchase(activity) }
    }

    fun restore() {
        viewModelScope.launch { billing.restorePurchases() }
    }
}
