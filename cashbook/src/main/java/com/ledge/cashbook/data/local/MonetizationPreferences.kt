package com.ledge.cashbook.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val MONETIZATION_PREFS = "monetization_prefs"
private val Context.monetizationDataStore by preferencesDataStore(name = MONETIZATION_PREFS)

@Singleton
class MonetizationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val REMOVE_ADS: Preferences.Key<Boolean> = booleanPreferencesKey("remove_ads")
    }

    fun removeAdsFlow(default: Boolean = false): Flow<Boolean> =
        context.monetizationDataStore.data.map { it[Keys.REMOVE_ADS] ?: default }

    suspend fun setRemoveAds(enabled: Boolean) {
        context.monetizationDataStore.edit { it[Keys.REMOVE_ADS] = enabled }
    }
}
