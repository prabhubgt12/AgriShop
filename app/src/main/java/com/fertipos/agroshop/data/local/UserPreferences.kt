package com.fertipos.agroshop.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "user_prefs"

private val Context.dataStore by preferencesDataStore(name = PREFS_NAME)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val THEME_MODE: Preferences.Key<Int> = intPreferencesKey("theme_mode")
        val LOGGED_IN: Preferences.Key<Boolean> = booleanPreferencesKey("logged_in")
    }

    fun themeModeFlow(defaultMode: Int = 0): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[Keys.THEME_MODE] ?: defaultMode }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    fun loggedInFlow(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.LOGGED_IN] ?: false }

    suspend fun setLoggedIn(value: Boolean) {
        context.dataStore.edit { it[Keys.LOGGED_IN] = value }
    }
}
