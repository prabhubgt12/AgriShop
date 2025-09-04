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

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-2556604347710668/9208334542",
    onLoadState: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val widthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    // Use provided production unit always (no test override)
    val unit = adUnitId

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(adaptiveSize)
                this.adUnitId = unit
                // Start hidden to avoid reserving space before load
                visibility = View.GONE
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("BannerAd", "Ad loaded for unit=$unit")
                        visibility = View.VISIBLE
                        onLoadState(true)
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("BannerAd", "Ad failed to load for unit=$unit code=${error.code} message=${error.message} domain=${error.domain}")
                        visibility = View.GONE
                        onLoadState(false)
                    }
                    override fun onAdImpression() {
                        Log.d("BannerAd", "Ad impression")
                    }
                    override fun onAdClicked() {
                        Log.d("BannerAd", "Ad clicked")
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
