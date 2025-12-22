package com.ledge.cashbook.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: DataStore<Preferences>
) : ViewModel() {

    companion object Keys {
        val SHOW_CATEGORY = booleanPreferencesKey("show_category_field")
        val SHOW_CATEGORY_IN_LIST = booleanPreferencesKey("show_category_in_list")
        val CATEGORIES_CSV = stringPreferencesKey("categories_csv")
        val SHOW_SUMMARY = booleanPreferencesKey("show_summary_card")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    }

    val showCategory: Flow<Boolean> = prefs.data.map { it[SHOW_CATEGORY] ?: false }
    val showCategoryInList: Flow<Boolean> = prefs.data.map { it[SHOW_CATEGORY_IN_LIST] ?: true }
    val categoriesCsv: Flow<String> = prefs.data.map { it[CATEGORIES_CSV] ?: "" }
    val showSummary: Flow<Boolean> = prefs.data.map { it[SHOW_SUMMARY] ?: true }
    val autoBackupEnabled: Flow<Boolean> = prefs.data.map { it[AUTO_BACKUP_ENABLED] ?: false }

    fun setShowCategory(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { it[SHOW_CATEGORY] = enabled }
        }
    }

    fun setShowCategoryInList(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { it[SHOW_CATEGORY_IN_LIST] = enabled }
        }
    }

    fun setCategoriesCsv(csv: String) {
        viewModelScope.launch {
            prefs.edit { it[CATEGORIES_CSV] = csv }
        }
    }

    fun setShowSummary(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { it[SHOW_SUMMARY] = enabled }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { it[AUTO_BACKUP_ENABLED] = enabled }
        }
    }
}
