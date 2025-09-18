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
                    note = "Opening Balance"
                )
            }
        }
    }

    fun txns(accountId: Int) = repo.txns(accountId)

    fun deleteAccountDeep(accountId: Int) {
        viewModelScope.launch {
            // Delete all transactions then the account itself
            repo.clear(accountId)
            val acc = repo.getAccount(accountId)
            if (acc != null) repo.deleteAccount(acc)
        }
    }
}
