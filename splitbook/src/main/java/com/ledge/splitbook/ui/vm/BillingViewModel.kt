package com.ledge.splitbook.ui.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.ledge.splitbook.data.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingRepo: BillingRepository
) : ViewModel() {

    fun start() = billingRepo.start()
    fun purchaseRemoveAds(activity: Activity) = billingRepo.launchPurchase(activity)
}
