package com.ledge.cashbook.ads

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.ledge.cashbook.BuildConfig
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

object BannerAdCache {
    private val adViews = ConcurrentHashMap<String, AdView>()
    private val loadTimestamps = ConcurrentHashMap<String, Long>()
    private const val MIN_REQUEST_INTERVAL_MS = 30_000 // 30 seconds between requests for same unit

    fun getAdView(context: android.content.Context, unit: String, size: AdSize): AdView {
        val existing = adViews[unit]
        if (existing != null) {
            // Remove from old parent if exists
            val parent = existing.parent as? ViewGroup
            parent?.removeView(existing)
            // Update size if changed
            if (existing.adSize != size) {
                existing.setAdSize(size)
            }
            Log.d("BannerAdCache", "Reusing cached AdView for unit=$unit")
            return existing
        }
        Log.d("BannerAdCache", "Creating new AdView for unit=$unit")
        return AdView(context).apply {
            setAdSize(size)
            setAdUnitId(unit)
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }.also { adViews[unit] = it }
    }

    fun shouldLoadAd(unit: String): Boolean {
        val now = System.currentTimeMillis()
        val lastLoad = loadTimestamps[unit] ?: 0
        if (now - lastLoad < MIN_REQUEST_INTERVAL_MS) {
            Log.d("BannerAdCache", "Throttling request for unit=$unit (last load: ${now - lastLoad}ms ago)")
            return false
        }
        loadTimestamps[unit] = now
        return true
    }

    fun markLoaded(unit: String) {
        loadTimestamps[unit] = System.currentTimeMillis()
    }
}

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-2556604347710668/5769099631",
    onLoadState: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    val unit = if (BuildConfig.USE_TEST_ADS) {
        // Google sample banner ad unit
        "ca-app-pub-3940256099942544/9214589741"
    } else adUnitId

    // Use cached AdView with stable key (only unit, not widthDp)
    val adView = remember(unit) {
        BannerAdCache.getAdView(context, unit, adaptiveSize)
    }

    val listenerAttached = remember(unit) { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = {
            adView.apply {
                if (!listenerAttached.value) {
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d("BannerAd", "Ad loaded for unit=$unit")
                            visibility = View.VISIBLE
                            BannerAdCache.markLoaded(unit)
                            onLoadState(true)
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e("BannerAd", "Failed unit=$unit code=${error.code} ${error.message}")
                            visibility = View.GONE
                            onLoadState(false)
                            // Retry after 60 seconds only if this was a recent request
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    if (BannerAdCache.shouldLoadAd(unit)) {
                                        Log.d("BannerAd", "Retry loading banner for unit=$unit")
                                        loadAd(AdRequest.Builder().build())
                                    }
                                } catch (_: Exception) {}
                            }, 60_000)
                        }
                        override fun onAdImpression() { Log.d("BannerAd", "Ad impression unit=$unit") }
                        override fun onAdClicked() { Log.d("BannerAd", "Ad clicked unit=$unit") }
                    }
                    listenerAttached.value = true
                }
                // Only load if not already visible and throttling allows
                if (visibility != View.VISIBLE && BannerAdCache.shouldLoadAd(unit)) {
                    Log.d("BannerAd", "Loading ad for unit=$unit")
                    loadAd(AdRequest.Builder().build())
                }
            }
        },
        update = { view ->
            // Update ad size if orientation changed
            if (view.adSize != adaptiveSize) {
                view.setAdSize(adaptiveSize)
            }
        }
    )
}
