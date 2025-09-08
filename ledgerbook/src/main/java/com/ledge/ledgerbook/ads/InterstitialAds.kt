package com.ledge.ledgerbook.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAds {
    // Production interstitial unit id provided by user
    private const val PROD_UNIT = "ca-app-pub-2556604347710668/2929559167"

    @Volatile
    private var interstitial: InterstitialAd? = null
    @Volatile
    private var isLoading: Boolean = false
    @Volatile
    private var shownThisSession: Boolean = false

    fun preload(context: Context) {
        if (isLoading || interstitial != null || shownThisSession) return
        isLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            PROD_UNIT,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded interstitial")
                    isLoading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load interstitial code=${error.code} message=${error.message}")
                    isLoading = false
                    interstitial = null
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        if (shownThisSession) {
            onDismiss?.invoke(); return
        }
        val ad = interstitial
        if (ad == null) {
            onDismiss?.invoke(); return
        }
        // Prepare window so interstitial never sits behind nav overlays:
        // 1) Disable edge-to-edge; 2) Show system bars; 3) Make nav bar opaque during ad.
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
        val prevNavColor = activity.window.navigationBarColor
        activity.window.navigationBarColor = Color.BLACK
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAds", "Dismissed")
                interstitial = null
                shownThisSession = true
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.window.navigationBarColor = prevNavColor
                onDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAds", "Failed to show: ${p0.code} ${p0.message}")
                interstitial = null
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.window.navigationBarColor = prevNavColor
                onDismiss?.invoke()
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Shown")
            }
        }
        ad.show(activity)
    }
}
