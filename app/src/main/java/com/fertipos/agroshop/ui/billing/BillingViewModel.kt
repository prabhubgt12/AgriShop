package com.fertipos.agroshop.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.repo.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingRepo: BillingRepository,
    customerDao: CustomerDao,
    productDao: ProductDao
) : ViewModel() {

    data class DraftItem(
        val product: Product,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double
    )

    data class UiState(
        val customers: List<Customer> = emptyList(),
        val products: List<Product> = emptyList(),
        val selectedCustomerId: Int? = null,
        val items: List<DraftItem> = emptyList(),
        val notes: String = "",
        val subtotal: Double = 0.0,
        val gstAmount: Double = 0.0,
        val total: Double = 0.0,
        val paid: Double = 0.0,
        val balance: Double = 0.0,
        val loading: Boolean = false,
        val error: String? = null,
        val successInvoiceId: Int? = null,
        val editingInvoiceId: Int? = null,
        val dateMillis: Long = System.currentTimeMillis()
    )

    private val selectedCustomer = MutableStateFlow<Int?>(null)
    private val draftItems = MutableStateFlow<List<DraftItem>>(emptyList())
    private val notes = MutableStateFlow("")
    private val paidText = MutableStateFlow("")
    private val status = MutableStateFlow(Pair(false, null as String?))
    private val successId = MutableStateFlow<Int?>(null)
    private val editingInvoiceId = MutableStateFlow<Int?>(null)
    private val billDate = MutableStateFlow(System.currentTimeMillis())

    private val customersFlow = customerDao.getNonSuppliers()
    private val productsFlow = productDao.getAll()

    private data class Interim(
        val customers: List<Customer>,
        val products: List<Product>,
        val selCust: Int?,
        val items: List<DraftItem>,
        val notes: String
    )

    private val interim = combine(
        customersFlow,
        productsFlow,
        selectedCustomer,
        draftItems,
        notes
    ) { customers, products, selCust, items, n ->
        Interim(customers, products, selCust, items, n)
    }

    val state: StateFlow<UiState> = combine(
        interim,
        status,
        successId,
        editingInvoiceId,
        paidText,
        billDate,
    ) { all: Array<Any?> ->
        val base = all[0] as Interim
        val st = all[1] as Pair<Boolean, String?>
        val sid = all[2] as Int?
        val editId = all[3] as Int?
        val paidStr = all[4] as String
        val dateMs = all[5] as Long
        val subtotal = base.items.sumOf { it.quantity * it.unitPrice }
        val gstAmount = base.items.sumOf { it.quantity * it.unitPrice * (it.gstPercent / 100.0) }
        val total = subtotal + gstAmount
        val paid = paidStr.toDoubleOrNull() ?: 0.0
        val balance = (total - paid).coerceAtLeast(0.0)
        UiState(
            customers = base.customers,
            products = base.products,
            selectedCustomerId = base.selCust,
            items = base.items,
            notes = base.notes,
            subtotal = subtotal,
            gstAmount = gstAmount,
            total = total,
            paid = paid,
            balance = balance,
            loading = st.first,
            error = st.second,
            successInvoiceId = sid,
            editingInvoiceId = editId,
            dateMillis = dateMs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun setCustomer(id: Int?) { selectedCustomer.value = id }
    fun setNotes(n: String) { notes.value = n }
    fun setPaid(text: String) { paidText.value = text }
    fun setBillDate(millis: Long?) { billDate.value = millis ?: System.currentTimeMillis() }

    fun addItem(product: Product, quantity: Double) {
        if (quantity <= 0) return
        val existing = draftItems.value.toMutableList()
        val idx = existing.indexOfFirst { it.product.id == product.id }
        if (idx >= 0) {
            val cur = existing[idx]
            existing[idx] = cur.copy(quantity = cur.quantity + quantity)
        } else {
            existing.add(DraftItem(product, quantity, product.sellingPrice, product.gstPercent))
        }
        draftItems.value = existing
    }

    fun updateItem(productId: Int, quantity: Double?, unitPrice: Double?, gstPercent: Double?) {
        val updated = draftItems.value.map {
            if (it.product.id == productId) {
                it.copy(
                    quantity = quantity ?: it.quantity,
                    unitPrice = unitPrice ?: it.unitPrice,
                    gstPercent = gstPercent ?: it.gstPercent
                )
            } else it
        }
        draftItems.value = updated
    }

    fun removeItem(productId: Int) {
        draftItems.value = draftItems.value.filter { it.product.id != productId }
    }

    fun clearSuccess() { successId.value = null }

    fun clearError() { status.value = false to null }

    fun submit() {
        val cust = selectedCustomer.value ?: run {
            status.value = false to "Select a customer"
            return
        }
        val items = draftItems.value
        if (items.isEmpty()) {
            status.value = false to "Add at least one item"
            return
        }
        status.value = true to null
        viewModelScope.launch {
            val drafts = items.map { BillingRepository.DraftItem(it.product.id, it.quantity, it.unitPrice, it.gstPercent) }
            val editId = editingInvoiceId.value
            val result = if (editId != null) {
                billingRepo.updateInvoice(
                    invoiceId = editId,
                    customerId = cust,
                    date = billDate.value,
                    notes = notes.value.ifBlank { null },
                    items = drafts,
                    paid = (paidText.value.toDoubleOrNull() ?: 0.0)
                ).map { editId }
            } else {
                billingRepo.createInvoice(
                    customerId = cust,
                    date = billDate.value,
                    notes = notes.value.ifBlank { null },
                    items = drafts,
                    paid = (paidText.value.toDoubleOrNull() ?: 0.0)
                )
            }
            status.value = false to result.exceptionOrNull()?.message
            result.onSuccess { id ->
                successId.value = id
                // reset draft
                draftItems.value = emptyList()
                notes.value = ""
                paidText.value = ""
                editingInvoiceId.value = null
            }
        }
    }

    fun loadInvoiceForEdit(invoiceId: Int) {
        viewModelScope.launch {
            status.value = true to null
            try {
                val pair = billingRepo.getInvoiceWithItemsOnce(invoiceId)
                if (pair != null) {
                    val (inv, items) = pair
                    selectedCustomer.value = inv.customerId
                    notes.value = inv.notes ?: ""
                    paidText.value = if (inv.paid == 0.0) "" else inv.paid.toString()
                    billDate.value = inv.date
                    // Ensure products are loaded before mapping items
                    val products = productsFlow.first()
                    val pmap = products.associateBy { it.id }
                    draftItems.value = items.mapNotNull { it ->
                        val p = pmap[it.productId] ?: return@mapNotNull null
                        DraftItem(product = p, quantity = it.quantity, unitPrice = it.unitPrice, gstPercent = it.gstPercent)
                    }
                    editingInvoiceId.value = invoiceId
                }
            } catch (e: Exception) {
                status.value = false to e.message
            } finally {
                status.value = false to null
            }
        }
    }

    fun resetForNewBill() {
        // Clear all draft state to start a fresh bill
        selectedCustomer.value = null
        draftItems.value = emptyList()
        notes.value = ""
        editingInvoiceId.value = null
        paidText.value = ""
        billDate.value = System.currentTimeMillis()
        // Clear any previous error so a fresh bill doesn't show stale errors
        status.value = false to null
    }
}
