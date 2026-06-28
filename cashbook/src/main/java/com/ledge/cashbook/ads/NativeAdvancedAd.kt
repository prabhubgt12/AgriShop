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
                    onLoadState(true)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("NativeAdvancedAd", "Failed to load native (unit=$unit): code=${error.code} message=${error.message} domain=${error.domain}")
                        onLoadState(false)
                        // Cooldown before retry to avoid request spam / rate limiting
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                Log.d("NativeAdvancedAd", "Retry loading native after cooldown")
                                loader.loadAd(AdRequest.Builder().build())
                            } catch (_: Exception) {}
                        }, 60_000)
                    }
                    override fun onAdClicked() { Log.d("NativeAdvancedAd", "Ad clicked (unit=$unit)") }
                    override fun onAdImpression() { Log.d("NativeAdvancedAd", "Impression recorded (unit=$unit)") }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
            loader = builder.build()
            loader.loadAd(AdRequest.Builder().build())
            container
        },
        update = { }
    )
}
