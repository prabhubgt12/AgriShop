package com.fertipos.agroshop.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.repo.LedgerEntryWithComputed
import com.fertipos.agroshop.data.repo.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.fertipos.agroshop.data.local.entities.LedgerEntry
import kotlinx.coroutines.flow.MutableStateFlow as KMutableStateFlow
import com.fertipos.agroshop.data.local.entities.LedgerPayment

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repo: LedgerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LedgerState())
    val state: StateFlow<LedgerState> = _state.asStateFlow()

    // Editing state
    val editingEntry: KMutableStateFlow<LedgerEntry?> = KMutableStateFlow(null)

    init {
        // Daily refresh ticker to recompute interest without DB changes
        val refresh = MutableStateFlow(0)
        viewModelScope.launch {
            while (true) {
                delay(86_400_000L)
                refresh.value = refresh.value + 1
            }
        }

        refresh
            .flatMapLatest { repo.entries() }
            .onEach { list ->
                val vms = list.map { it.toItemVM() }
                val lend = list.filter { it.entry.type.equals("LEND", ignoreCase = true) }
                val borrow = list.filter { it.entry.type.equals("BORROW", ignoreCase = true) }
                // Overview totals should reflect PRINCIPAL only (exclude interest)
                val totalLend = lend.sumOf { it.entry.principal }
                val totalLendInt = lend.sumOf { it.accruedInterest }
                val totalBorrow = borrow.sumOf { it.entry.principal }
                val totalBorrowInt = borrow.sumOf { it.accruedInterest }
                // Final amount should include INTEREST as well
                val finalAmount = (totalLend + totalLendInt) - (totalBorrow + totalBorrowInt)
                _state.value = _state.value.copy(
                    items = vms,
                    totalLend = totalLend,
                    totalLendInterest = totalLendInt,
                    totalBorrow = totalBorrow,
                    totalBorrowInterest = totalBorrowInt,
                    finalAmount = finalAmount
                )
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        // local filter for now
        val all = repo.entries() // not ideal to resubscribe; kept simple for scaffolding
    }

    fun onAddNew() {
        // TODO: navigate to Add screen when implemented
    }

    fun saveNew(entry: LedgerEntry) {
        viewModelScope.launch {
            repo.addEntry(entry)
        }
    }

    fun beginEdit(id: Int) {
        viewModelScope.launch {
            editingEntry.value = repo.getEntry(id)
        }
    }

    fun clearEdit() { editingEntry.value = null }

    fun saveUpdate(entry: LedgerEntry) {
        viewModelScope.launch { repo.updateEntry(entry) }
    }

    fun applyPartial(entryId: Int, amount: Double) {
        viewModelScope.launch { repo.applyPartialPayment(entryId, amount) }
    }

    fun applyPartial(entryId: Int, amount: Double, atMillis: Long) {
        viewModelScope.launch { repo.applyPartialPayment(entryId, amount, atMillis) }
    }

    suspend fun computeAt(entryId: Int, atMillis: Long): Triple<Double, Double, Double> =
        repo.computeAt(entryId, atMillis)

    fun addPayment(entryId: Int, amount: Double, date: Long, note: String?) {
        viewModelScope.launch { repo.addPayment(entryId, amount, date, note) }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            repo.getEntry(id)?.let { repo.deleteEntry(it) }
        }
    }

    // Payments viewing state
    val paymentsForViewing: KMutableStateFlow<List<LedgerPayment>> = KMutableStateFlow(emptyList())
    val paymentsEntryId: KMutableStateFlow<Int?> = KMutableStateFlow(null)

    fun openPayments(id: Int) {
        viewModelScope.launch {
            paymentsEntryId.value = id
            paymentsForViewing.value = repo.getPaymentsFor(id)
        }
    }

    fun closePayments() {
        paymentsEntryId.value = null
        paymentsForViewing.value = emptyList()
    }
}

private fun LedgerEntryWithComputed.toItemVM(): LedgerItemVM =
    LedgerItemVM(
        id = entry.id,
        name = entry.name,
        type = entry.type.uppercase(),
        principal = entry.principal,
        rate = entry.rateRupees,
        rateBasis = (entry.period ?: "MONTHLY").uppercase(),
        fromDateMillis = entry.fromDate,
        dateStr = java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(entry.fromDate)),
        accrued = accruedInterest,
        total = total,
        outstanding = outstanding
    )

data class LedgerState(
    val query: String = "",
    val items: List<LedgerItemVM> = emptyList(),
    val totalLend: Double = 0.0,
    val totalLendInterest: Double = 0.0,
    val totalBorrow: Double = 0.0,
    val totalBorrowInterest: Double = 0.0,
    val finalAmount: Double = 0.0,
)

data class LedgerItemVM(
    val id: Int,
    val name: String,
    val type: String,
    val principal: Double,
    val rate: Double,
    val rateBasis: String,
    val fromDateMillis: Long,
    val dateStr: String,
    val accrued: Double,
    val total: Double,
    val outstanding: Double,
)
