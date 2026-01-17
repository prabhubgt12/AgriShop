package com.ledge.splitbook

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SplitBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("SplitBook", "Application onCreate")
        MobileAds.initialize(this)
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("SplitBook", "Uncaught exception in thread ${'$'}{t?.name}", e)
        }
    }
}
