package com.ledge.cashbook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import com.ledge.cashbook.data.local.dao.RecentTxnRow
import com.ledge.cashbook.util.Currency
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCashBook: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCategories: () -> Unit,
) {
    val adsVm: AdsViewModel = hiltViewModel()
    val homeVm: HomeViewModel = hiltViewModel()
    val hasRemoveAds by adsVm.hasRemoveAds.collectAsState(initial = adsVm.hasRemoveAds.value)
    val ctx = LocalContext.current
    val monetVm: MonetizationViewModel = hiltViewModel()
    val price by monetVm.removeAdsPrice.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var nativeLoaded by remember { mutableStateOf(false) }
    val recent by homeVm.recentTxns.collectAsState()
    val todayCredit by homeVm.todayCredit.collectAsState()
    val todayDebit by homeVm.todayDebit.collectAsState()

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
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF6750A4),
                            Color(0xFF4A3C8C)
                        )
                    )
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cash Book
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenCashBook() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Book, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(text = stringResource(R.string.title_cash_book))
                        }
                        // Categories
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!hasRemoveAds) {
                                        val act = (ctx as? android.app.Activity)
                                        if (act != null) {
                                            InterstitialAds.showIfAvailable(act) { onOpenCategories() }
                                        } else onOpenCategories()
                                    } else onOpenCategories()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.List, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(text = stringResource(R.string.title_categories))
                        }
                        // Settings
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!hasRemoveAds) {
                                        val act = (ctx as? android.app.Activity)
                                        if (act != null) {
                                            InterstitialAds.showIfAvailable(act) { onOpenSettings() }
                                        } else onOpenSettings()
                                    } else onOpenSettings()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(text = stringResource(R.string.settings_title))
                        }
                    }
                }
            }

            // Remove-ads banner card just below tiles
            if (!hasRemoveAds && price != null) {
                item {
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
                                text = stringResource(R.string.remove_ads_card_text, (price ?: "—")),
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
                }
            }

            item {
                TodayTotalsCard(
                    credit = todayCredit,
                    debit = todayDebit
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.recent_transactions),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        if (recent.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_recent_transactions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val state = rememberLazyListState()
                            LazyColumn(
                                state = state,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(recent, key = { it.id }) { t ->
                                    RecentTxnRowItem(t)
                                }
                            }
                        }
                    }
                }
            }

            // Native ad at the very end (after recent transactions)
            if (!hasRemoveAds) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.ad_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NativeAdvancedAd(
                            modifier = Modifier.fillMaxWidth(),
                            onLoadState = { loaded -> nativeLoaded = loaded }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTotalsCard(
    credit: Double,
    debit: Double
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val creditColor = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val debitColor = if (dark) Color(0xFFE57373) else Color(0xFFB71C1C)
    val net = credit - debit

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.home_today),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = Currency.inr(kotlin.math.abs(net)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (net >= 0) creditColor else debitColor
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_total_credit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Currency.inr(credit), style = MaterialTheme.typography.labelLarge, color = creditColor)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_total_debit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Currency.inr(debit), style = MaterialTheme.typography.labelLarge, color = debitColor)
                }
            }
        }
    }
}

@Composable
private fun RecentTxnRowItem(t: RecentTxnRow) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val arrow = if (t.isCredit) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val amountColor = if (t.isCredit) (if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)) else (if (dark) Color(0xFFE57373) else Color(0xFFB71C1C))
    val arrowColor = amountColor
    val fmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = arrow,
                contentDescription = null,
                tint = arrowColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fmt.format(Date(t.date)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t.accountName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val secondLine = when {
                    !t.note.isNullOrBlank() && !t.category.isNullOrBlank() -> "${t.note} • ${t.category}"
                    !t.note.isNullOrBlank() -> t.note
                    !t.category.isNullOrBlank() -> t.category
                    else -> null
                }
                if (secondLine != null) {
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Text(
                text = Currency.inr(t.amount),
                style = MaterialTheme.typography.titleSmall,
                color = amountColor
            )
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
