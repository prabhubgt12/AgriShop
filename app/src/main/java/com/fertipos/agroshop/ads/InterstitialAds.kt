package com.fertipos.agroshop.ads

import android.app.Activity
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAds {
    // Production interstitial ad unit ID
    private const val TEST_UNIT = "ca-app-pub-2556604347710668/5265052238"

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
        // Temporarily disable edge-to-edge and ensure system bars are visible with an opaque nav bar
        // so the interstitial's close button doesn't sit behind gesture/3-button navigation overlays.
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        // Make nav bar opaque during ad
        val prevNavColor = activity.window.navigationBarColor
        activity.window.navigationBarColor = Color.BLACK
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                // Restore edge-to-edge
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Restore previous nav bar color
                activity.window.navigationBarColor = prevNavColor
                onDismiss?.invoke()
                // Preload next
                preload(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                interstitial = null
                // Restore edge-to-edge
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Restore previous nav bar color
                activity.window.navigationBarColor = prevNavColor
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
