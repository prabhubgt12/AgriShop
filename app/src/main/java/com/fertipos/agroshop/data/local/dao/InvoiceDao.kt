package com.fertipos.agroshop.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItem>)

    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY date DESC")
    fun getInvoicesForCustomer(customerId: Int): Flow<List<Invoice>>

    @Query("SELECT COUNT(*) FROM invoices WHERE customerId = :customerId")
    suspend fun countInvoicesForCustomer(customerId: Int): Int

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun getItemsForInvoice(invoiceId: Int): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceByIdOnce(id: Int): Invoice?

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getItemsForInvoiceOnce(invoiceId: Int): List<InvoiceItem>

    // Invoices between dates (inclusive) for exporting Sales
    @Query(
        """
        SELECT * FROM invoices
        WHERE date BETWEEN :from AND :to
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getInvoicesBetweenOnce(from: Long, to: Long): List<Invoice>

    @Transaction
    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun clearItemsForInvoice(invoiceId: Int)

    @Query("DELETE FROM invoices WHERE id = :invoiceId")
    suspend fun deleteInvoiceById(invoiceId: Int)

    // Reports: customer-wise totals
    data class CustomerSummaryRow(
        val customerId: Int,
        val customerName: String,
        val invoiceCount: Int,
        val totalAmount: Double,
        val lastInvoiceDate: Long
    )

    @Query(
        """
        SELECT c.id AS customerId, c.name AS customerName,
               COUNT(i.id) AS invoiceCount,
               IFNULL(SUM(i.total), 0) AS totalAmount,
               IFNULL(MAX(i.date), 0) AS lastInvoiceDate
        FROM customers c
        LEFT JOIN invoices i ON i.customerId = c.id
        GROUP BY c.id, c.name
        ORDER BY totalAmount DESC, customerName ASC
        """
    )
    fun getCustomerSummaries(): Flow<List<CustomerSummaryRow>>

    // Export rows for a single customer (includes product name)
    data class ExportRow(
        val invoiceId: Int,
        val date: Long,
        val productName: String?,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double,
        val lineTotal: Double
    )

    @Query(
        """
        SELECT ii.invoiceId AS invoiceId,
               i.date AS date,
               p.name AS productName,
               ii.quantity AS quantity,
               ii.unitPrice AS unitPrice,
               ii.gstPercent AS gstPercent,
               ii.lineTotal AS lineTotal
        FROM invoice_items ii
        JOIN invoices i ON i.id = ii.invoiceId
        LEFT JOIN products p ON p.id = ii.productId
        WHERE i.customerId = :customerId
        ORDER BY i.date DESC, ii.invoiceId ASC
        """
    )
    suspend fun getExportRowsForCustomerOnce(customerId: Int): List<ExportRow>

    // Export rows for all customers (adds customerName)
    data class ExportRowAll(
        val customerName: String?,
        val invoiceId: Int,
        val date: Long,
        val productName: String?,
        val quantity: Double,
        val unitPrice: Double,
        val gstPercent: Double,
        val lineTotal: Double
    )

    @Query(
        """
        SELECT c.name AS customerName,
               ii.invoiceId AS invoiceId,
               i.date AS date,
               p.name AS productName,
               ii.quantity AS quantity,
               ii.unitPrice AS unitPrice,
               ii.gstPercent AS gstPercent,
               ii.lineTotal AS lineTotal
        FROM invoice_items ii
        JOIN invoices i ON i.id = ii.invoiceId
        LEFT JOIN products p ON p.id = ii.productId
        LEFT JOIN customers c ON c.id = i.customerId
        ORDER BY c.name ASC, i.date DESC, ii.invoiceId ASC
        """
    )
    suspend fun getExportRowsAllOnce(): List<ExportRowAll>
}

// Simple date-range totals for P&L
data class InvoiceRangeSummary(
    val subtotal: Double,
    val gst: Double,
    val total: Double
)

@Dao
interface InvoiceSummaryDao {
    @Query(
        """
        SELECT IFNULL(SUM(subtotal),0) AS subtotal,
               IFNULL(SUM(gstAmount),0) AS gst,
               IFNULL(SUM(total),0) AS total
        FROM invoices
        WHERE date BETWEEN :from AND :to
        """
    )
    suspend fun getInvoiceSummaryBetween(from: Long, to: Long): InvoiceRangeSummary
}

// Lines between dates for P&L (per sale line with product purchase price)
data class PLItemRow(
    val date: Long,
    val productId: Int,
    val quantity: Double,
    val unitPrice: Double,
    val purchasePrice: Double
)

@Dao
interface InvoicePlLinesDao {
    @Query(
        """
        SELECT i.date AS date,
               ii.productId AS productId,
               ii.quantity AS quantity,
               ii.unitPrice AS unitPrice,
               IFNULL(p.purchasePrice, 0) AS purchasePrice
        FROM invoice_items ii
        JOIN invoices i ON i.id = ii.invoiceId
        LEFT JOIN products p ON p.id = ii.productId
        WHERE i.date BETWEEN :from AND :to
        ORDER BY i.date ASC, ii.invoiceId ASC
        """
    )
    suspend fun getPlItemRowsBetween(from: Long, to: Long): List<PLItemRow>

    // Aggregated product-wise sales and cost (cost based on current product.purchasePrice)
    data class ProductPlAggRow(
        val productId: Int,
        val productName: String?,
        val qty: Double,
        val salesAmount: Double,
        val costAmount: Double
    )

    @Query(
        """
        SELECT ii.productId AS productId,
               p.name AS productName,
               IFNULL(SUM(ii.quantity), 0) AS qty,
               IFNULL(SUM(ii.quantity * ii.unitPrice), 0) AS salesAmount,
               IFNULL(SUM(ii.quantity * IFNULL(p.purchasePrice, 0)), 0) AS costAmount
        FROM invoice_items ii
        JOIN invoices i ON i.id = ii.invoiceId
        LEFT JOIN products p ON p.id = ii.productId
        WHERE i.date BETWEEN :from AND :to
        GROUP BY ii.productId, p.name
        ORDER BY salesAmount DESC
        """
    )
    suspend fun getProductPlAggBetween(from: Long, to: Long): List<ProductPlAggRow>
}
