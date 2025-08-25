package com.fertipos.agroshop.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.InvoiceSummaryDao
import com.fertipos.agroshop.data.local.dao.InvoicePlLinesDao
import com.fertipos.agroshop.data.local.dao.PurchaseSummaryDao
import com.fertipos.agroshop.data.local.dao.PurchaseDao
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
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val invoiceSummaryDao: InvoiceSummaryDao,
    private val invoicePlLinesDao: InvoicePlLinesDao,
    private val purchaseSummaryDao: PurchaseSummaryDao,
    private val purchaseDao: PurchaseDao
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

    // Range helpers for exports
    suspend fun getInvoicesBetween(from: Long, to: Long): List<com.fertipos.agroshop.data.local.entities.Invoice> =
        withContext(Dispatchers.IO) { invoiceDao.getInvoicesBetweenOnce(from, to) }

    suspend fun getPurchasesBetween(from: Long, to: Long): List<com.fertipos.agroshop.data.local.entities.Purchase> =
        withContext(Dispatchers.IO) { purchaseDao.getPurchasesBetweenOnce(from, to) }

    // Profit & Loss
    data class PLResult(
        val from: Long,
        val to: Long,
        val salesSubtotal: Double,
        val salesGst: Double,
        val salesTotal: Double,
        val purchasesSubtotal: Double,
        val purchasesGst: Double,
        val purchasesTotal: Double,
        val grossProfit: Double, // salesSubtotal - purchasesSubtotal
        val netAmount: Double    // salesTotal - purchasesTotal
    )

    enum class CostingMethod { FIFO, AVERAGE }

    // Compute P&L from invoice lines only (sales side). GST is excluded from P&L.
    // Costing method switch is wired for future inventory-layer support; currently all
    // methods use product.purchasePrice (acts like moving average approximation).
    suspend fun computeProfitAndLoss(from: Long, to: Long, method: CostingMethod): PLResult = withContext(Dispatchers.IO) {
        val lines = invoicePlLinesDao.getPlItemRowsBetween(from, to)

        val costPerLine: (com.fertipos.agroshop.data.local.dao.PLItemRow) -> Double = when (method) {
            CostingMethod.FIFO -> { row -> row.purchasePrice } // TODO: replace with FIFO layer lookup
            CostingMethod.AVERAGE -> { row -> row.purchasePrice } // uses product.purchasePrice as moving average
        }

        val salesSubtotal = lines.sumOf { it.quantity * it.unitPrice }
        val purchasesSubtotal = lines.sumOf { it.quantity * costPerLine(it) }
        val grossProfit = salesSubtotal - purchasesSubtotal

        PLResult(
            from = from,
            to = to,
            salesSubtotal = salesSubtotal,
            salesGst = 0.0,
            salesTotal = salesSubtotal,
            purchasesSubtotal = purchasesSubtotal,
            purchasesGst = 0.0,
            purchasesTotal = purchasesSubtotal,
            grossProfit = grossProfit,
            netAmount = grossProfit
        )
    }

    // Product-wise P/L rows
    data class ProductPLRow(
        val productId: Int,
        val productName: String,
        val quantity: Double,
        val salesAmount: Double,
        val costAmount: Double,
        val profit: Double
    )

    suspend fun computeProductWisePl(from: Long, to: Long): List<ProductPLRow> = withContext(Dispatchers.IO) {
        val rows = invoicePlLinesDao.getProductPlAggBetween(from, to)
        rows.map {
            val sales = it.salesAmount
            val cost = it.costAmount
            ProductPLRow(
                productId = it.productId,
                productName = it.productName ?: "Product #${it.productId}",
                quantity = it.qty,
                salesAmount = sales,
                costAmount = cost,
                profit = sales - cost
            )
        }
    }

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
