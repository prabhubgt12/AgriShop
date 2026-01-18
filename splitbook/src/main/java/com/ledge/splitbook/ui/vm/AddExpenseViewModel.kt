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
        val editingExpenseId: Long? = null,
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
        val shareByAll: Boolean = true,
        val selectedMemberIds: Set<Long> = emptySet(),
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
                    selectedMemberIds = if (_uiState.value.selectedMemberIds.isEmpty()) list.map { it.id }.toSet() else _uiState.value.selectedMemberIds,
                )
                recalcCanSave()
            }
        }
    }

    fun loadForEdit(expenseId: Long) {
        viewModelScope.launch {
            val e = expenseRepo.getExpense(expenseId) ?: return@launch
            // Ensure members are loaded first (load() must be called with groupId before this)
            val members = _uiState.value.members
            val byId = members.associateBy { it.id }
            val splits = expenseRepo.getSplits(expenseId)
            val selIds = splits.map { it.memberId }.toSet()
            val amountsMap = splits.associate { it.memberId to String.format("%.2f", it.value) }

            // Determine if the original expense was shared equally by all
            val allMemberIds = members.map { it.id }.toSet()
            val allMembersIncluded = allMemberIds.isNotEmpty() && selIds == allMemberIds
            val allEqualType = splits.isNotEmpty() && splits.all { it.type == SplitType.EQUAL }
            val shareAll = allMembersIncluded && allEqualType

            // Derive mode from stored splits
            val modeDerived = when {
                splits.any { it.type == SplitType.PERCENTAGE } -> Mode.PERCENT
                splits.any { it.type == SplitType.CUSTOM_AMOUNT } -> Mode.CUSTOM
                else -> Mode.EQUAL
            }
            _uiState.value = _uiState.value.copy(
                editingExpenseId = expenseId,
                groupId = e.groupId,
                amount = String.format("%.2f", e.amount),
                paidById = e.paidByMemberId,
                paidByName = byId[e.paidByMemberId]?.name ?: _uiState.value.paidByName,
                category = e.category,
                note = e.note ?: "",
                date = e.createdAt ?: _uiState.value.date,
                shareByAll = shareAll,
                selectedMemberIds = if (shareAll) allMemberIds else selIds,
                customAmounts = _uiState.value.customAmounts.toMutableMap().apply { putAll(amountsMap) },
                mode = modeDerived
            )
            // If share-by-all, recompute equal distribution so UI reflects equal split
            if (shareAll) {
                distributeEqual(e.amount, allMemberIds.toList())
            }
            // Derive percentages from amounts
            recomputePercentagesFromAmounts()
            recalcCanSave()
        }
    }

    fun updateAmount(value: String) {
        _uiState.value = _uiState.value.copy(amount = value)
        // Auto-populate amounts/percentages when amount changes
        autoDistributeAfterAmountOrSelectionChange()
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

    fun toggleShareByAll(checked: Boolean) {
        val ids = _uiState.value.members.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            shareByAll = checked,
            mode = if (checked) Mode.EQUAL else _uiState.value.mode,
            selectedMemberIds = if (checked) ids else _uiState.value.selectedMemberIds
        )
        autoDistributeAfterAmountOrSelectionChange()
        recalcCanSave()
    }

    fun toggleMemberSelected(memberId: Long, selected: Boolean) {
        val set = _uiState.value.selectedMemberIds.toMutableSet()
        if (selected) set.add(memberId) else set.remove(memberId)
        _uiState.value = _uiState.value.copy(selectedMemberIds = set)
        autoDistributeAfterAmountOrSelectionChange()
        recalcCanSave()
    }

    fun updateCustomAmount(memberId: Long, value: String) {
        val map = _uiState.value.customAmounts.toMutableMap()
        map[memberId] = value
        _uiState.value = _uiState.value.copy(customAmounts = map)
        // When amounts change, recompute percentages to match
        recomputePercentagesFromAmounts()
        recalcCanSave()
    }

    fun updatePercentage(memberId: Long, value: String) {
        val map = _uiState.value.percentages.toMutableMap()
        map[memberId] = value
        _uiState.value = _uiState.value.copy(percentages = map)
        // When percentages change, recompute amounts accordingly
        recomputeAmountsFromPercentages()
        recalcCanSave()
    }

    private fun recalcCanSave() {
        val amt = _uiState.value.amount.toDoubleOrNull()
        var ok = amt != null && amt > 0.0 && _uiState.value.paidById != null && _uiState.value.members.isNotEmpty()
        // Mandatory fields: Description (note) and Date must be non-empty
        ok = ok && _uiState.value.note.isNotBlank() && _uiState.value.date.isNotBlank()
        if (_uiState.value.shareByAll) {
            // equal split across all members
            ok = ok && _uiState.value.members.isNotEmpty()
            _uiState.value = _uiState.value.copy(mode = Mode.EQUAL)
        } else {
            val sel = _uiState.value.selectedMemberIds
            ok = ok && sel.isNotEmpty()
            val anyPct = sel.mapNotNull { _uiState.value.percentages[it]?.toDoubleOrNull() }.sum() > 0.0
            if (anyPct) {
                val sumPct = sel.mapNotNull { _uiState.value.percentages[it]?.toDoubleOrNull() }.sum()
                val sumPctRounded = kotlin.math.round(sumPct * 100) / 100.0
                ok = ok && kotlin.math.abs(sumPctRounded - 100.0) <= 0.01
                _uiState.value = _uiState.value.copy(mode = Mode.PERCENT)
            } else {
                val total = amt ?: 0.0
                val sum = sel.mapNotNull { _uiState.value.customAmounts[it]?.toDoubleOrNull() }.sum()
                val totalRounded = kotlin.math.round(total * 100) / 100.0
                val sumRounded = kotlin.math.round(sum * 100) / 100.0
                ok = ok && kotlin.math.abs(sumRounded - totalRounded) <= 0.01
                _uiState.value = _uiState.value.copy(mode = Mode.CUSTOM)
            }
        }
        _uiState.value = _uiState.value.copy(canSave = ok)
    }

    // Helpers
    private fun selectedIds(): List<Long> = if (_uiState.value.shareByAll) {
        _uiState.value.members.map { it.id }
    } else {
        _uiState.value.selectedMemberIds.toList()
    }

    private fun autoDistributeAfterAmountOrSelectionChange() {
        val total = _uiState.value.amount.toDoubleOrNull() ?: return
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val sumPct = ids.mapNotNull { _uiState.value.percentages[it]?.toDoubleOrNull() }.sum()
        val hasAnyPct = sumPct > 0.0
        val pctValid = kotlin.math.abs(sumPct - 100.0) < 0.01
        if (_uiState.value.shareByAll || !hasAnyPct || !pctValid) {
            // If sharing by all, or no percentages provided yet, or percentages are invalid after selection change,
            // default to equal distribution across the current selection.
            distributeEqual(total, ids)
        } else {
            // Percentages are present and valid, recompute amounts accordingly.
            recomputeAmountsFromPercentages()
        }
    }

    private fun distributeEqual(total: Double, ids: List<Long>) {
        if (ids.isEmpty()) return
        val n = ids.size
        val equal = if (n == 0) 0.0 else (total / n)
        val equalRounded = { d: Double -> kotlin.math.round(d * 100) / 100.0 }
        val amounts = _uiState.value.customAmounts.toMutableMap()
        val pcts = _uiState.value.percentages.toMutableMap()
        ids.forEach { id ->
            amounts[id] = equalRounded(equal).toString()
            pcts[id] = (100.0 / n).toString()
        }
        _uiState.value = _uiState.value.copy(customAmounts = amounts, percentages = pcts)
    }

    private fun recomputeAmountsFromPercentages() {
        val total = _uiState.value.amount.toDoubleOrNull() ?: return
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val amounts = _uiState.value.customAmounts.toMutableMap()
        ids.forEach { id ->
            val pct = _uiState.value.percentages[id]?.toDoubleOrNull() ?: 0.0
            val amt = kotlin.math.round((total * pct / 100.0) * 100) / 100.0
            amounts[id] = amt.toString()
        }
        _uiState.value = _uiState.value.copy(customAmounts = amounts)
    }

    private fun recomputePercentagesFromAmounts() {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val total = _uiState.value.amount.toDoubleOrNull() ?: return
        if (total <= 0.0) return
        val pcts = _uiState.value.percentages.toMutableMap()
        ids.forEach { id ->
            val amt = _uiState.value.customAmounts[id]?.toDoubleOrNull() ?: 0.0
            val pct = kotlin.math.round((amt / total * 100.0) * 100) / 100.0
            pcts[id] = pct.toString()
        }
        _uiState.value = _uiState.value.copy(percentages = pcts)
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
        val memberIds = if (state.shareByAll) state.members.map { it.id } else state.selectedMemberIds.toList()
        if (memberIds.isEmpty() || state.paidById == null) return
        val splits = when (state.mode) {
            Mode.EQUAL -> {
                val shares = SplitLogic.splitEqual(amount, memberIds)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.EQUAL, s.amount) }
            }
            Mode.CUSTOM -> {
                val map = state.customAmounts.filterKeys { it in memberIds.toSet() }.mapNotNull { (id, v) -> v.toDoubleOrNull()?.let { id to it } }.toMap()
                val shares = SplitLogic.splitCustom(amount, map)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.CUSTOM_AMOUNT, s.amount) }
            }
            Mode.PERCENT -> {
                val map = state.percentages.filterKeys { it in memberIds.toSet() }.mapNotNull { (id, v) -> v.toDoubleOrNull()?.let { id to it } }.toMap()
                val shares = SplitLogic.splitPercentage(amount, map)
                shares.map { s -> ExpenseSplitEntity(0, 0, s.memberId, SplitType.PERCENTAGE, s.amount) }
            }
        }
        viewModelScope.launch {
            if (state.editingExpenseId == null) {
                expenseRepo.addExpense(
                    groupId = state.groupId,
                    amount = amount,
                    category = if (state.category.isBlank()) "general" else state.category,
                    paidByMemberId = state.paidById,
                    note = state.note.trim(),
                    createdAt = state.date,
                    splits = splits
                )
            } else {
                expenseRepo.updateExpense(
                    expenseId = state.editingExpenseId,
                    amount = amount,
                    category = if (state.category.isBlank()) "general" else state.category,
                    paidByMemberId = state.paidById,
                    note = state.note.trim(),
                    createdAt = state.date,
                    splits = splits
                )
            }
            onSaved()
        }
    }
}
