package com.ledge.cashbook.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_APP_LOCALE = stringPreferencesKey("app_locale")
    }

    val themeMode: StateFlow<Int> = dataStore.data
        .map { it[KEY_THEME_MODE] ?: MODE_SYSTEM }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MODE_SYSTEM)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_THEME_MODE] = mode }
        }
    }

    val appLocaleTag: StateFlow<String> = dataStore.data
        .map { it[KEY_APP_LOCALE] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setAppLocale(tag: String) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_APP_LOCALE] = tag }
        }
    }
}
