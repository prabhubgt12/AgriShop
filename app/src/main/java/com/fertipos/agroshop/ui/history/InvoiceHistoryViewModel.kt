package com.fertipos.agroshop.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.repo.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@HiltViewModel
class InvoiceHistoryViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val billingRepo: BillingRepository
) : ViewModel() {

    data class InvoiceRow(
        val invoice: Invoice,
        val customerName: String
    )

    data class InvoiceItemRow(
        val item: InvoiceItem,
        val product: Product?
    )

    private val selectedInvoiceId = MutableStateFlow<Int?>(null)
    private val fromMillis = MutableStateFlow<Long?>(null)
    private val toMillis = MutableStateFlow<Long?>(null)

    val listState: StateFlow<List<InvoiceRow>> = combine(
        invoiceDao.getAllInvoices(),
        customerDao.getAll(),
        fromMillis,
        toMillis
    ) { invoices, customers, from, to ->
        val map = customers.associateBy { it.id }
        invoices
            .asSequence()
            .filter { inv -> (from == null || inv.date >= from) && (to == null || inv.date <= to) }
            .map { inv -> InvoiceRow(inv, map[inv.customerId]?.name ?: "Unknown") }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detailState: StateFlow<List<InvoiceItemRow>> = selectedInvoiceId.flatMapLatest { id ->
        if (id == null) {
            MutableStateFlow(emptyList())
        } else combine(
            invoiceDao.getItemsForInvoice(id),
            productDao.getAll()
        ) { items, products ->
            val pmap = products.associateBy { it.id }
            items.map { InvoiceItemRow(it, pmap[it.productId]) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedInvoice: StateFlow<InvoiceRow?> = combine(
        selectedInvoiceId,
        listState
    ) { id, rows ->
        if (id == null) null else rows.firstOrNull { it.invoice.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectInvoice(id: Int?) { selectedInvoiceId.value = id }

    fun setDateRange(from: Long?, to: Long?) {
        fromMillis.value = from
        toMillis.value = to
    }

    fun deleteInvoice(invoiceId: Int) {
        viewModelScope.launch {
            billingRepo.deleteInvoice(invoiceId)
            if (selectedInvoiceId.value == invoiceId) {
                selectedInvoiceId.value = null
            }
        }
    }

    suspend fun getItemRowsOnce(invoiceId: Int): List<InvoiceItemRow> {
        val items = invoiceDao.getItemsForInvoice(invoiceId).first()
        val products = productDao.getAll().first().associateBy { it.id }
        return items.map { InvoiceItemRow(it, products[it.productId]) }
    }
}
