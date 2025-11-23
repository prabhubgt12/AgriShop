package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.repo.CategoryRepository
import com.ledge.cashbook.data.local.entities.CategoryKeyword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repo: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addOrUpdate(name: String, keywordsCsv: String, color: Int? = null, id: Long? = null) {
        val keywords = keywordsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            repo.upsertCategoryWithKeywords(id = id, name = name, color = color, keywords = keywords)
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            repo.deleteCategoryCascade(category.id)
        }
    }

    fun keywordsFor(categoryId: Long): kotlinx.coroutines.flow.Flow<List<CategoryKeyword>> = repo.keywords(categoryId)
}
