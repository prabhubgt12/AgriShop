package com.ledge.ledgerbook.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ledge.ledgerbook.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAds {
    // Production interstitial unit id provided by user
    private const val PROD_UNIT = "ca-app-pub-2556604347710668/2929559167"
    private const val TEST_UNIT = "ca-app-pub-3940256099942544/1033173712"

    @Volatile
    private var interstitial: InterstitialAd? = null
    @Volatile
    private var isLoading: Boolean = false
    @Volatile
    private var lastShownAt: Long = 0L
    private const val MIN_INTERVAL_MS: Long = 2 * 60 * 1000 // 2 minutes

    fun preload(context: Context) {
        if (isLoading || interstitial != null) {
            Log.d("InterstitialAds", "preload skipped: isLoading=$isLoading hasAd=${interstitial != null}")
            return
        }
        isLoading = true
        val request = AdRequest.Builder().build()
        val unitId = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else PROD_UNIT
        InterstitialAd.load(
            context,
            unitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded interstitial unit=" + unitId)
                    isLoading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load interstitial unit=" + unitId + " code=${error.code} message=${error.message}")
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
            preload(activity)
            onDismiss?.invoke(); return
        }
        // Ensure system bars are shown appropriately during ad without changing bar colors
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAds", "Dismissed")
                interstitial = null
                lastShownAt = System.currentTimeMillis()
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Preload next for future
                preload(activity)
                onDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAds", "Failed to show: ${p0.code} ${p0.message}")
                interstitial = null
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Try to load a fresh one for next time
                preload(activity)
                onDismiss?.invoke()
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Shown")
            }
        }
        ad.show(activity)
    }
}
