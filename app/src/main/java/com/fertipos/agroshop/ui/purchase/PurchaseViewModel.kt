package com.fertipos.agroshop.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.repo.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val purchaseRepo: PurchaseRepository,
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
        val suppliers: List<Customer> = emptyList(),
        val products: List<Product> = emptyList(),
        val selectedSupplierId: Int? = null,
        val items: List<DraftItem> = emptyList(),
        val notes: String = "",
        val subtotal: Double = 0.0,
        val gstAmount: Double = 0.0,
        val total: Double = 0.0,
        val paid: Double = 0.0,
        val balance: Double = 0.0,
        val loading: Boolean = false,
        val error: String? = null,
        val successPurchaseId: Int? = null,
        val successEditedId: Int? = null,
        val editingPurchaseId: Int? = null,
        val editingDateMillis: Long? = null,
        val newDateMillis: Long = System.currentTimeMillis()
    )

    private val selectedSupplier = MutableStateFlow<Int?>(null)
    private val draftItems = MutableStateFlow<List<DraftItem>>(emptyList())
    private val notes = MutableStateFlow("")
    private val paidText = MutableStateFlow("")
    private val status = MutableStateFlow(Pair(false, null as String?))
    private val successId = MutableStateFlow<Int?>(null)

    private val suppliersFlow = customerDao.getAll()
    private val productsFlow = productDao.getAll()

    private data class Interim(
        val suppliers: List<Customer>,
        val products: List<Product>,
        val selSupp: Int?,
        val items: List<DraftItem>,
        val notes: String
    )

    private val editingId = MutableStateFlow<Int?>(null)
    private val editingDate = MutableStateFlow<Long?>(null)
    private val editedId = MutableStateFlow<Int?>(null)
    private val newDate = MutableStateFlow(System.currentTimeMillis())

    private val interim = combine(
        suppliersFlow,
        productsFlow,
        selectedSupplier,
        draftItems,
        notes
    ) { suppliers, products, selSupp, items, n ->
        Interim(suppliers, products, selSupp, items, n)
    }

    val state: StateFlow<UiState> = combine(
        interim,
        status,
        successId,
        editedId,
        editingId,
        editingDate,
        paidText,
        newDate,
    ) { all: Array<Any?> ->
        val base = all[0] as Interim
        val st = all[1] as Pair<Boolean, String?>
        val sid = all[2] as Int?
        val eid = all[3] as Int?
        val editing = all[4] as Int?
        val editDate = all[5] as Long?
        val paidStr = all[6] as String
        val newDateMs = all[7] as Long
        val subtotal = base.items.sumOf { it.quantity * it.unitPrice }
        val gstAmount = base.items.sumOf { it.quantity * it.unitPrice * (it.gstPercent / 100.0) }
        val total = subtotal + gstAmount
        val paid = paidStr.toDoubleOrNull() ?: 0.0
        val balance = (total - paid).coerceAtLeast(0.0)
        UiState(
            suppliers = base.suppliers,
            products = base.products,
            selectedSupplierId = base.selSupp,
            items = base.items,
            notes = base.notes,
            subtotal = subtotal,
            gstAmount = gstAmount,
            total = total,
            paid = paid,
            balance = balance,
            loading = st.first,
            error = st.second,
            successPurchaseId = sid,
            successEditedId = eid,
            editingPurchaseId = editing,
            editingDateMillis = editDate,
            newDateMillis = newDateMs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    suspend fun loadForEdit(purchaseId: Int) {
        status.value = true to null
        viewModelScope.launch {
            try {
                val pair = purchaseRepo.getPurchaseWithItemsOnce(purchaseId) ?: throw IllegalArgumentException("Not found")
                val (p, items) = pair
                val prodMap = productsFlow.first().associateBy { it.id }
                selectedSupplier.value = p.supplierId
                notes.value = p.notes ?: ""
                paidText.value = if (p.paid == 0.0) "" else p.paid.toString()
                editingId.value = p.id
                editingDate.value = p.date
                draftItems.value = items.mapNotNull { it ->
                    val prod = prodMap[it.productId] ?: return@mapNotNull null
                    DraftItem(product = prod, quantity = it.quantity, unitPrice = it.unitPrice, gstPercent = it.gstPercent)
                }
                status.value = false to null
            } catch (e: Exception) {
                status.value = false to (e.message ?: "Failed to load purchase")
            }
        }
    }

    fun addItem(product: Product, quantity: Double) {
        if (quantity <= 0) return
        val existing = draftItems.value.toMutableList()
        val idx = existing.indexOfFirst { it.product.id == product.id }
        if (idx >= 0) {
            val cur = existing[idx]
            existing[idx] = cur.copy(quantity = cur.quantity + quantity)
        } else {
            // default purchase unit price is product.purchasePrice
            existing.add(DraftItem(product, quantity, product.purchasePrice, product.gstPercent))
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

    fun clearSuccess() { successId.value = null; editedId.value = null }

    fun resetForNewPurchase() {
        editingId.value = null
        editingDate.value = null
        newDate.value = System.currentTimeMillis()
        draftItems.value = emptyList()
        notes.value = ""
        selectedSupplier.value = null
        paidText.value = ""
        successId.value = null
        editedId.value = null
        status.value = false to null
    }

    fun setSupplier(id: Int?) { selectedSupplier.value = id }
    fun setNotes(n: String) { notes.value = n }
    fun setPaid(text: String) { paidText.value = text }

    fun setEditingDate(millis: Long?) { editingDate.value = millis }
    fun setNewDate(millis: Long?) { newDate.value = millis ?: System.currentTimeMillis() }

    fun submit() {
        val supp = selectedSupplier.value ?: run {
            status.value = false to "Select a supplier"
            return
        }
        val items = draftItems.value
        if (items.isEmpty()) {
            status.value = false to "Add at least one item"
            return
        }
        status.value = true to null
        viewModelScope.launch {
            val drafts = items.map { PurchaseRepository.DraftItem(it.product.id, it.quantity, it.unitPrice, it.gstPercent) }
            val editId = editingId.value
            if (editId == null) {
                val result = purchaseRepo.createPurchase(
                    supplierId = supp,
                    date = newDate.value,
                    notes = notes.value.ifBlank { null },
                    items = drafts,
                    paid = (paidText.value.toDoubleOrNull() ?: 0.0)
                )
                status.value = false to result.exceptionOrNull()?.message
                result.onSuccess { id ->
                    successId.value = id
                    // reset draft
                    draftItems.value = emptyList()
                    notes.value = ""
                    paidText.value = ""
                }
            } else {
                val date = editingDate.value ?: System.currentTimeMillis()
                val result = purchaseRepo.updatePurchase(
                    purchaseId = editId,
                    supplierId = supp,
                    date = date,
                    notes = notes.value.ifBlank { null },
                    items = drafts,
                    paid = (paidText.value.toDoubleOrNull() ?: 0.0)
                )
                status.value = false to result.exceptionOrNull()?.message
                result.onSuccess {
                    editedId.value = editId
                    // keep items and selection; remain in edit mode or clear?
                    // For consistency, leave edit mode but notify success
                }
            }
        }
    }
}
