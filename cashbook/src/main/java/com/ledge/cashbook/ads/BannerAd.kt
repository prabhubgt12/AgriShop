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

    val adView = remember(unit, widthDp) {
        Log.d("BannerAd", "Creating AdView unit=$unit size=$adaptiveSize")
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
        factory = {
            adView.apply {
                if (!listenerAttached.value) {
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d("BannerAd", "Ad loaded")
                            visibility = View.VISIBLE
                            onLoadState(true)
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e("BannerAd", "Failed code=${error.code} ${error.message}")
                            visibility = View.GONE
                            onLoadState(false)
                            // Cooldown before retry to avoid request spam
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    Log.d("BannerAd", "Retry loading banner after cooldown")
                                    loadAd(AdRequest.Builder().build())
                                } catch (_: Exception) {}
                            }, 60_000)
                        }
                        override fun onAdImpression() { Log.d("BannerAd", "Ad impression") }
                        override fun onAdClicked() { Log.d("BannerAd", "Ad clicked") }
                    }
                    listenerAttached.value = true
                }
                if (visibility != View.VISIBLE) {
                    loadAd(AdRequest.Builder().build())
                }
            }
        },
        update = { }
    )
}
