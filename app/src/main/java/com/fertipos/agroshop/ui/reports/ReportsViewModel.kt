package com.fertipos.agroshop.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Invoice
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao
) : ViewModel() {

    data class CustomerSummary(
        val customerId: Int,
        val customerName: String,
        val invoiceCount: Int,
        val totalAmount: Double,
        val lastInvoiceDate: Long
    )

    data class UiState(
        val summaries: List<CustomerSummary> = emptyList(),
        val customers: List<Customer> = emptyList(),
        val selectedCustomerId: Int? = null,
        val invoices: List<Invoice> = emptyList(),
        val subtotal: Double = 0.0,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val selectedCustomer = MutableStateFlow<Int?>(null)

    private val baseState: StateFlow<UiState> = combine(
        invoiceDao.getCustomerSummaries(),
        customerDao.getAll()
    ) { rows, customers ->
        UiState(
            summaries = rows.map {
                CustomerSummary(
                    customerId = it.customerId,
                    customerName = it.customerName,
                    invoiceCount = it.invoiceCount,
                    totalAmount = it.totalAmount,
                    lastInvoiceDate = it.lastInvoiceDate
                )
            },
            customers = customers
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private val filteredInvoices: StateFlow<List<Invoice>> = selectedCustomer
        .flatMapLatest { id ->
            val cid = id ?: -1
            if (cid <= 0) kotlinx.coroutines.flow.flowOf(emptyList())
            else invoiceDao.getInvoicesForCustomer(cid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<UiState> = combine(baseState, selectedCustomer, filteredInvoices) { base, selId, invoices ->
        val effectiveSelected = selId ?: base.customers.firstOrNull()?.id
        val list = if (selId == null) invoices else invoices
        val sum = list.sumOf { it.total }
        base.copy(
            selectedCustomerId = effectiveSelected,
            invoices = list,
            subtotal = sum
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun setSelectedCustomer(id: Int) {
        selectedCustomer.value = id
    }

    // Export helpers
    suspend fun getExportRowsForCustomer(customerId: Int): Pair<String, List<com.fertipos.agroshop.data.local.dao.InvoiceDao.ExportRow>> {
        return withContext(Dispatchers.IO) {
            val name = customerDao.getById(customerId)?.name ?: "Customer_$customerId"
            val rows = invoiceDao.getExportRowsForCustomerOnce(customerId)
            name to rows
        }
    }

    suspend fun getExportRowsAll(): List<com.fertipos.agroshop.data.local.dao.InvoiceDao.ExportRowAll> {
        return withContext(Dispatchers.IO) {
            invoiceDao.getExportRowsAllOnce()
        }
    }
}
