package com.ledge.cashbook.ads

import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.ledge.cashbook.BuildConfig
import android.widget.TextView
import android.widget.ImageView
import android.view.ViewGroup
import android.util.Log
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

object NativeAdCache {
    private val loadTimestamps = ConcurrentHashMap<String, Long>()
    private const val MIN_REQUEST_INTERVAL_MS = 30_000 // 30 seconds between requests for same unit

    fun shouldLoadAd(unit: String): Boolean {
        val now = System.currentTimeMillis()
        val lastLoad = loadTimestamps[unit] ?: 0
        if (now - lastLoad < MIN_REQUEST_INTERVAL_MS) {
            Log.d("NativeAdCache", "Throttling request for unit=$unit (last load: ${now - lastLoad}ms ago)")
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
fun NativeAdvancedAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-2556604347710668/5374934695", // production native advanced unit id
    onLoadState: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val unit = if (BuildConfig.USE_TEST_ADS) {
        // Google sample native advanced ad unit
        "ca-app-pub-3940256099942544/2247696110"
    } else adUnitId

    var nativeAd by remember(unit) { mutableStateOf<NativeAd?>(null) }
    var hasLoaded by remember(unit) { mutableStateOf(false) }

    DisposableEffect(unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            lateinit var loader: AdLoader
            val builder = AdLoader.Builder(ctx, unit)
                .forNativeAd { ad ->
                    nativeAd?.destroy()
                    nativeAd = ad
                    val adView = NativeAdView(ctx)
                    // Simple programmatic layout for headline + media
                    val media = MediaView(ctx)
                    adView.mediaView = media
                    val headline = TextView(ctx)
                    adView.headlineView = headline
                    // Layout
                    val root = FrameLayout(ctx)
                    root.addView(media, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    root.addView(headline)
                    adView.addView(root)
                    // Bind data
                    (adView.headlineView as TextView).text = ad.headline
                    adView.mediaView?.setMediaContent(ad.mediaContent)
                    adView.setNativeAd(ad)
                    container.removeAllViews()
                    container.addView(adView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    val adapter = ad.responseInfo?.mediationAdapterClassName
                    Log.d("NativeAdvancedAd", "Loaded native ad unit=$unit adapter=${adapter} headline='${ad.headline}'")
                    NativeAdCache.markLoaded(unit)
                    hasLoaded = true
                    onLoadState(true)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("NativeAdvancedAd", "Failed to load native (unit=$unit): code=${error.code} message=${error.message} domain=${error.domain}")
                        onLoadState(false)
                        // Retry after 60 seconds only if throttling allows
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                if (NativeAdCache.shouldLoadAd(unit)) {
                                    Log.d("NativeAdvancedAd", "Retry loading native for unit=$unit")
                                    loader.loadAd(AdRequest.Builder().build())
                                }
                            } catch (_: Exception) {}
                        }, 60_000)
                    }
                    override fun onAdClicked() { Log.d("NativeAdvancedAd", "Ad clicked (unit=$unit)") }
                    override fun onAdImpression() { Log.d("NativeAdvancedAd", "Impression recorded (unit=$unit)") }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
            loader = builder.build()
            // Only load if throttling allows and not already loaded
            if (!hasLoaded && NativeAdCache.shouldLoadAd(unit)) {
                Log.d("NativeAdvancedAd", "Loading native ad for unit=$unit")
                loader.loadAd(AdRequest.Builder().build())
            }
            container
        },
        update = { }
    )
}
