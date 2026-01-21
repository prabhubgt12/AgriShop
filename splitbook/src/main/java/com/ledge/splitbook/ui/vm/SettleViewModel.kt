package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.ExpenseEntity
import com.ledge.splitbook.data.entity.ExpenseSplitEntity
import com.ledge.splitbook.data.entity.MemberEntity
import com.ledge.splitbook.data.repo.ExpenseRepository
import com.ledge.splitbook.data.repo.MemberRepository
import com.ledge.splitbook.data.repo.SettlementRepository
import com.ledge.splitbook.data.entity.SettlementEntity
import com.ledge.splitbook.domain.SettlementLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltViewModel
class SettleViewModel @Inject constructor(
    private val memberRepo: MemberRepository,
    private val expenseRepo: ExpenseRepository,
    private val settlementRepo: SettlementRepository,
) : ViewModel() {

    data class UiState(
        val groupId: Long = 0L,
        val members: List<MemberEntity> = emptyList(),
        val expenses: List<ExpenseEntity> = emptyList(),
        val settlements: List<SettlementEntity> = emptyList(),
        val nets: Map<Long, Double> = emptyMap(),
        val transfers: List<SettlementLogic.Transfer> = emptyList(),
        val memberSummaries: List<com.ledge.splitbook.util.MemberSummary> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var observeJob: Job? = null
    private var loadedGroupId: Long? = null

    fun load(groupId: Long) {
        if (loadedGroupId == groupId && observeJob != null) return
        loadedGroupId = groupId
        observeJob?.cancel()
        _ui.value = UiState(groupId = groupId, isLoading = true)
        observeJob = viewModelScope.launch {
            combine(
                memberRepo.observeMembers(groupId).distinctUntilChanged(),
                expenseRepo.observeExpenses(groupId),
                // Also observe split count so we recompute when splits change without expense list changing
                expenseRepo.observeSplitCount(groupId),
                settlementRepo.observeSettlements(groupId)
            ) { members, expenses, splitCount, settlements ->
                // splitCount is only for invalidation; not stored in state
                Triple(members, expenses, settlements)
            }
                .debounce(200)
                .collectLatest { (members, expenses, settlements) ->
                // Mark loading while we recompute the derived snapshot to avoid transient mismatches in UI
                _ui.value = _ui.value.copy(
                    members = members,
                    expenses = expenses,
                    settlements = settlements,
                    isLoading = true
                )
                recompute()
            }
        }
    }

    fun reload() {
        val gid = loadedGroupId ?: return
        observeJob?.cancel()
        observeJob = null
        loadedGroupId = null
        load(gid)
    }

    private fun recompute() {
        val state = _ui.value
        if (state.members.isEmpty()) {
            _ui.value = state.copy(nets = emptyMap(), transfers = emptyList(), isLoading = false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Base nets from expenses and deposits
                val baseNets = computeNets(state.members, state.expenses).toMutableMap()
                // Apply recorded settlements (paid transfers) to adjust nets
                state.settlements.forEach { s ->
                    if (s.status == "completed") {
                        baseNets[s.fromMemberId] = (baseNets[s.fromMemberId] ?: 0.0) + s.amount
                        baseNets[s.toMemberId] = (baseNets[s.toMemberId] ?: 0.0) - s.amount
                    }
                }
                // Normalize after applying settlements to avoid -0.0/epsilon drift
                val normalized = baseNets.mapValues { v ->
                    val r = kotlin.math.round((v.value) * 100.0) / 100.0
                    if (kotlin.math.abs(r) <= 0.01) 0.0 else r
                }
                val summaries = computeMemberSummaries(state.members, state.expenses)
                val transfers = SettlementLogic.settle(normalized)
                val current = _ui.value
                val netsChanged = current.nets != normalized
                val transfersChanged = current.transfers != transfers
                val summariesChanged = current.memberSummaries != summaries
                if (netsChanged || transfersChanged || summariesChanged || current.isLoading) {
                    _ui.value = current.copy(
                        nets = if (netsChanged) normalized else current.nets,
                        transfers = if (transfersChanged) transfers else current.transfers,
                        memberSummaries = if (summariesChanged) summaries else current.memberSummaries,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(isLoading = false, error = t.message)
            }
        }
    }

    private suspend fun computeNets(members: List<MemberEntity>, expenses: List<ExpenseEntity>): Map<Long, Double> {
        if (expenses.isEmpty()) return emptyMap()
        val ids = expenses.map { it.id }
        // Fetch splits per expense id in batches via DAO is encapsulated in repository; for now access through expenseRepo's DAO not exposed.
        // As a simple approach, re-fetch per expense (small data in MVP). We'll extend repo later for batching if needed.
        val splitDaoField = ExpenseRepository::class.java.getDeclaredField("expenseSplitDao").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val splitDao = splitDaoField.get(expenseRepo) as com.ledge.splitbook.data.dao.ExpenseSplitDao
        val nets = members.associate { it.id to 0.0 }.toMutableMap()
        val adminId = members.firstOrNull { it.isAdmin }?.id
        // Apply deposits: credit each depositor, and debit the admin with total deposits
        val totalDeposits = members.filter { !it.isAdmin }.sumOf { it.deposit }
        members.filter { !it.isAdmin }.forEach { m ->
            nets[m.id] = (nets[m.id] ?: 0.0) + (m.deposit)
        }
        if (adminId != null && totalDeposits != 0.0) {
            nets[adminId] = (nets[adminId] ?: 0.0) - totalDeposits
        }
        for (exp in expenses) {
            nets[exp.paidByMemberId] = (nets[exp.paidByMemberId] ?: 0.0) + exp.amount
            val splits: List<ExpenseSplitEntity> = splitDao.getByExpense(exp.id)
            splits.forEach { s ->
                nets[s.memberId] = (nets[s.memberId] ?: 0.0) - s.value
            }
        }
        // Round to 2 decimals to avoid truncation bias and residuals
        return nets.mapValues { kotlin.math.round(it.value * 100.0) / 100.0 }
    }

    private suspend fun computeMemberSummaries(members: List<MemberEntity>, expenses: List<ExpenseEntity>): List<com.ledge.splitbook.util.MemberSummary> {
        if (members.isEmpty()) return emptyList()
        val splitDaoField = ExpenseRepository::class.java.getDeclaredField("expenseSplitDao").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val splitDao = splitDaoField.get(expenseRepo) as com.ledge.splitbook.data.dao.ExpenseSplitDao
        val paid = members.associate { it.id to 0.0 }.toMutableMap()
        val shared = members.associate { it.id to 0.0 }.toMutableMap()
        expenses.forEach { e ->
            paid[e.paidByMemberId] = (paid[e.paidByMemberId] ?: 0.0) + e.amount
            val splits = splitDao.getByExpense(e.id)
            splits.forEach { s ->
                shared[s.memberId] = (shared[s.memberId] ?: 0.0) + s.value
            }
        }
        return members.map { m ->
            val p = (paid[m.id] ?: 0.0)
            val s = (shared[m.id] ?: 0.0)
            com.ledge.splitbook.util.MemberSummary(m.id, p, s, s - p)
        }
    }

    fun markTransferPaid(groupId: Long, fromMemberId: Long, toMemberId: Long, amount: Double) {
        // Optimistic UI update: adjust nets and transfers immediately
        val current = _ui.value
        val nets = current.nets.toMutableMap()
        nets[fromMemberId] = (nets[fromMemberId] ?: 0.0) + amount
        nets[toMemberId] = (nets[toMemberId] ?: 0.0) - amount
        val normalized = nets.mapValues { v ->
            val r = kotlin.math.round((v.value) * 100.0) / 100.0
            if (kotlin.math.abs(r) <= 0.01) 0.0 else r
        }
        val transfersNow = SettlementLogic.settle(normalized)
        _ui.value = current.copy(nets = normalized, transfers = transfersNow)

        // Persist settlement
        viewModelScope.launch(Dispatchers.IO) {
            settlementRepo.markPaid(groupId, fromMemberId, toMemberId, amount)
        }
    }

    fun addMember(name: String, deposit: Double = 0.0, isAdmin: Boolean = false) {
        val gid = _ui.value.groupId
        if (gid == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            memberRepo.addMember(gid, name, deposit, isAdmin)
        }
    }

    fun updateMemberDeposit(memberId: Long, newDeposit: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            memberRepo.updateMemberDeposit(memberId, newDeposit)
        }
    }

    fun removeMember(memberId: Long) {
        val gid = _ui.value.groupId
        viewModelScope.launch(Dispatchers.IO) {
            val ok = memberRepo.removeMemberIfUnused(gid, memberId)
            if (!ok) {
                _ui.value = _ui.value.copy(error = "Cannot remove: used in expenses")
            }
        }
    }

    fun makeAdmin(memberId: Long) {
        val gid = _ui.value.groupId
        if (gid == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            memberRepo.setAdmin(gid, memberId)
        }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}
