package com.fertipos.agroshop.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
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
    private val dao: CustomerDao
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
                _error.value = "Cannot delete customer. It is referenced by existing bills or purchases."
            }
        }
    }

    fun clearError() { _error.value = null }
}
