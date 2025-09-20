package com.fertipos.agroshop.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.fertipos.agroshop.data.prefs.LocalePrefs
import java.util.Locale

object LocaleHelper {
    fun wrap(newBase: Context): Context {
        return try {
            val tag = LocalePrefs.getAppLocale(newBase.applicationContext)
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
