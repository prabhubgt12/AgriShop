package com.fertipos.agroshop.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val CURRENCY_PREFS = "currency_prefs"
private val Context.currencyDataStore by preferencesDataStore(name = CURRENCY_PREFS)

@Singleton
class CurrencyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val CURRENCY_CODE: Preferences.Key<String> = stringPreferencesKey("currency_code")
        val SHOW_SYMBOL: Preferences.Key<Boolean> = booleanPreferencesKey("show_currency_symbol")
    }

    fun currencyCodeFlow(defaultCode: String = "INR"): Flow<String> =
        context.currencyDataStore.data.map { it[Keys.CURRENCY_CODE] ?: defaultCode }

    suspend fun setCurrencyCode(code: String) {
        context.currencyDataStore.edit { it[Keys.CURRENCY_CODE] = code }
    }

    fun showSymbolFlow(default: Boolean = true): Flow<Boolean> =
        context.currencyDataStore.data.map { it[Keys.SHOW_SYMBOL] ?: default }

    suspend fun setShowSymbol(show: Boolean) {
        context.currencyDataStore.edit { it[Keys.SHOW_SYMBOL] = show }
    }
}
