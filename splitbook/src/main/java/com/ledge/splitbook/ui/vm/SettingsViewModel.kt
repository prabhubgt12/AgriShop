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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val ui: StateFlow<SettingsRepository.Settings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsRepository.Settings())

    init {
        // Keep global currency formatter and app locale synced even if Settings screen isn't opened
        viewModelScope.launch {
            repo.settings.collect { s ->
                com.ledge.splitbook.util.CurrencyFormatter.setConfig(s.currency, s.showCurrencySymbol)
                // Apply app language using AppCompatDelegate locales
                val tag = when (s.language.lowercase()) {
                    "hindi" -> "hi"
                    "kannada" -> "kn"
                    "tamil" -> "ta"
                    "telugu" -> "te"
                    "english" -> "en"
                    else -> "en"
                }
                val locales = LocaleListCompat.forLanguageTags(tag)
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { repo.setDarkMode(enabled) }
    fun setLanguage(value: String) = viewModelScope.launch { repo.setLanguage(value) }
    fun setCurrency(value: String) = viewModelScope.launch { repo.setCurrency(value) }
    fun setShowCurrencySymbol(enabled: Boolean) = viewModelScope.launch { repo.setShowCurrencySymbol(enabled) }
    fun setRemoveAds(enabled: Boolean) = viewModelScope.launch { repo.setRemoveAds(enabled) }
}
