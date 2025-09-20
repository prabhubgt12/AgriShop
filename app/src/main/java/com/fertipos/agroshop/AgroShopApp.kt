package com.fertipos.agroshop

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.fertipos.agroshop.ads.InterstitialAds
import dagger.hilt.android.HiltAndroidApp
import com.fertipos.agroshop.data.prefs.LocalePrefs

@HiltAndroidApp
class AgroShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply stored locale at app start (Android 11/OEMs rely on early apply as well)
        val tag = LocalePrefs.getAppLocale(applicationContext)
        LocalePrefs.applyLocale(applicationContext, tag)
        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this)
        // Preload a test interstitial globally so it's ready when needed
        InterstitialAds.preload(this)
    }
}
