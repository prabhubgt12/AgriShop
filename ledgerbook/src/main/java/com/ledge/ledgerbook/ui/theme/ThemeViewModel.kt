package com.ledge.ledgerbook.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ledge.ledgerbook.data.local.ThemePreferences

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val prefs: ThemePreferences
) : ViewModel() {

    companion object {
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }

    // Backing state from DataStore
    val themeMode: StateFlow<Int> = prefs.themeModeFlow(MODE_SYSTEM)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MODE_SYSTEM)

    // Feature flags
    val groupingEnabled: StateFlow<Boolean> = prefs.groupingEnabledFlow(true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Reminder thresholds (days)
    val overdueDays: StateFlow<Int> = prefs.overdueDaysFlow(365)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 365)

    val dueSoonWindowDays: StateFlow<Int> = prefs.dueSoonWindowDaysFlow(30)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setGroupingEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setGroupingEnabled(enabled) }
    }

    fun setOverdueDays(days: Int) {
        viewModelScope.launch { prefs.setOverdueDays(days) }
    }

    fun setDueSoonWindowDays(days: Int) {
        viewModelScope.launch { prefs.setDueSoonWindowDays(days) }
    }
}
