package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.CategoryEntity
import com.ledge.splitbook.data.repo.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: CategoryRepository
) : ViewModel() {

    private val defaults = listOf("Hotel","Food","Toll","Parking","Shopping","Fuel","Other")

    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Seed defaults if empty
            repo.observeCategories().collect { list ->
                if (list.isEmpty()) {
                    defaults.forEach { name -> repo.add(name) }
                }
            }
        }
    }

    suspend fun add(name: String) { repo.add(name) }
    suspend fun rename(id: Long, newName: String) { repo.rename(id, newName) }
    suspend fun delete(id: Long) { repo.delete(id) }
}
