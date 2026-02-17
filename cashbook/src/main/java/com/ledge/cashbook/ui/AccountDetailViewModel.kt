package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.data.repo.CashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val repo: CashRepository
) : ViewModel() {

    private val _accountId = MutableStateFlow<Int?>(null)
    val accountId: StateFlow<Int?> = _accountId.asStateFlow()

    val accountName: StateFlow<String> = _accountId.filterNotNull().flatMapLatest { id ->
        flow {
            emit(repo.getAccount(id)?.name ?: "")
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val txns: StateFlow<List<CashTxn>> = _accountId.filterNotNull().flatMapLatest { id ->
        repo.txns(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val balance: StateFlow<Double> = txns.map { list ->
        list.fold(0.0) { acc, t -> acc + if (t.isCredit) t.amount else -t.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun load(id: Int) { _accountId.value = id }

    fun generateRecurringTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.generateDueRecurringTxns()
        }
    }

    fun addTxn(
        date: Long,
        amount: Double,
        isCredit: Boolean,
        note: String?,
        attachmentUri: String?,
        category: String?,
        makeRecurring: Boolean
    ) {
        val id = _accountId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (makeRecurring) {
                repo.addTxnWithMonthlyRecurring(id, date, amount, isCredit, note, attachmentUri, category)
            } else {
                repo.addTxn(id, date, amount, isCredit, note, attachmentUri, category)
            }
        }
    }

    fun deleteTxn(txn: CashTxn) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteTxn(txn)
        }
    }

    fun updateTxn(updated: CashTxn) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateTxn(updated)
        }
    }

    fun stopRecurring(txn: CashTxn) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.stopRecurringFor(txn)
        }
    }

    suspend fun isRecurringActive(recurringId: Int): Boolean = repo.isRecurringActive(recurringId)

    // Selection state for bulk actions
    private val _selection = MutableStateFlow<Set<Int>>(emptySet())
    val selection: StateFlow<Set<Int>> = _selection.asStateFlow()

    fun toggleSelection(id: Int) {
        _selection.update { cur -> if (id in cur) cur - id else cur + id }
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun selectAll(list: List<CashTxn>) { _selection.value = list.map { it.id }.toSet() }

    fun moveSelected(toAccountId: Int) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.moveTxns(ids, toAccountId)
            // Clear selection after move
            _selection.value = emptySet()
        }
    }
}
