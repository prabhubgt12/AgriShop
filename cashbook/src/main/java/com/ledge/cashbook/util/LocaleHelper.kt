package com.ledge.cashbook.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

object LocaleHelper {
    private val KEY_APP_LOCALE = stringPreferencesKey("app_locale")

    fun wrap(newBase: Context): Context {
        return try {
            val app = newBase.applicationContext
            val ep = EntryPointAccessors.fromApplication(app, DataStoreEntryPoint::class.java)
            val ds: DataStore<Preferences> = ep.prefs()
            val tag = runBlocking { ds.data.first()[KEY_APP_LOCALE] ?: "" }
            if (tag.isBlank()) return newBase
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(newBase.resources.configuration)
            config.setLocales(LocaleList(locale))
            newBase.createConfigurationContext(config)
        } catch (_: Exception) {
            newBase
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface DataStoreEntryPoint {
    fun prefs(): DataStore<Preferences>
}
