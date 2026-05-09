package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.dao.RecentTxnRow
import com.ledge.cashbook.data.repo.CashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: CashRepository
) : ViewModel() {
    private val todayFrom: Long = CashRepository.truncateToDay(System.currentTimeMillis())
    private val todayTo: Long = todayFrom + 86_400_000L

    val recentTxns: StateFlow<List<RecentTxnRow>> = repo.recentTxns(10)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayCredit: StateFlow<Double> = repo.todayCredit(todayFrom, todayTo)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val todayDebit: StateFlow<Double> = repo.todayDebit(todayFrom, todayTo)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val todayTotals: StateFlow<Pair<Double, Double>> = combine(todayCredit, todayDebit) { c, d -> c to d }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0 to 0.0)
}
