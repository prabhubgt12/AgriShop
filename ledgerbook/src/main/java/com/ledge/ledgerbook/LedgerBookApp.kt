package com.ledge.ledgerbook

import android.app.Application
import com.ledge.ledgerbook.data.prefs.LocalePrefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import com.google.android.gms.ads.MobileAds

@HiltAndroidApp
class LedgerBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply stored locale at app start so selection survives process death / recreate
        runBlocking {
            val tag = LocalePrefs.getAppLocaleTag(applicationContext)
            LocalePrefs.applyLocale(applicationContext, tag)
        }
        // Initialize Google Mobile Ads SDK explicitly
        MobileAds.initialize(this)
    }
}
