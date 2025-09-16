package com.fertipos.agroshop.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppNavViewModel @Inject constructor() : ViewModel() {
    private val _selected = MutableStateFlow(0)
    val selected: StateFlow<Int> = _selected

    // Keep track of the previous tab to support returning after edit flows
    private val _previousSelected = MutableStateFlow(0)
    val previousSelected: StateFlow<Int> = _previousSelected

    // Optional one-shot override for system back navigation target (e.g., return to Customer/Product tab
    // after editing from a History screen).
    private val _backOverrideTab = MutableStateFlow<Int?>(null)
    val backOverrideTab: StateFlow<Int?> = _backOverrideTab

    // One-shot flag to tell Invoice History to keep its current customer filter when returning
    // from Billing (edit flow started from locked customer history).
    private val _preserveInvoiceHistoryFilterOnReturn = MutableStateFlow(false)
    val preserveInvoiceHistoryFilterOnReturn: StateFlow<Boolean> = _preserveInvoiceHistoryFilterOnReturn

    // One-shot flag to tell Purchase History to keep its current product filter when returning
    // from Purchase edit (flow started from product-specific history).
    private val _preservePurchaseHistoryFilterOnReturn = MutableStateFlow(false)
    val preservePurchaseHistoryFilterOnReturn: StateFlow<Boolean> = _preservePurchaseHistoryFilterOnReturn

    private val _pendingEditInvoiceId = MutableStateFlow<Int?>(null)
    val pendingEditInvoiceId: StateFlow<Int?> = _pendingEditInvoiceId

    // For editing an existing Purchase in Purchase screen
    private val _pendingEditPurchaseId = MutableStateFlow<Int?>(null)
    val pendingEditPurchaseId: StateFlow<Int?> = _pendingEditPurchaseId

    // One-shot trigger counter to indicate user asked for a fresh New Bill screen
    private val _newBillTick = MutableStateFlow(0)
    val newBillTick: StateFlow<Int> = _newBillTick

    // Optional product filter for Purchase History screen
    private val _pendingPurchaseHistoryProductId = MutableStateFlow<Int?>(null)
    val pendingPurchaseHistoryProductId: StateFlow<Int?> = _pendingPurchaseHistoryProductId

    // Optional customer filter for Invoice History screen
    private val _pendingInvoiceHistoryCustomerId = MutableStateFlow<Int?>(null)
    val pendingInvoiceHistoryCustomerId: StateFlow<Int?> = _pendingInvoiceHistoryCustomerId

    fun navigateTo(index: Int) {
        // Record previous tab before switching
        if (index != _selected.value) {
            _previousSelected.value = _selected.value
        }
        if (index == 3 && _pendingEditInvoiceId.value == null) {
            // Navigating to Billing without an edit request means start a new bill
            _newBillTick.value = _newBillTick.value + 1
        }
        _selected.value = index
    }

    // Set a one-time back override. Will be consumed by the destination screen BackHandler.
    fun setBackOverrideTab(index: Int) { _backOverrideTab.value = index }
    fun clearBackOverrideTab() { _backOverrideTab.value = null }

    fun setPreserveInvoiceHistoryFilterOnReturn() { _preserveInvoiceHistoryFilterOnReturn.value = true }
    fun clearPreserveInvoiceHistoryFilterOnReturn() { _preserveInvoiceHistoryFilterOnReturn.value = false }

    fun setPreservePurchaseHistoryFilterOnReturn() { _preservePurchaseHistoryFilterOnReturn.value = true }
    fun clearPreservePurchaseHistoryFilterOnReturn() { _preservePurchaseHistoryFilterOnReturn.value = false }

    fun requestEditInvoice(id: Int) { _pendingEditInvoiceId.value = id }
    fun clearPendingEdit() { _pendingEditInvoiceId.value = null }

    fun requestEditPurchase(id: Int) { _pendingEditPurchaseId.value = id }
    fun clearPendingEditPurchase() { _pendingEditPurchaseId.value = null }

    fun requestNewBill() { _newBillTick.value = _newBillTick.value + 1 }

    fun requestPurchaseHistoryForProduct(productId: Int) { _pendingPurchaseHistoryProductId.value = productId }
    fun clearPendingPurchaseHistoryProduct() { _pendingPurchaseHistoryProductId.value = null }

    fun requestInvoiceHistoryForCustomer(customerId: Int) { _pendingInvoiceHistoryCustomerId.value = customerId }
    fun clearPendingInvoiceHistoryCustomer() { _pendingInvoiceHistoryCustomerId.value = null }
}
