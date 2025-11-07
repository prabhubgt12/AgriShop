package com.ledge.cashbook.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val KEY_CODE = stringPreferencesKey("currency_code")
    private val KEY_SHOW_SYMBOL = booleanPreferencesKey("currency_show_symbol")

    val currencyCode: Flow<String> = dataStore.data.map { it[KEY_CODE] ?: "INR" }
    val showSymbol: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_SYMBOL] ?: true }

    suspend fun setCurrencyCode(code: String) {
        dataStore.edit { it[KEY_CODE] = code }
    }

    suspend fun setShowSymbol(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_SYMBOL] = show }
    }
}
