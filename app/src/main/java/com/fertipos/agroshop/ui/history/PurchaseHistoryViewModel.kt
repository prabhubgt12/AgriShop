package com.fertipos.agroshop.ui.history

import androidx.lifecycle.ViewModel
import com.fertipos.agroshop.data.local.dao.PurchaseDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.entities.PurchaseItem
import com.fertipos.agroshop.data.repo.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@HiltViewModel
class PurchaseHistoryViewModel @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val purchaseRepo: PurchaseRepository
) : ViewModel() {

    private val _productFilter = MutableStateFlow<Int?>(null)
    val productFilter: StateFlow<Int?> = _productFilter

    private val fromMillis = MutableStateFlow<Long?>(null)
    private val toMillis = MutableStateFlow<Long?>(null)

    // Expose for edit dialog pickers
    val suppliersFlow = customerDao.getAll()
    val productsFlow = productDao.getAll()

    private val baseRows: Flow<List<Row>> = _productFilter.flatMapLatest { pid ->
        if (pid == null) {
            purchaseDao.getAllPurchasesWithSupplier().map { rows -> rows.map { Row.Purchase(it) } }
        } else {
            purchaseDao.getPurchaseHistoryForProduct(pid).map { rows -> rows.map { Row.ProductHistory(it) } }
        }
    }

    val listState: Flow<List<Row>> = combine(baseRows, fromMillis, toMillis) { rows, from, to ->
        rows.filter { r ->
            val date = when (r) {
                is Row.Purchase -> r.row.date
                is Row.ProductHistory -> r.row.date
            }
            (from == null || date >= from) && (to == null || date <= to)
        }
    }

    fun setProductFilter(productId: Int?) { _productFilter.value = productId }

    fun setDateRange(from: Long?, to: Long?) {
        fromMillis.value = from
        toMillis.value = to
    }

    data class ItemWithProductName(
        val item: PurchaseItem,
        val productName: String?
    )

    suspend fun getItemRowsOnce(purchaseId: Int): List<ItemWithProductName> {
        val items = purchaseDao.getItemsForPurchaseOnce(purchaseId)
        val products = productDao.getAll().first().associateBy { it.id }
        return items.map { ItemWithProductName(it, products[it.productId]?.name) }
    }

    data class FullPurchase(
        val id: Int,
        val date: Long,
        val supplierName: String?,
        val subtotal: Double,
        val gstAmount: Double,
        val total: Double,
        val notes: String?,
        val items: List<ItemWithProductName>
    )

    suspend fun getFullPurchaseOnce(purchaseId: Int): FullPurchase? {
        val purchase = purchaseDao.getPurchaseByIdOnce(purchaseId) ?: return null
        val items = getItemRowsOnce(purchaseId)
        val supplierName = customerDao.getAll().first().associateBy { it.id }[purchase.supplierId]?.name
        return FullPurchase(
            id = purchase.id,
            date = purchase.date,
            supplierName = supplierName,
            subtotal = purchase.subtotal,
            gstAmount = purchase.gstAmount,
            total = purchase.total,
            notes = purchase.notes,
            items = items
        )
    }

    suspend fun updatePurchaseNotes(purchaseId: Int, notes: String?) {
        withContext(Dispatchers.IO) {
            val p = purchaseDao.getPurchaseByIdOnce(purchaseId) ?: return@withContext
            purchaseDao.updatePurchase(p.copy(notes = notes))
        }
    }

    suspend fun updatePurchase(
        purchaseId: Int,
        supplierId: Int,
        date: Long,
        notes: String?,
        items: List<PurchaseRepository.DraftItem>,
        paid: Double
    ): Result<Unit> {
        return purchaseRepo.updatePurchase(purchaseId, supplierId, date, notes, items, paid)
    }

    suspend fun deletePurchase(purchaseId: Int) {
        withContext(Dispatchers.IO) {
            purchaseRepo.deletePurchase(purchaseId)
        }
    }

    sealed class Row {
        data class Purchase(val row: PurchaseDao.PurchaseWithSupplier) : Row()
        data class ProductHistory(val row: PurchaseDao.ProductPurchaseRow) : Row()
    }
}
