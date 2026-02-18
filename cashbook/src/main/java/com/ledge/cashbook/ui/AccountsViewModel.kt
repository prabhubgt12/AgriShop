package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.repo.CashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: CashRepository
) : ViewModel() {
    val accounts: StateFlow<List<CashAccount>> = repo.accounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Aggregates across all accounts
    val totalCredit: StateFlow<Double> = repo.totalCredit()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)
    val totalDebit: StateFlow<Double> = repo.totalDebit()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)
    val dueAccountsCount: StateFlow<Int> = repo.dueAccountsCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun addAccount(name: String) {
        viewModelScope.launch { repo.addAccount(name) }
    }

    fun addAccount(name: String, openBalance: Double?) {
        viewModelScope.launch {
            val id = repo.addAccount(name)
            val ob = openBalance ?: 0.0
            if (ob > 0.0) {
                repo.addTxn(
                    accountId = id,
                    date = System.currentTimeMillis(),
                    amount = ob,
                    isCredit = true,
                    note = "Opening Balance",
                    attachmentUri = null,
                    category = null
                )
            }
        }
    }

    fun txns(accountId: Int) = repo.txns(accountId)

    fun addTxn(
        accountId: Int,
        date: Long,
        amount: Double,
        isCredit: Boolean,
        note: String?,
        attachmentUri: String?,
        category: String?,
        makeRecurring: Boolean
    ) {
        viewModelScope.launch {
            if (makeRecurring) {
                repo.addTxnWithMonthlyRecurring(accountId, date, amount, isCredit, note, attachmentUri, category)
            } else {
                repo.addTxn(accountId, date, amount, isCredit, note, attachmentUri, category)
            }
        }
    }

    fun deleteAccountDeep(accountId: Int) {
        viewModelScope.launch {
            // Delete all transactions then the account itself
            repo.clear(accountId)
            val acc = repo.getAccount(accountId)
            if (acc != null) repo.deleteAccount(acc)
        }
    }

    fun renameAccount(accountId: Int, newName: String) {
        viewModelScope.launch {
            repo.updateAccountName(accountId, newName)
        }
    }
}
