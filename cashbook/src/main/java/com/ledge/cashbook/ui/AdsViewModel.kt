package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import com.ledge.cashbook.billing.BillingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val billing: BillingManager
) : ViewModel() {
    val hasRemoveAds: StateFlow<Boolean> = billing.hasRemoveAds
}
