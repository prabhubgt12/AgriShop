package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.repo.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    repo: CategoryRepository
) : ViewModel() {
    val categories: StateFlow<List<Category>> = repo.categories()
        .map { it.sortedBy { c -> c.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
