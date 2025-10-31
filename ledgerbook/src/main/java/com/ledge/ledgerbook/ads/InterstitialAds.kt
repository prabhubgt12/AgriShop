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
    private const val PROD_UNIT = "ca-app-pub-2556604347710668/2929559167"
    private const val TEST_UNIT = "ca-app-pub-3940256099942544/1033173712"

    private const val MIN_INTERVAL_MS: Long = 2 * 60 * 1000 // 2 minutes per unit

    // Per-unit state
    private val adsByUnit = java.util.concurrent.ConcurrentHashMap<String, InterstitialAd?>()
    private val loadingByUnit = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val lastShownAtByUnit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun effectiveUnit(prodUnitId: String): String = if (BuildConfig.USE_TEST_ADS) TEST_UNIT else prodUnitId

    fun preload(context: Context, prodUnitId: String? = null) {
        val unit = effectiveUnit(prodUnitId ?: PROD_UNIT)
        if (loadingByUnit[unit] == true) return
        if (adsByUnit[unit] != null) return
        loadingByUnit[unit] = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            unit,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingByUnit[unit] = false
                    adsByUnit[unit] = ad
                    Log.d("InterstitialAds", "Loaded unit=$unit")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingByUnit[unit] = false
                    adsByUnit[unit] = null
                    Log.e("InterstitialAds", "Load failed unit=$unit code=${error.code} msg=${error.message}")
                }
            }
        )
    }

    fun preloadAll(context: Context, prodUnitIds: List<String>) {
        prodUnitIds.forEach { preload(context, it) }
    }

    fun showWithUnit(activity: Activity, prodUnitId: String, onDismiss: (() -> Unit)? = null) {
        val unit = effectiveUnit(prodUnitId)
        val now = System.currentTimeMillis()
        val last = lastShownAtByUnit[unit] ?: 0L
        val invoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val invokeOnce: () -> Unit = {
            if (invoked.compareAndSet(false, true)) {
                activity.runOnUiThread { onDismiss?.invoke() }
            }
        }
        if (now - last < MIN_INTERVAL_MS) {
            Log.d("InterstitialAds", "Throttled unit=$unit last=$last now=$now")
            invokeOnce()
            return
        }
        val ad = adsByUnit[unit]
        if (ad == null) {
            Log.d("InterstitialAds", "No ad ready for unit=$unit; preloading and navigating")
            preload(activity, unit)
            invokeOnce()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adsByUnit[unit] = null
                lastShownAtByUnit[unit] = System.currentTimeMillis()
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, unit)
                invokeOnce()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                adsByUnit[unit] = null
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, unit)
                invokeOnce()
            }
        }
        try {
            ad.show(activity)
        } finally {
            // Ensure navigation proceeds even if callbacks are not invoked
            invokeOnce()
        }
    }

    fun showIfAvailable(activity: Activity, onDismiss: (() -> Unit)? = null) {
        // Fallback: pick any ready unit not throttled
        val now = System.currentTimeMillis()
        val entry = adsByUnit.entries.firstOrNull { (u, ad) -> ad != null && now - (lastShownAtByUnit[u] ?: 0L) >= MIN_INTERVAL_MS }
        val unit = entry?.key
        val ad = entry?.value
        val invoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val invokeOnce: () -> Unit = {
            if (invoked.compareAndSet(false, true)) {
                activity.runOnUiThread { onDismiss?.invoke() }
            }
        }
        if (ad == null || unit == null) {
            invokeOnce()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adsByUnit[unit] = null
                lastShownAtByUnit[unit] = System.currentTimeMillis()
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, unit)
                invokeOnce()
            }
            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                adsByUnit[unit] = null
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                preload(activity, unit)
                invokeOnce()
            }
        }
        try {
            ad.show(activity)
        } finally {
            // Ensure navigation proceeds even if callbacks are not invoked
            invokeOnce()
        }
    }
}
