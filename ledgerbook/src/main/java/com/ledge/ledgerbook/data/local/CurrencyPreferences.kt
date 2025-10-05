package com.ledge.ledgerbook.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val CURRENCY_CODE: Preferences.Key<String> = stringPreferencesKey("currency_code")
        val SHOW_SYMBOL: Preferences.Key<Boolean> = booleanPreferencesKey("show_currency_symbol")
    }

    fun currencyCodeFlow(defaultCode: String = "INR"): Flow<String> =
        context.ledgerDataStore.data.map { prefs -> prefs[Keys.CURRENCY_CODE] ?: defaultCode }

    suspend fun setCurrencyCode(code: String) {
        context.ledgerDataStore.edit { it[Keys.CURRENCY_CODE] = code }
    }

    fun showSymbolFlow(default: Boolean = true): Flow<Boolean> =
        context.ledgerDataStore.data.map { prefs -> prefs[Keys.SHOW_SYMBOL] ?: default }

    suspend fun setShowSymbol(show: Boolean) {
        context.ledgerDataStore.edit { it[Keys.SHOW_SYMBOL] = show }
    }
}
