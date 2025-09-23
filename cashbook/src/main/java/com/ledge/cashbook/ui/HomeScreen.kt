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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCashBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val adsVm: AdsViewModel = hiltViewModel()
    val hasRemoveAds by adsVm.hasRemoveAds.collectAsState(initial = false)
    val ctx = LocalContext.current

    // Preload interstitial when screen shows (only if ads are enabled)
    LaunchedEffect(hasRemoveAds) {
        if (!hasRemoveAds) {
            InterstitialAds.preload(ctx.applicationContext)
        }
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
            // Native ad area fills remaining space when ads are enabled
            if (!hasRemoveAds) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)) {
                    NativeAdvancedAd(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = title)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
