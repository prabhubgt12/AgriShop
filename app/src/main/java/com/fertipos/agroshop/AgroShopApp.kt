package com.fertipos.agroshop

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.fertipos.agroshop.ads.InterstitialAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AgroShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this)
        // Preload a test interstitial globally so it's ready when needed
        InterstitialAds.preload(this)
    }
}
