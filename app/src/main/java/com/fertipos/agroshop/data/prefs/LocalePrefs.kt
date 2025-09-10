package com.fertipos.agroshop.data.prefs

import android.content.Context
import android.os.Build
import android.app.LocaleManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocalePrefs {
    private const val PREFS = "agroshop_locale_prefs"
    private const val KEY_TAG = "app_locale_tag"

    fun getAppLocale(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getString(KEY_TAG, "") ?: ""
    }

    fun setAppLocale(context: Context, tag: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_TAG, tag).apply()
    }

    fun applyLocale(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val lm = context.getSystemService(LocaleManager::class.java)
            val androidLocales = if (tag.isBlank()) android.os.LocaleList.getEmptyLocaleList() else android.os.LocaleList.forLanguageTags(tag)
            lm?.applicationLocales = androidLocales
        } else {
            val localeList = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun toLocaleList(tag: String): LocaleListCompat =
        if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
}
