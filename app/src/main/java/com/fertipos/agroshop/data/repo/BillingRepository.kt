package com.fertipos.agroshop.data.repo

import androidx.room.withTransaction
import com.fertipos.agroshop.data.local.AppDatabase
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import javax.inject.Inject

class BillingRepository @Inject constructor(
    private val db: AppDatabase,
    private val invoiceDao: InvoiceDao,
    private val productDao: ProductDao
) {
    data class DraftItem(
        val productId: Int,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double
    )

    suspend fun createInvoice(
        customerId: Int,
        notes: String?,
        items: List<DraftItem>,
        paid: Double
    ): Result<Int> {
        if (items.isEmpty()) return Result.failure(IllegalArgumentException("No items"))
        val subtotal = items.sumOf { it.quantity * it.unitPrice }
        val gstAmount = items.sumOf { it.quantity * it.unitPrice * (it.gstPercent / 100.0) }
        val total = subtotal + gstAmount

        return try {
            var invoiceId = 0
            db.withTransaction {
                // Stock availability validation (aggregate per product)
                val requiredByProduct: Map<Int, Double> = items.groupBy { it.productId }
                    .mapValues { entry -> entry.value.sumOf { it.quantity } }
                for ((pid, reqQty) in requiredByProduct) {
                    val prod = productDao.getById(pid) ?: throw IllegalArgumentException("Product $pid not found")
                    if (prod.stockQuantity < reqQty) {
                        throw IllegalStateException("Insufficient stock for ${prod.name}. Available: ${prod.stockQuantity}, required: $reqQty")
                    }
                }

                invoiceId = invoiceDao.insertInvoice(
                    Invoice(
                        customerId = customerId,
                        subtotal = subtotal,
                        gstAmount = gstAmount,
                        total = total,
                        notes = notes,
                        paid = paid
                    )
                ).toInt()

                val toInsert = items.map { d ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = d.productId,
                        quantity = d.quantity,
                        unitPrice = d.unitPrice,
                        gstPercent = d.gstPercent,
                        lineTotal = d.quantity * d.unitPrice * (1 + d.gstPercent / 100.0)
                    )
                }
                invoiceDao.insertItems(toInsert)

                // Deduct stock
                items.forEach { d ->
                    productDao.adjustStock(d.productId, -d.quantity)
                }
            }
            Result.success(invoiceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getInvoiceWithItemsOnce(id: Int): Pair<Invoice, List<InvoiceItem>>? {
        val inv = invoiceDao.getInvoiceByIdOnce(id) ?: return null
        val items = invoiceDao.getItemsForInvoiceOnce(id)
        return inv to items
    }

    suspend fun updateInvoice(
        invoiceId: Int,
        customerId: Int,
        notes: String?,
        items: List<DraftItem>,
        paid: Double
    ): Result<Unit> {
        if (items.isEmpty()) return Result.failure(IllegalArgumentException("No items"))
        val subtotal = items.sumOf { it.quantity * it.unitPrice }
        val gstAmount = items.sumOf { it.quantity * it.unitPrice * (it.gstPercent / 100.0) }
        val total = subtotal + gstAmount

        return try {
            db.withTransaction {
                // Determine stock deltas compared to existing items
                val oldInvoice = invoiceDao.getInvoiceByIdOnce(invoiceId)
                    ?: throw IllegalArgumentException("Invoice not found")
                val oldItems = invoiceDao.getItemsForInvoiceOnce(invoiceId)
                val oldByProduct = oldItems.groupBy { it.productId }.mapValues { e -> e.value.sumOf { it.quantity } }
                val newByProduct = items.groupBy { it.productId }.mapValues { e -> e.value.sumOf { it.quantity } }
                // For every product in union, compute delta = new - old and check stock
                val productIds = (oldByProduct.keys + newByProduct.keys).toSet()
                for (pid in productIds) {
                    val oldQty = oldByProduct[pid] ?: 0.0
                    val newQty = newByProduct[pid] ?: 0.0
                    val delta = newQty - oldQty
                    if (delta > 0) {
                        val prod = productDao.getById(pid) ?: throw IllegalArgumentException("Product $pid not found")
                        if (prod.stockQuantity < delta) {
                            throw IllegalStateException("Insufficient stock for ${prod.name}. Available: ${prod.stockQuantity}, required extra: ${delta}")
                        }
                    }
                }

                // Update invoice header
                invoiceDao.updateInvoice(
                    Invoice(
                        id = invoiceId,
                        customerId = customerId,
                        date = oldInvoice.date,
                        subtotal = subtotal,
                        gstAmount = gstAmount,
                        total = total,
                        notes = notes,
                        paid = paid
                    )
                )

                // Replace items
                invoiceDao.clearItemsForInvoice(invoiceId)
                val toInsert = items.map { d ->
                    InvoiceItem(
                        invoiceId = invoiceId,
                        productId = d.productId,
                        quantity = d.quantity,
                        unitPrice = d.unitPrice,
                        gstPercent = d.gstPercent,
                        lineTotal = d.quantity * d.unitPrice * (1 + d.gstPercent / 100.0)
                    )
                }
                invoiceDao.insertItems(toInsert)

                // Apply stock deltas
                for (pid in productIds) {
                    val oldQty = oldByProduct[pid] ?: 0.0
                    val newQty = newByProduct[pid] ?: 0.0
                    val delta = newQty - oldQty
                    if (delta != 0.0) {
                        productDao.adjustStock(pid, -delta)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
