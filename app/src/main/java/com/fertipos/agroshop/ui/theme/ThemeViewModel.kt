package com.fertipos.agroshop.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fertipos.agroshop.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode(val code: Int) {
    SYSTEM(0), LIGHT(1), DARK(2);
    companion object {
        fun from(code: Int): ThemeMode = when (code) {
            1 -> LIGHT
            2 -> DARK
            else -> SYSTEM
        }
    }
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val userPrefs: UserPreferences
) : ViewModel() {

    val themeMode = userPrefs.themeModeFlow(defaultMode = ThemeMode.SYSTEM.code)
        .map { ThemeMode.from(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            userPrefs.setThemeMode(mode.code)
        }
    }
}
