package com.ledge.ledgerbook.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.ledgerbook.data.local.entities.LoanProfile
import com.ledge.ledgerbook.data.repo.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val repo: LoanRepository
) : ViewModel() {

    val loans: StateFlow<List<LoanProfile>> = repo.observeAll()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(profile: LoanProfile, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repo.save(profile)
            onSaved(id)
        }
    }

    suspend fun getById(id: Long): LoanProfile? = repo.getById(id)
}
