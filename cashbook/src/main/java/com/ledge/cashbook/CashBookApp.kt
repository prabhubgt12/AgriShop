package com.ledge.cashbook

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@HiltAndroidApp
class CashBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved locale before any UI is composed
        try {
            val ep = EntryPointAccessors.fromApplication(this, DataStoreEntryPoint::class.java)
            val dataStore = ep.prefs()
            val key = stringPreferencesKey("app_locale")
            val tag = runBlocking { dataStore.data.first()[key] ?: "" }
            val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        } catch (_: Exception) { }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface DataStoreEntryPoint {
    fun prefs(): DataStore<Preferences>
}
