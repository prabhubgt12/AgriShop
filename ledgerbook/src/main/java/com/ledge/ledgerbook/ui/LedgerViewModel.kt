package com.ledge.ledgerbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import com.ledge.ledgerbook.data.repo.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repo: LedgerRepository
) : ViewModel() {

    // Region: Public State
    data class LedgerItemVM(
        val id: Int,
        val type: String,
        val name: String,
        val principal: Double,
        val accrued: Double,
        val paid: Double,
        val outstanding: Double,
        val total: Double,
        val rate: Double,
        val rateBasis: String,
        val fromDateMillis: Long,
        val dateStr: String
    )

    data class LedgerState(
        val items: List<LedgerItemVM> = emptyList(),
        val totalLend: Double = 0.0,
        val totalLendInterest: Double = 0.0,
        val totalBorrow: Double = 0.0,
        val totalBorrowInterest: Double = 0.0,
        val finalAmount: Double = 0.0
    )

    val state: StateFlow<LedgerState> = repo.entries()
        .map { list ->
            val items = list.map { ewc ->
                val e = ewc.entry
                LedgerItemVM(
                    id = e.id,
                    type = e.type.uppercase(),
                    name = e.name,
                    principal = e.principal,
                    accrued = ewc.accruedInterest,
                    paid = ewc.paid,
                    outstanding = ewc.outstanding,
                    total = ewc.total,
                    rate = e.rateRupees,
                    rateBasis = (e.period ?: "MONTHLY").uppercase(),
                    fromDateMillis = e.fromDate,
                    dateStr = java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(e.fromDate))
                )
            }
            val totalLend = items.filter { it.type == "LEND" }.sumOf { it.principal }
            val totalLendInt = items.filter { it.type == "LEND" }.sumOf { it.accrued }
            val totalBorrow = items.filter { it.type == "BORROW" }.sumOf { it.principal }
            val totalBorrowInt = items.filter { it.type == "BORROW" }.sumOf { it.accrued }
            val finalAmt = (totalLend + totalLendInt) - (totalBorrow + totalBorrowInt)
            LedgerState(
                items = items,
                totalLend = totalLend,
                totalLendInterest = totalLendInt,
                totalBorrow = totalBorrow,
                totalBorrowInterest = totalBorrowInt,
                finalAmount = finalAmt
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerState())

    // Editing entry dialog state (for add or edit)
    private val _editingEntry = MutableStateFlow<LedgerEntry?>(null)
    val editingEntry: StateFlow<LedgerEntry?> = _editingEntry.asStateFlow()

    fun onAddClick() {
        _editingEntry.value = LedgerEntry(
            type = "LEND",
            name = "",
            principal = 0.0,
            interestType = "SIMPLE",
            period = "MONTHLY",
            compoundPeriod = "MONTHLY",
            rateRupees = 0.0,
            fromDate = System.currentTimeMillis(),
            notes = null
        )
    }

    fun onEdit(entry: LedgerEntry) { _editingEntry.value = entry }
    fun beginEdit(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val e = repo.getEntry(id)
            _editingEntry.value = e
        }
    }
    fun clearEdit() { _editingEntry.value = null }

    fun save(entry: LedgerEntry) {
        viewModelScope.launch {
            if (entry.id == 0) repo.addEntry(entry) else repo.updateEntry(entry)
            clearEdit()
        }
    }
    fun saveNew(entry: LedgerEntry) = save(entry)
    fun saveUpdate(entry: LedgerEntry) = save(entry)

    fun delete(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.getEntry(id)?.let { repo.deleteEntry(it) }
        }
    }

    // Partial payment helpers
    suspend fun computeAt(entryId: Int, atMillis: Long) = repo.computeAt(entryId, atMillis)
    suspend fun computeAtFromSnapshot(entryId: Int, atMillis: Long, prevPrincipal: Double, prevFromDate: Long) =
        repo.computeAtFromSnapshot(entryId, atMillis, prevPrincipal, prevFromDate)
    fun applyPartial(entryId: Int, amount: Double, dateMillis: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.applyPartialPayment(entryId, amount, dateMillis) }
    }

    fun applyPartialWithMeta(entryId: Int, amount: Double, dateMillis: Long, userNote: String?, attachmentUri: String?) {
        viewModelScope.launch(Dispatchers.IO) { repo.applyPartialWithMeta(entryId, amount, dateMillis, userNote, attachmentUri) }
    }

    fun deleteLatestPayment(entryId: Int, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteLatestPayment(entryId)
            onDone?.invoke()
        }
    }

    fun editLatestPayment(entryId: Int, newAmount: Double, newDateMillis: Long, userNote: String?, attachmentUri: String?, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.editLatestPayment(entryId, newAmount, newDateMillis, userNote, attachmentUri)
            onDone?.invoke()
        }
    }

    // Payment history dialog state
    private val _paymentsEntryId = MutableStateFlow<Int?>(null)
    val paymentsEntryId: StateFlow<Int?> = _paymentsEntryId.asStateFlow()
    private val _paymentsForViewing = MutableStateFlow<List<LedgerPayment>>(emptyList())
    val paymentsForViewing: StateFlow<List<LedgerPayment>> = _paymentsForViewing.asStateFlow()

    fun openPayments(entryId: Int) {
        _paymentsEntryId.value = entryId
        viewModelScope.launch(Dispatchers.IO) {
            _paymentsForViewing.value = repo.getPaymentsFor(entryId)
        }
    }
    fun closePayments() {
        _paymentsEntryId.value = null
        _paymentsForViewing.value = emptyList()
    }
}
