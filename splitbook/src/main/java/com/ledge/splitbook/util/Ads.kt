package com.ledge.splitbook.util

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ledge.splitbook.BuildConfig

object AdsManager {
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val REAL_INTERSTITIAL = "ca-app-pub-2556604347710668/3311615427"

    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var lastShownElapsedMs: Long = 0L
    private const val COOLDOWN_MS: Long = 5 * 60 * 1000 // 5 min

    private fun interstitialUnitId(): String = if (BuildConfig.USE_TEST_ADS) TEST_INTERSTITIAL else REAL_INTERSTITIAL

    fun ensureInterstitialLoaded(context: Context) {
        if (interstitial != null) return
        InterstitialAd.load(
            context,
            interstitialUnitId(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
            }
        )
    }

    fun shouldShow(): Boolean {
        val since = SystemClock.elapsedRealtime() - lastShownElapsedMs
        return since >= COOLDOWN_MS
    }

    fun tryShow(activity: Activity) {
        val ad = interstitial
        if (ad == null) {
            ensureInterstitialLoaded(activity)
            return
        }
        if (!shouldShow()) return
        ad.show(activity)
        lastShownElapsedMs = SystemClock.elapsedRealtime()
        interstitial = null
        ensureInterstitialLoaded(activity)
    }
}
