package com.ledge.splitbook.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val CURRENCY = stringPreferencesKey("currency")
        val SHOW_CURRENCY_SYMBOL = booleanPreferencesKey("show_currency_symbol")
        val REMOVE_ADS = booleanPreferencesKey("remove_ads")
    }

    data class Settings(
        val darkMode: Boolean = false,
        val language: String = "English",
        val currency: String = "INR",
        val showCurrencySymbol: Boolean = true,
        val removeAds: Boolean = false,
    )

    val settings: Flow<Settings> = dataStore.data.map { pref ->
        Settings(
            darkMode = pref[Keys.DARK_MODE] ?: false,
            language = pref[Keys.LANGUAGE] ?: "English",
            currency = pref[Keys.CURRENCY] ?: "INR",
            showCurrencySymbol = pref[Keys.SHOW_CURRENCY_SYMBOL] ?: true,
            removeAds = pref[Keys.REMOVE_ADS] ?: false,
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguage(value: String) {
        dataStore.edit { it[Keys.LANGUAGE] = value }
    }

    suspend fun setCurrency(value: String) {
        dataStore.edit { it[Keys.CURRENCY] = value }
    }

    suspend fun setShowCurrencySymbol(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_CURRENCY_SYMBOL] = enabled }
    }

    suspend fun setRemoveAds(enabled: Boolean) {
        dataStore.edit { it[Keys.REMOVE_ADS] = enabled }
    }
}
