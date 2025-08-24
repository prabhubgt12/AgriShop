package com.fertipos.agroshop.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.entities.Customer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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

    val state: StateFlow<UiState> = dao.getAll()
        .map { UiState(customers = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun add(name: String, phone: String?, address: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insert(Customer(name = name.trim(), phone = phone?.trim().takeUnless { it.isNullOrBlank() }, address = address?.trim().takeUnless { it.isNullOrBlank() }))
        }
    }

    fun update(customer: Customer, name: String, phone: String?, address: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.update(customer.copy(name = name.trim(), phone = phone?.trim().takeUnless { it.isNullOrBlank() }, address = address?.trim().takeUnless { it.isNullOrBlank() }, updatedAt = System.currentTimeMillis()))
        }
    }

    fun delete(customer: Customer) {
        viewModelScope.launch { dao.delete(customer) }
    }
}
