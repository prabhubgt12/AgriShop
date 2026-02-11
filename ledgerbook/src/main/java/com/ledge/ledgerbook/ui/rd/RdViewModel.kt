package com.ledge.ledgerbook.ui.rd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit
import com.ledge.ledgerbook.data.repo.RdRepository
import com.ledge.ledgerbook.util.RdCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@HiltViewModel
class RdViewModel @Inject constructor(
    private val repo: RdRepository
) : ViewModel() {

    data class RdAccountVM(
        val id: Long,
        val name: String,
        val installmentAmount: Double,
        val annualRatePercent: Double,
        val startDateMillis: Long,
        val tenureMonths: Int,
        val startDateStr: String,
        val totalDeposited: Double,
        val accruedInterest: Double,
        val totalValue: Double
    )

    val accounts: StateFlow<List<RdAccountVM>> = combine(repo.accounts(), repo.allDeposits()) { list, deposits ->
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val now = System.currentTimeMillis()
        val grouped = deposits.groupBy { it.rdAccountId }
        list.map { a ->
            val deps = grouped[a.id].orEmpty()
            val sum = RdCalculator.summaryAsOf(a, deps, now)
            RdAccountVM(
                id = a.id,
                name = a.name,
                installmentAmount = a.installmentAmount,
                annualRatePercent = a.annualRatePercent,
                startDateMillis = a.startDateMillis,
                tenureMonths = a.tenureMonths,
                startDateStr = Instant.ofEpochMilli(a.startDateMillis).atZone(zone).toLocalDate().format(fmt),
                totalDeposited = sum.totalDeposited,
                accruedInterest = sum.accruedInterest,
                totalValue = sum.totalValue
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedAccountId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId

    fun selectAccount(id: Long?) {
        _selectedAccountId.value = id
    }

    val selectedAccount: StateFlow<RdAccount?> = _selectedAccountId
        .flatMapLatest { id ->
            flow {
                val account = withContext(Dispatchers.IO) { if (id == null) null else repo.getAccount(id) }
                emit(account)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedDeposits: StateFlow<List<RdDeposit>> = _selectedAccountId
        .flatMapLatest { id ->
            if (id == null) {
                flow { emit(emptyList()) }
            } else {
                repo.deposits(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedSummary: StateFlow<RdCalculator.Summary?> = combine(selectedAccount, selectedDeposits) { a, d ->
        if (a == null) null else RdCalculator.summaryAsOf(a, d, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun getById(id: Long): RdAccount? = withContext(Dispatchers.IO) {
        repo.getAccount(id)
    }

    fun saveAccount(account: RdAccount, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repo.saveAccount(account)
            onSaved(id)
        }
    }

    fun deleteAccount(account: RdAccount, onDone: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAccount(account)
            onDone?.invoke()
        }
    }

    fun markInstallmentPaid(
        accountId: Long,
        dueDateMillis: Long,
        amount: Double,
        paidDateMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.addDeposit(
                RdDeposit(
                    rdAccountId = accountId,
                    dueDateMillis = dueDateMillis,
                    paidDateMillis = paidDateMillis,
                    amountPaid = amount
                )
            )
        }
    }
}
