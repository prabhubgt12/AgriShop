package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.ExpenseSplitEntity
import com.ledge.splitbook.data.entity.MemberEntity
import com.ledge.splitbook.data.entity.SplitType
import com.ledge.splitbook.data.repo.ExpenseRepository
import com.ledge.splitbook.data.repo.MemberRepository
import com.ledge.splitbook.domain.SplitLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val memberRepo: MemberRepository,
    private val expenseRepo: ExpenseRepository,
) : ViewModel() {

    enum class Mode { EQUAL, CUSTOM, PERCENT }

    data class UiState(
        val groupId: Long = 0L,
        val members: List<MemberEntity> = emptyList(),
        val amount: String = "",
        val paidById: Long? = null,
        val paidByName: String = "",
        val category: String = "",
        val note: String = "",
        val date: String = java.time.LocalDate.now().toString(),
        val mode: Mode = Mode.EQUAL,
        val customAmounts: Map<Long, String> = emptyMap(),
        val percentages: Map<Long, String> = emptyMap(),
        val canSave: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: UiState get() = _uiState.value
    val uiFlow: StateFlow<UiState> = _uiState.asStateFlow()

    fun load(groupId: Long) {
        _uiState.value = _uiState.value.copy(groupId = groupId)
        viewModelScope.launch {
            memberRepo.observeMembers(groupId).collect { list ->
                val payerId = _uiState.value.paidById ?: list.firstOrNull()?.id
                val payerName = list.firstOrNull { it.id == payerId }?.name ?: ""
                _uiState.value = _uiState.value.copy(
                    members = list,
                    paidById = payerId,
                    paidByName = payerName,
                    customAmounts = list.associate { it.id to (_uiState.value.customAmounts[it.id] ?: "") },
                    percentages = list.associate { it.id to (_uiState.value.percentages[it.id] ?: "") },
                )
                recalcCanSave()
            }
        }
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amount = value)
        recalcCanSave()
    }

    fun updateCategory(value: String) {
        _uiState.value = _uiState.value.copy(category = value)
        recalcCanSave()
    }

    fun updateNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value)
    }

    fun updateDate(value: String) {
        _uiState.value = _uiState.value.copy(date = value)
    }

    fun selectPayer(memberId: Long) {
        val name = _uiState.value.members.firstOrNull { it.id == memberId }?.name ?: ""
        _uiState.value = _uiState.value.copy(paidById = memberId, paidByName = name)
        recalcCanSave()
    }

    fun setMode(mode: Mode) {
        _uiState.value = _uiState.value.copy(mode = mode)
        recalcCanSave()
    }

    fun updateCustomAmount(memberId: Long, value: String) {
        val map = _uiState.value.customAmounts.toMutableMap()
        map[memberId] = value
        _uiState.value = _uiState.value.copy(customAmounts = map)
        recalcCanSave()
    }

    fun updatePercentage(memberId: Long, value: String) {
        val map = _uiState.value.percentages.toMutableMap()
        map[memberId] = value
        _uiState.value = _uiState.value.copy(percentages = map)
        recalcCanSave()
    }

    private fun recalcCanSave() {
        val amt = _uiState.value.amount.toDoubleOrNull()
        var ok = amt != null && amt > 0.0 && _uiState.value.paidById != null && _uiState.value.members.isNotEmpty()
        when (_uiState.value.mode) {
            Mode.EQUAL -> { /* nothing extra */ }
            Mode.CUSTOM -> {
                val total = amt ?: 0.0
                val sum = _uiState.value.customAmounts.mapNotNull { it.value.toDoubleOrNull() }.sum()
                ok = ok && kotlin.math.abs(sum - total) < 0.01
            }
            Mode.PERCENT -> {
                val sumPct = _uiState.value.percentages.mapNotNull { it.value.toDoubleOrNull() }.sum()
                ok = ok && kotlin.math.abs(sumPct - 100.0) < 0.01
            }
        }
        _uiState.value = _uiState.value.copy(canSave = ok)
    }

    fun addSampleMembers() {
        val gid = _uiState.value.groupId
        if (gid == 0L) return
        viewModelScope.launch {
            memberRepo.addMember(gid, "You")
            memberRepo.addMember(gid, "Friend")
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val amount = state.amount.toDoubleOrNull() ?: return
        val memberIds = state.members.map { it.id }
        if (memberIds.isEmpty() || state.paidById == null) return
        val splits = when (state.mode) {
            Mode.EQUAL -> {
                val shares = SplitLogic.splitEqual(amount, memberIds)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.EQUAL, s.amount) }
            }
            Mode.CUSTOM -> {
                val map = state.customAmounts.mapNotNull { (id, v) -> v.toDoubleOrNull()?.let { id to it } }.toMap()
                val shares = SplitLogic.splitCustom(amount, map)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.CUSTOM_AMOUNT, s.amount) }
            }
            Mode.PERCENT -> {
                val map = state.percentages.mapNotNull { (id, v) -> v.toDoubleOrNull()?.let { id to it } }.toMap()
                val shares = SplitLogic.splitPercentage(amount, map)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.PERCENTAGE, s.amount) }
            }
        }
        viewModelScope.launch {
            expenseRepo.addExpense(
                groupId = state.groupId,
                amount = amount,
                category = if (state.category.isBlank()) "general" else state.category,
                paidByMemberId = state.paidById,
                note = state.note.ifBlank { null },
                createdAt = state.date,
                splits = splits
            )
            onSaved()
        }
    }
}
