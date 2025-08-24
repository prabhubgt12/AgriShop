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

    private val _pendingEditInvoiceId = MutableStateFlow<Int?>(null)
    val pendingEditInvoiceId: StateFlow<Int?> = _pendingEditInvoiceId

    // One-shot trigger counter to indicate user asked for a fresh New Bill screen
    private val _newBillTick = MutableStateFlow(0)
    val newBillTick: StateFlow<Int> = _newBillTick

    fun navigateTo(index: Int) {
        if (index == 3 && _pendingEditInvoiceId.value == null) {
            // Navigating to Billing without an edit request means start a new bill
            _newBillTick.value = _newBillTick.value + 1
        }
        _selected.value = index
    }

    fun requestEditInvoice(id: Int) { _pendingEditInvoiceId.value = id }
    fun clearPendingEdit() { _pendingEditInvoiceId.value = null }

    fun requestNewBill() { _newBillTick.value = _newBillTick.value + 1 }
}
