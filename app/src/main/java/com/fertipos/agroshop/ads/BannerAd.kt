package com.fertipos.agroshop.ads

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import android.util.Log
import com.fertipos.agroshop.BuildConfig

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    // Production banner unit id
    adUnitId: String = "ca-app-pub-2556604347710668/7495382334",
    onLoadState: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    // Select ad unit based on BuildConfig flag
    val unit = if (BuildConfig.USE_TEST_ADS) {
        // Google sample banner (320x50). SDK will adapt size as requested above.
        "ca-app-pub-3940256099942544/9214589741"
    } else adUnitId

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adaptiveSize)
                this.adUnitId = unit
                visibility = View.GONE
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("BannerAd", "Banner loaded (unit=" + unit + ")")
                        visibility = View.VISIBLE
                        onLoadState(true)
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("BannerAd", "Banner failed to load (unit=" + unit + ") code=${error.code} message=${error.message}")
                        visibility = View.GONE
                        onLoadState(false)
                    }
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
