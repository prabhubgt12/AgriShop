package com.fertipos.agroshop.data.repo

import androidx.room.withTransaction
import com.fertipos.agroshop.data.local.AppDatabase
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.dao.PurchaseDao
import com.fertipos.agroshop.data.local.entities.Purchase
import com.fertipos.agroshop.data.local.entities.PurchaseItem
import javax.inject.Inject

class PurchaseRepository @Inject constructor(
    private val db: AppDatabase,
    private val purchaseDao: PurchaseDao,
    private val productDao: ProductDao
) {
    data class DraftItem(
        val productId: Int,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double
    )

    suspend fun createPurchase(
        supplierId: Int,
        notes: String?,
        items: List<DraftItem>,
        paid: Double
    ): Result<Int> {
        if (items.isEmpty()) return Result.failure(IllegalArgumentException("No items"))
        val subtotal = items.sumOf { it.quantity * it.unitPrice }
        val gstAmount = items.sumOf { it.quantity * it.unitPrice * (it.gstPercent / 100.0) }
        val total = subtotal + gstAmount

        return try {
            var purchaseId = 0
            db.withTransaction {
                purchaseId = purchaseDao.insertPurchase(
                    Purchase(
                        supplierId = supplierId,
                        subtotal = subtotal,
                        gstAmount = gstAmount,
                        total = total,
                        notes = notes,
                        paid = paid
                    )
                ).toInt()

                val toInsert = items.map { d ->
                    PurchaseItem(
                        purchaseId = purchaseId,
                        productId = d.productId,
                        quantity = d.quantity,
                        unitPrice = d.unitPrice,
                        gstPercent = d.gstPercent,
                        lineTotal = d.quantity * d.unitPrice * (1 + d.gstPercent / 100.0)
                    )
                }
                purchaseDao.insertItems(toInsert)

                // Increase stock and update purchase price (weighted average)
                items.forEach { d ->
                    val prod = productDao.getById(d.productId)
                        ?: throw IllegalArgumentException("Product ${'$'}{d.productId} not found")
                    val oldQty = prod.stockQuantity
                    val newQty = d.quantity
                    val combinedQty = oldQty + newQty
                    val newPurchasePrice = if (combinedQty > 0) {
                        ((oldQty * prod.purchasePrice) + (newQty * d.unitPrice)) / combinedQty
                    } else prod.purchasePrice

                    // update product with new purchase price
                    productDao.update(
                        prod.copy(
                            purchasePrice = newPurchasePrice,
                            stockQuantity = prod.stockQuantity // stock adjusted separately
                        )
                    )
                    // add stock
                    productDao.adjustStock(d.productId, newQty)
                }
            }
            Result.success(purchaseId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePurchase(
        purchaseId: Int,
        supplierId: Int,
        date: Long,
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
                val existing = purchaseDao.getPurchaseByIdOnce(purchaseId)
                    ?: throw IllegalArgumentException("Purchase ${'$'}purchaseId not found")
                val oldItems = purchaseDao.getItemsForPurchaseOnce(purchaseId)

                // Update purchase header
                purchaseDao.updatePurchase(
                    existing.copy(
                        supplierId = supplierId,
                        date = date,
                        subtotal = subtotal,
                        gstAmount = gstAmount,
                        total = total,
                        notes = notes,
                        paid = paid
                    )
                )

                // Replace items
                purchaseDao.clearItemsForPurchase(purchaseId)
                val toInsert = items.map { d ->
                    PurchaseItem(
                        purchaseId = purchaseId,
                        productId = d.productId,
                        quantity = d.quantity,
                        unitPrice = d.unitPrice,
                        gstPercent = d.gstPercent,
                        lineTotal = d.quantity * d.unitPrice * (1 + d.gstPercent / 100.0)
                    )
                }
                purchaseDao.insertItems(toInsert)

                // Adjust stock by delta (new - old)
                val oldMap = oldItems.groupBy { it.productId }.mapValues { it.value.sumOf { i -> i.quantity } }
                val newMap = items.groupBy { it.productId }.mapValues { it.value.sumOf { i -> i.quantity } }
                val productIds = (oldMap.keys + newMap.keys).toSet()
                productIds.forEach { pid ->
                    val delta = (newMap[pid] ?: 0.0) - (oldMap[pid] ?: 0.0)
                    if (delta != 0.0) productDao.adjustStock(pid, delta)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPurchaseWithItemsOnce(purchaseId: Int): Pair<Purchase, List<PurchaseItem>>? {
        val p = purchaseDao.getPurchaseByIdOnce(purchaseId) ?: return null
        val items = purchaseDao.getItemsForPurchaseOnce(purchaseId)
        return p to items
    }
}
