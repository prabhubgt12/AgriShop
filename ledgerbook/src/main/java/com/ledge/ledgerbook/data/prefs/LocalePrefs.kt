package com.ledge.ledgerbook.data.prefs

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DS_NAME = "settings"

val Context.localeDataStore by preferencesDataStore(name = DS_NAME)

object LocalePrefs {
    private val KEY_APP_LOCALE = stringPreferencesKey("app_locale_tag") // "" => System

    fun appLocaleFlow(ctx: Context): Flow<String> =
        ctx.localeDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[KEY_APP_LOCALE] ?: "" }

    suspend fun getAppLocaleTag(ctx: Context): String {
        return ctx.localeDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[KEY_APP_LOCALE] ?: "" }
            .first()
    }

    suspend fun setAppLocale(ctx: Context, tag: String) {
        ctx.localeDataStore.edit { it[KEY_APP_LOCALE] = tag }
    }

    fun applyLocale(ctx: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = ctx.getSystemService(LocaleManager::class.java)
            val androidLocales = if (tag.isBlank()) android.os.LocaleList.getEmptyLocaleList()
            else android.os.LocaleList.forLanguageTags(tag)
            lm?.applicationLocales = androidLocales
        } else {
            if (tag.isBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
    }
}
