package com.fertipos.agroshop.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAds {
    // Google TEST interstitial ad unit ID
    private const val TEST_UNIT = "ca-app-pub-3940256099942544/1033173712"

    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var isLoading: Boolean = false

    fun preload(context: Context) {
        if (isLoading || interstitial != null) return
        isLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            TEST_UNIT,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded test interstitial")
                    isLoading = false
                    interstitial = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load test interstitial: ${error.code} ${error.message}")
                    isLoading = false
                    interstitial = null
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        val ad = interstitial
        if (ad == null) { onDismiss?.invoke(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                onDismiss?.invoke()
                // Preload next
                preload(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                interstitial = null
                onDismiss?.invoke()
                preload(activity.applicationContext)
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Shown test interstitial")
            }
        }
        ad.show(activity)
    }
}
