package com.ledge.cashbook.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

object LocalePrefs {
    // Simple in-memory flow mirroring ThemeViewModel persisted store; used by SettingsScreen to reflect changes.
    private val appLocale = MutableStateFlow("")

    fun appLocaleFlow(context: Context): Flow<String> = appLocale

    suspend fun setAppLocale(context: Context, tag: String) {
        appLocale.value = tag
    }

    fun applyLocale(context: Context, tag: String) {
        val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
