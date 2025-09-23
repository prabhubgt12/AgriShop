package com.ledge.cashbook.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ledge.cashbook.BuildConfig

object InterstitialAds {
    // Production and Google sample interstitial units
    private const val PROD_UNIT = "ca-app-pub-2556604347710668/2748771352"
    private const val TEST_UNIT = "ca-app-pub-3940256099942544/1033173712"

    @Volatile private var interstitial: InterstitialAd? = null
    @Volatile private var isLoading: Boolean = false
    @Volatile private var lastShownAt: Long = 0L
    private const val MIN_INTERVAL_MS: Long = 2 * 60 * 1000 // 2 minutes throttle

    fun preload(context: Context) {
        if (isLoading || interstitial != null) return
        isLoading = true
        val request = AdRequest.Builder().build()
        val unit = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else PROD_UNIT
        InterstitialAd.load(
            context,
            unit,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded interstitial (unit=$unit)")
                    isLoading = false
                    interstitial = ad
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load interstitial (unit=$unit): ${error.code} ${error.message}")
                    isLoading = false
                    interstitial = null
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (now - lastShownAt < MIN_INTERVAL_MS) {
            Log.d("InterstitialAds", "Throttled: lastShownAt=$lastShownAt now=$now")
            onDismiss?.invoke(); return
        }
        val ad = interstitial
        if (ad == null) {
            Log.d("InterstitialAds", "No interstitial ready; triggering preload and continuing")
            preload(activity.applicationContext)
            onDismiss?.invoke(); return
        }
        // Ensure system bars visible during ad
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                lastShownAt = System.currentTimeMillis()
                // Restore E2E
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                onDismiss?.invoke()
                // Preload next
                preload(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                interstitial = null
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                onDismiss?.invoke()
                preload(activity.applicationContext)
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Interstitial shown")
            }
        }
        ad.show(activity)
    }
}
