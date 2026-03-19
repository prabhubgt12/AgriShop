package com.ledge.splitbook.ui.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingRepo: BillingRepository
) : ViewModel() {

    private val _productPrice = MutableStateFlow<String?>(null)
    val productPrice: StateFlow<String?> = _productPrice.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun start() {
        billingRepo.start()
        viewModelScope.launch {
            // Listen for product updates
            billingRepo.productUpdates.collect { details ->
                _productPrice.value = details?.getOneTimePurchaseOfferDetails()?.formattedPrice
                _isLoading.value = false // Stop loading once we have price or null
            }
        }
    }
    
    fun purchaseRemoveAds(activity: Activity) = billingRepo.launchPurchase(activity)
}
