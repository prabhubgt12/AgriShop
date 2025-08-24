package com.fertipos.agroshop.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.dao.CompanyProfileDao
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CompanyProfileViewModel @Inject constructor(
    private val dao: CompanyProfileDao
) : ViewModel() {

    val profile: StateFlow<CompanyProfile> = dao.getProfileFlow()
        .map { it ?: CompanyProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanyProfile())

    fun save(update: CompanyProfile) {
        viewModelScope.launch {
            dao.upsert(update.copy(id = 1))
        }
    }
}
