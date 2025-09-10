package com.fertipos.agroshop.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fertipos.agroshop.data.local.entities.Purchase
import com.fertipos.agroshop.data.local.entities.PurchaseItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Update
    suspend fun updatePurchase(purchase: Purchase)

    @Delete
    suspend fun deletePurchase(purchase: Purchase)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PurchaseItem>)

    @Query("SELECT * FROM purchases ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY date DESC")
    fun getPurchasesForSupplier(supplierId: Int): Flow<List<Purchase>>

    @Query("SELECT COUNT(*) FROM purchases WHERE supplierId = :supplierId")
    suspend fun countPurchasesForSupplier(supplierId: Int): Int

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseByIdOnce(id: Int): Purchase?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getItemsForPurchaseOnce(purchaseId: Int): List<PurchaseItem>

    // Purchases between dates (inclusive) for exporting Purchases
    @Query(
        """
        SELECT * FROM purchases
        WHERE date BETWEEN :from AND :to
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getPurchasesBetweenOnce(from: Long, to: Long): List<Purchase>

    @Transaction
    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun clearItemsForPurchase(purchaseId: Int)

    // Purchases with supplier name for list screen
    data class PurchaseWithSupplier(
        val id: Int,
        val date: Long,
        val supplierName: String?,
        val subtotal: Double,
        val gstAmount: Double,
        val total: Double
    )

    @Query(
        """
        SELECT p.id AS id, p.date AS date, c.name AS supplierName,
               p.subtotal AS subtotal, p.gstAmount AS gstAmount, p.total AS total
        FROM purchases p
        LEFT JOIN customers c ON c.id = p.supplierId
        ORDER BY p.date DESC
        """
    )
    fun getAllPurchasesWithSupplier(): Flow<List<PurchaseWithSupplier>>

    // Product-wise purchase history rows
    data class ProductPurchaseRow(
        val purchaseId: Int,
        val date: Long,
        val supplierName: String?,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double,
        val lineTotal: Double
    )

    @Query(
        """
        SELECT pi.purchaseId AS purchaseId,
               p.date AS date,
               c.name AS supplierName,
               pi.quantity AS quantity,
               pi.unitPrice AS unitPrice,
               pi.gstPercent AS gstPercent,
               pi.lineTotal AS lineTotal
        FROM purchase_items pi
        JOIN purchases p ON p.id = pi.purchaseId
        LEFT JOIN customers c ON c.id = p.supplierId
        WHERE pi.productId = :productId
        ORDER BY p.date DESC, pi.purchaseId ASC
        """
    )
    fun getPurchaseHistoryForProduct(productId: Int): Flow<List<ProductPurchaseRow>>
}

// Simple date-range totals for P&L (Purchases)
data class PurchaseRangeSummary(
    val subtotal: Double,
    val gst: Double,
    val total: Double
)

@Dao
interface PurchaseSummaryDao {
    @Query(
        """
        SELECT IFNULL(SUM(subtotal),0) AS subtotal,
               IFNULL(SUM(gstAmount),0) AS gst,
               IFNULL(SUM(total),0) AS total
        FROM purchases
        WHERE date BETWEEN :from AND :to
        """
    )
    suspend fun getPurchaseSummaryBetween(from: Long, to: Long): PurchaseRangeSummary
}
