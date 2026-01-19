package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.repo.GroupRepository
import com.ledge.splitbook.data.repo.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupsRepo: GroupRepository,
    private val expenseRepo: ExpenseRepository,
) : ViewModel() {

    val groups = groupsRepo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Memoize per-group total flows so collectors receive the latest value without
    // resetting to 0.0 on recomposition (prevents flicker when navigating back).
    private val totalFlows = mutableMapOf<Long, kotlinx.coroutines.flow.StateFlow<Double>>()

    fun totalForGroup(groupId: Long): kotlinx.coroutines.flow.StateFlow<Double> =
        totalFlows.getOrPut(groupId) {
            expenseRepo.observeExpenses(groupId)
                .map { list -> list.sumOf { it.amount } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
        }

    fun createGroup(name: String, icon: String?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = groupsRepo.createGroup(name = name, icon = icon)
            onCreated(id)
        }
    }

    fun renameGroup(id: Long, newName: String) {
        viewModelScope.launch { groupsRepo.renameGroup(id, newName) }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch { groupsRepo.deleteGroup(id) }
    }
}
