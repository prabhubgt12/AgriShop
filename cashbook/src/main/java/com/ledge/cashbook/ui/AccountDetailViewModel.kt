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

    fun addTxn(date: Long, amount: Double, isCredit: Boolean, note: String?) {
        val id = _accountId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.addTxn(id, date, amount, isCredit, note)
        }
    }
}
