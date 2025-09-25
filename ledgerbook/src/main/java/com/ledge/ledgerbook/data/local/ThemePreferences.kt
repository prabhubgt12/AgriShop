package com.ledge.ledgerbook.data.local

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

private const val PREFS_NAME = "ledger_prefs"
private val Context.dataStore by preferencesDataStore(name = PREFS_NAME)

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val THEME_MODE: Preferences.Key<Int> = intPreferencesKey("theme_mode")
        val GROUPING_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("grouping_enabled")
        // Thresholds (in days)
        val OVERDUE_DAYS: Preferences.Key<Int> = intPreferencesKey("overdue_days")
        val DUE_SOON_WINDOW_DAYS: Preferences.Key<Int> = intPreferencesKey("due_soon_window_days")
    }

    fun themeModeFlow(defaultMode: Int = 0): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[Keys.THEME_MODE] ?: defaultMode }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    fun groupingEnabledFlow(default: Boolean = true): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.GROUPING_ENABLED] ?: default }

    suspend fun setGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GROUPING_ENABLED] = enabled }
    }

    // Overdue threshold in days (default 365)
    fun overdueDaysFlow(default: Int = 365): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[Keys.OVERDUE_DAYS] ?: default }

    suspend fun setOverdueDays(days: Int) {
        context.dataStore.edit { it[Keys.OVERDUE_DAYS] = days }
    }

    // Due soon window size in days (default 30)
    fun dueSoonWindowDaysFlow(default: Int = 30): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[Keys.DUE_SOON_WINDOW_DAYS] ?: default }

    suspend fun setDueSoonWindowDays(days: Int) {
        context.dataStore.edit { it[Keys.DUE_SOON_WINDOW_DAYS] = days }
    }
}
