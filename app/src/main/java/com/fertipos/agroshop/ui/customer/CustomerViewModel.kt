package com.fertipos.agroshop.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.PurchaseDao
import com.fertipos.agroshop.data.local.entities.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val dao: CustomerDao,
    private val invoiceDao: InvoiceDao,
    private val purchaseDao: PurchaseDao
) : ViewModel() {

    data class UiState(
        val customers: List<Customer> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<UiState> = combine(dao.getAll(), _error) { list, err ->
        UiState(customers = list, error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun add(name: String, phone: String?, address: String?, isSupplier: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insert(
                Customer(
                    name = name.trim(),
                    phone = phone?.trim().takeUnless { it.isNullOrBlank() },
                    address = address?.trim().takeUnless { it.isNullOrBlank() },
                    isSupplier = isSupplier
                )
            )
        }
    }

    suspend fun addAndReturnId(name: String, phone: String?, address: String?, isSupplier: Boolean): Int {
        if (name.isBlank()) return 0
        val id = dao.insert(
            Customer(
                name = name.trim(),
                phone = phone?.trim().takeUnless { it.isNullOrBlank() },
                address = address?.trim().takeUnless { it.isNullOrBlank() },
                isSupplier = isSupplier
            )
        )
        return id.toInt()
    }

    fun update(customer: Customer, name: String, phone: String?, address: String?, isSupplier: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.update(
                customer.copy(
                    name = name.trim(),
                    phone = phone?.trim().takeUnless { it.isNullOrBlank() },
                    address = address?.trim().takeUnless { it.isNullOrBlank() },
                    isSupplier = isSupplier,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(customer: Customer) {
        viewModelScope.launch {
            try {
                dao.delete(customer)
            } catch (e: Exception) {
                _error.value = "ERR_CUSTOMER_REFERENCED"
            }
        }
    }

    fun clearError() { _error.value = null }

    suspend fun isReferenced(customerId: Int): Boolean {
        val invs = invoiceDao.countInvoicesForCustomer(customerId)
        val purs = purchaseDao.countPurchasesForSupplier(customerId)
        return invs > 0 || purs > 0
    }
}
