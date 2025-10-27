package com.ledge.cashbook.ads

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.ledge.cashbook.BuildConfig
import android.util.Log

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

    // Load on enter and destroy on dispose so navigation back/forward always refreshes the banner
    DisposableEffect(adView, unit) {
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d("BannerAd", "Ad loaded")
                adView.visibility = View.VISIBLE
                onLoadState(true)
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e("BannerAd", "Failed code=${error.code} ${error.message}")
                adView.visibility = View.GONE
                onLoadState(false)
            }
        }
        adView.loadAd(AdRequest.Builder().build())
        onDispose {
            try {
                // Assign a no-op listener instead of null to satisfy non-null type
                adView.adListener = object : AdListener() {}
                adView.destroy()
            } catch (_: Throwable) {}
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { adView },
        update = { }
    )
}
