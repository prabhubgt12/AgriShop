package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val ui: StateFlow<SettingsRepository.Settings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsRepository.Settings())

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { repo.setDarkMode(enabled) }
    fun setLanguage(value: String) = viewModelScope.launch { repo.setLanguage(value) }
    fun setCurrency(value: String) = viewModelScope.launch { repo.setCurrency(value) }
    fun setRemoveAds(enabled: Boolean) = viewModelScope.launch { repo.setRemoveAds(enabled) }
}
