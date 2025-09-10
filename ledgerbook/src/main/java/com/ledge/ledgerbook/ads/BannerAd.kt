package com.ledge.ledgerbook.ads

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import android.util.Log
import android.view.View
import com.ledge.ledgerbook.BuildConfig
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import android.os.Handler
import android.os.Looper

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-2556604347710668/9208334542",
    onLoadState: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    // Use Google sample banner unit when USE_TEST_ADS is enabled; production unit otherwise
    val unit = if (BuildConfig.USE_TEST_ADS) {
        // 320x50 sample banner: https://developers.google.com/admob/android/test-ads
        "ca-app-pub-3940256099942544/9214589741"
    } else adUnitId

    val adView = remember(unit, widthDp) {
        Log.d("BannerAd", "Creating AdView with unit=" + unit + ", size=" + adaptiveSize)
        AdView(context).apply {
            setAdSize(adaptiveSize)
            setAdUnitId(unit)
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    val listenerAttached = remember(unit) { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { _ ->
            adView.apply {
                // Attach listeners only once
                if (!listenerAttached.value) {
                    setAdListener(object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d("BannerAd", "Ad loaded for unit=$unit")
                            visibility = View.VISIBLE
                            onLoadState(true)
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e("BannerAd", "Ad failed to load for unit=$unit code=${error.code} message=${error.message} domain=${error.domain}")
                            visibility = View.GONE
                            onLoadState(false)
                            // Basic cooldown before retry to avoid request spam / rate limiting
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    Log.d("BannerAd", "Retry loading banner after cooldown")
                                    loadAd(AdRequest.Builder().build())
                                } catch (_: Exception) {}
                            }, 60_000)
                        }
                        override fun onAdImpression() { Log.d("BannerAd", "Ad impression") }
                        override fun onAdClicked() { Log.d("BannerAd", "Ad clicked") }
                    })
                    listenerAttached.value = true
                }
                if (visibility != View.VISIBLE) {
                    val request = AdRequest.Builder().build()
                    Log.d("BannerAd", "Loading banner with request: $request")
                    loadAd(request)
                }
            }
        },
        update = { /* no-op; we keep a single adView */ }
    )
}
