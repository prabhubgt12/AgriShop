package com.ledge.cashbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ledge.cashbook.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.cashbook.ads.NativeAdvancedAd
import com.ledge.cashbook.ads.InterstitialAds
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.ledge.cashbook.billing.MonetizationViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCashBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val adsVm: AdsViewModel = hiltViewModel()
    val hasRemoveAds by adsVm.hasRemoveAds.collectAsState(initial = adsVm.hasRemoveAds.value)
    val ctx = LocalContext.current
    val monetVm: MonetizationViewModel = hiltViewModel()
    val price by monetVm.removeAdsPrice.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var nativeLoaded by remember { mutableStateOf(false) }

    // Preload interstitial when screen shows (only if ads are enabled)
    LaunchedEffect(hasRemoveAds) {
        if (!hasRemoveAds) {
            InterstitialAds.preload(ctx.applicationContext)
        }
    }
    // Refresh billing ownership on entry so refunds/cancellations reflect immediately
    LaunchedEffect(Unit) {
        monetVm.restore()
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.title_dashboard)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile(title = stringResource(R.string.title_cash_book), icon = Icons.Default.Book, modifier = Modifier.weight(1f)) { onOpenCashBook() }
                HomeTile(title = stringResource(R.string.settings_title), icon = Icons.Default.Settings, modifier = Modifier.weight(1f)) {
                    if (!hasRemoveAds) {
                        val act = (ctx as? android.app.Activity)
                        if (act != null) {
                            InterstitialAds.showIfAvailable(act) { onOpenSettings() }
                        } else onOpenSettings()
                    } else onOpenSettings()
                }
            }
            // Remove-ads banner card just below tiles (gate on price so it doesn't flash before billing resolves)
            if (!hasRemoveAds && price != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Remove ads permanently in just " + (price ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val act = (ctx as? android.app.Activity)
                            if (act != null) scope.launch { monetVm.purchaseRemoveAds(act) }
                        }) { Text(text = stringResource(R.string.remove_ads)) }
                    }
                }
                // Native ad card only when ad is ready
                if (nativeLoaded) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            NativeAdvancedAd(
                                modifier = Modifier.fillMaxWidth(),
                                onLoadState = { loaded -> nativeLoaded = loaded }
                            )
                        }
                    }
                } else {
                    // Start loading and avoid rendering the card until ready
                    NativeAdvancedAd(
                        modifier = Modifier, // off-screen container not needed; AndroidView still needs a parent
                        onLoadState = { loaded -> nativeLoaded = loaded }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(title)
        }
    }
}
