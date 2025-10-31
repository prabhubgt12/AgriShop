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
    private const val MIN_INTERVAL_MS: Long = 2 * 60 * 1000 // 2 minutes
    // Per-placement throttle timestamps (keyed by effective unit id used for loading/showing)
    private val lastShownAtByUnit: java.util.concurrent.ConcurrentHashMap<String, Long> = java.util.concurrent.ConcurrentHashMap()
    @Volatile
    private var lastLoadedUnitId: String? = null

    fun preload(context: Context, prodUnitId: String? = null) {
        val requestedUnit = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else (prodUnitId ?: PROD_UNIT)
        if (isLoading) {
            Log.d("InterstitialAds", "preload skipped: loading in progress")
            return
        }
        if (interstitial != null && lastLoadedUnitId == requestedUnit) {
            Log.d("InterstitialAds", "preload skipped: already have ad for unit=$requestedUnit")
            return
        }
        isLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            requestedUnit,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded interstitial unit=" + requestedUnit)
                    isLoading = false
                    interstitial = ad
                    lastLoadedUnitId = requestedUnit
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load interstitial unit=" + requestedUnit + " code=${error.code} message=${error.message}")
                    isLoading = false
                    interstitial = null
                    lastLoadedUnitId = null
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        val currentUnit = lastLoadedUnitId
        val now = System.currentTimeMillis()
        if (currentUnit != null) {
            val last = lastShownAtByUnit[currentUnit] ?: 0L
            if (now - last < MIN_INTERVAL_MS) {
                Log.d("InterstitialAds", "Throttled for unit=$currentUnit: lastShown=$last now=$now")
                onDismiss?.invoke(); return
            }
        }
        val ad = interstitial
        if (ad == null) {
            Log.d("InterstitialAds", "No interstitial ready; triggering preload and continuing")
            preload(activity, lastLoadedUnitId)
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
                val unit = lastLoadedUnitId
                if (unit != null) {
                    lastShownAtByUnit[unit] = System.currentTimeMillis()
                }
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Preload next for future (same unit)
                preload(activity, lastLoadedUnitId)
                onDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAds", "Failed to show: ${p0.code} ${p0.message}")
                interstitial = null
                // Restore window settings
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                // Try to load a fresh one for next time (same unit)
                preload(activity, lastLoadedUnitId)
                onDismiss?.invoke()
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Shown")
            }
        }
        ad.show(activity)
    }

    fun showWithUnit(activity: Activity, prodUnitId: String, onDismiss: (() -> Unit)? = null) {
        val requestedUnit = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else prodUnitId
        val now = System.currentTimeMillis()
        val last = lastShownAtByUnit[requestedUnit] ?: 0L
        if (now - last < MIN_INTERVAL_MS) {
            Log.d("InterstitialAds", "Throttled (placement) for unit=$requestedUnit: lastShown=$last now=$now")
            onDismiss?.invoke(); return
        }
        if (interstitial == null || lastLoadedUnitId != requestedUnit) {
            Log.d("InterstitialAds", "No ready ad for unit=$requestedUnit; preload and continue")
            preload(activity, prodUnitId)
            onDismiss?.invoke(); return
        }
        showIfAvailable(activity, onDismiss)
    }
}
