package com.ledge.cashbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.data.local.dao.BusinessProfileDao
import com.ledge.cashbook.data.local.entities.BusinessProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BusinessProfileViewModel @Inject constructor(
    private val dao: BusinessProfileDao
) : ViewModel() {

    val profile: StateFlow<BusinessProfile> = dao.getProfileFlow()
        .map { it ?: BusinessProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BusinessProfile())

    fun save(update: BusinessProfile) {
        viewModelScope.launch {
            dao.upsert(update.copy(id = 1))
        }
    }
}
