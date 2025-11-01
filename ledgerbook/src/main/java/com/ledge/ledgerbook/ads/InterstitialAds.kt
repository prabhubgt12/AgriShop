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

    private val adsByUnit: java.util.concurrent.ConcurrentHashMap<String, InterstitialAd> = java.util.concurrent.ConcurrentHashMap()
    private val loadingByUnit: java.util.concurrent.ConcurrentHashMap<String, Boolean> = java.util.concurrent.ConcurrentHashMap()

    fun preload(context: Context, prodUnitId: String? = null) {
        val requestedUnit = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else (prodUnitId ?: PROD_UNIT)
        if (loadingByUnit[requestedUnit] == true) {
            Log.d("InterstitialAds", "preload skipped: loading in progress for unit=$requestedUnit")
            return
        }
        if (adsByUnit[requestedUnit] != null) {
            Log.d("InterstitialAds", "preload skipped: already cached ad for unit=$requestedUnit")
            return
        }
        loadingByUnit[requestedUnit] = true
        isLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            requestedUnit,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAds", "Loaded interstitial unit=" + requestedUnit)
                    loadingByUnit[requestedUnit] = false
                    isLoading = false
                    adsByUnit[requestedUnit] = ad
                    interstitial = ad
                    lastLoadedUnitId = requestedUnit
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InterstitialAds", "Failed to load interstitial unit=" + requestedUnit + " code=${error.code} message=${error.message}")
                    loadingByUnit[requestedUnit] = false
                    isLoading = false
                    if (lastLoadedUnitId == requestedUnit) {
                        interstitial = null
                        lastLoadedUnitId = null
                    }
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        val defaultUnit = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else PROD_UNIT
        val currentUnit = defaultUnit
        val now = System.currentTimeMillis()
        val last = lastShownAtByUnit[currentUnit] ?: 0L
        if (now - last < MIN_INTERVAL_MS) {
            Log.d("InterstitialAds", "Throttled for unit=$currentUnit: lastShown=$last now=$now")
            onDismiss?.invoke(); return
        }
        val cached = adsByUnit[currentUnit]
        val ad = cached ?: if (lastLoadedUnitId == currentUnit) interstitial else null
        if (ad == null) {
            Log.d("InterstitialAds", "No interstitial ready for unit=$currentUnit; triggering preload and continuing")
            preload(activity, currentUnit)
            onDismiss?.invoke(); return
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAds", "Dismissed")
                adsByUnit.remove(currentUnit)
                if (lastLoadedUnitId == currentUnit) interstitial = null
                lastShownAtByUnit[currentUnit] = System.currentTimeMillis()
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, currentUnit)
                onDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAds", "Failed to show: ${p0.code} ${p0.message}")
                adsByUnit.remove(currentUnit)
                if (lastLoadedUnitId == currentUnit) interstitial = null
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, currentUnit)
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
        val cached = adsByUnit[requestedUnit]
        val ad = cached ?: if (lastLoadedUnitId == requestedUnit) interstitial else null
        if (ad == null) {
            Log.d("InterstitialAds", "No ready ad for unit=$requestedUnit; preload and continue")
            preload(activity, prodUnitId)
            onDismiss?.invoke(); return
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAds", "Dismissed")
                adsByUnit.remove(requestedUnit)
                if (lastLoadedUnitId == requestedUnit) interstitial = null
                lastShownAtByUnit[requestedUnit] = System.currentTimeMillis()
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, requestedUnit)
                onDismiss?.invoke()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                Log.e("InterstitialAds", "Failed to show: ${p0.code} ${p0.message}")
                adsByUnit.remove(requestedUnit)
                if (lastLoadedUnitId == requestedUnit) interstitial = null
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, requestedUnit)
                onDismiss?.invoke()
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("InterstitialAds", "Shown")
            }
        }
        ad.show(activity)
    }
}
