package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.ExpenseEntity
import com.ledge.splitbook.data.entity.MemberEntity
import com.ledge.splitbook.data.repo.ExpenseRepository
import com.ledge.splitbook.data.repo.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val memberRepo: MemberRepository,
    private val expenseRepo: ExpenseRepository,
) : ViewModel() {

    data class UiState(
        val groupId: Long = 0L,
        val members: List<MemberEntity> = emptyList(),
        val expenses: List<ExpenseEntity> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(groupId: Long) {
        _ui.value = UiState(groupId = groupId, isLoading = true)
        viewModelScope.launch {
            launch { memberRepo.observeMembers(groupId).collectLatest { _ui.value = _ui.value.copy(members = it) } }
            launch { expenseRepo.observeExpenses(groupId).collectLatest { _ui.value = _ui.value.copy(expenses = it, isLoading = false) } }
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { expenseRepo.deleteExpense(expenseId) }
    }
}
