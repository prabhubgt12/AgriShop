package com.fertipos.agroshop.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import com.fertipos.agroshop.ui.customer.CustomerScreen
import com.fertipos.agroshop.ui.product.ProductScreen
import com.fertipos.agroshop.ui.billing.BillingScreen
import com.fertipos.agroshop.ui.reports.ReportsScreen
import com.fertipos.agroshop.ui.purchase.PurchaseScreen
import com.fertipos.agroshop.ui.settings.SettingsScreen
import com.fertipos.agroshop.ui.history.InvoiceHistoryScreen
import com.fertipos.agroshop.ui.history.PurchaseHistoryScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.fertipos.agroshop.ads.BannerAd
import com.fertipos.agroshop.ads.InterstitialAds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign

enum class RangeOption(val label: String, val days: Long) {
    M1("Last month", 30),
    M3("3 months", 90),
    M6("6 months", 180),
    Y1("Year", 365)
}

@Composable
private fun MonthlySalesProfitChart() {
    val reportsVm: com.fertipos.agroshop.ui.reports.ReportsViewModel = hiltViewModel()
    val ctx = LocalContext.current

    // Build fiscal months (Apr -> Mar) [India common]
    val months = remember {
        val cal = java.util.Calendar.getInstance()
        val now = cal.timeInMillis
        val fiscalYearStart = run {
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = now
            val year = c.get(java.util.Calendar.YEAR)
            val month = c.get(java.util.Calendar.MONTH) // 0-based
            val fy = if (month >= java.util.Calendar.APRIL) year else year - 1
            c.clear()
            c.set(fy, java.util.Calendar.APRIL, 1, 0, 0, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            c.timeInMillis
        }
        // 12 months windows
        (0 until 12).map { i ->
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = fiscalYearStart
            c.add(java.util.Calendar.MONTH, i)
            val start = c.timeInMillis
            // end: last millisecond of month
            c.add(java.util.Calendar.MONTH, 1)
            c.add(java.util.Calendar.MILLISECOND, -1)
            val end = c.timeInMillis
            // label like Apr, May ...
            c.timeInMillis = start
            val lbl = c.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
            Triple(lbl, start, end)
        }
    }

    // State for monthly values
    data class MP(val sales: Double, val profit: Double)
    var monthly by remember { mutableStateOf(List(12) { MP(0.0, 0.0) }) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Load data once
    LaunchedEffect(months) {
        val results = mutableListOf<MP>()
        for ((_, start, end) in months) {
            val toEnd = end.coerceAtLeast(start)
            val pl = reportsVm.computeProfitAndLoss(start, toEnd, com.fertipos.agroshop.ui.reports.ReportsViewModel.CostingMethod.AVERAGE)
            results.add(MP(sales = pl.salesSubtotal, profit = pl.grossProfit.coerceAtLeast(0.0)))
        }
        monthly = results
    }

    val maxSales = remember(monthly) { monthly.maxOfOrNull { it.sales }?.takeIf { it > 0 } ?: 1.0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Chart area
        val barCount = months.size
        val barSpacing = 8.dp
        val chartHeight = 180.dp
        val barCorner = 6.dp
        // Pre-computed spacing in px for non-composable scopes
        val spacingPx = with(LocalDensity.current) { barSpacing.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(barCount, monthly) {
                    detectTapGestures { offset ->
                        val w = size.width
                        val totalSpacingPx = spacingPx * (barCount + 1)
                        val barWidth = ((w - totalSpacingPx) / barCount).coerceAtLeast(0f)
                        var x = spacingPx
                        for (i in 0 until barCount) {
                            val left = x
                            val right = x + barWidth
                            if (offset.x in left..right) {
                                selectedIndex = i
                                break
                            }
                            x += barWidth + spacingPx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val barWidth = ((w - spacingPx * (barCount + 1)) / barCount).coerceAtLeast(0f)
            // Gap between sales and profit bars inside the same month group
            val innerGap = (spacingPx * 0.4f).coerceAtLeast(2f)
            var x = spacingPx
            monthly.forEachIndexed { i, mp ->
                val salesH = (mp.sales / maxSales).toFloat() * h
                val profitH = (mp.profit.coerceAtLeast(0.0) / maxSales).toFloat() * h
                // Two side-by-side bars within the month group
                val singleBarWidth = ((barWidth - innerGap) / 2f).coerceAtLeast(1f)
                val salesLeft = x
                val profitLeft = x + singleBarWidth + innerGap

                // Sales bar
                drawRect(
                    color = MonthlySalesColor,
                    topLeft = Offset(salesLeft, h - salesH),
                    size = Size(singleBarWidth, salesH)
                )
                // Profit bar
                drawRect(
                    color = MonthlyProfitColor,
                    topLeft = Offset(profitLeft, h - profitH),
                    size = Size(singleBarWidth, profitH)
                )
                x += barWidth + spacingPx
            }
        }

        Spacer(Modifier.height(8.dp))
        // X-axis labels
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            months.forEach { (lbl, _, _) ->
                Text(lbl, style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        // Legends
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(color = MonthlySalesColor)
                Spacer(Modifier.width(8.dp))
                val i = selectedIndex
                val text = if (i != null) {
                    val v = monthly.getOrNull(i)?.sales ?: 0.0
                    "Sales: ₹" + String.format("%,.2f", v)
                } else "Sales"
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(color = MonthlyProfitColor)
                Spacer(Modifier.width(8.dp))
                val i = selectedIndex
                val text = if (i != null) {
                    val v = monthly.getOrNull(i)?.profit ?: 0.0
                    "Profit: ₹" + String.format("%,.2f", v)
                } else "Profit"
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// Vibrant palette for Overview chart
private val OverviewSalesColor = Color(0xFF6C63FF)   // vibrant indigo
private val OverviewPurchaseColor = Color(0xFFFFA000) // vibrant orange
private val MonthlySalesColor = Color(0xFF6C63FF)
private val MonthlyProfitColor = Color(0xFF26A69A)

@Composable
private fun Legend(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color = color, shape = RoundedCornerShape(2.dp))
    )
}

@Composable
fun DashboardScreen() {
    val navVm: AppNavViewModel = hiltViewModel()
    val selected = navVm.selected.collectAsState()
    val previous = navVm.previousSelected.collectAsState()
    // Monetization state (remove ads)
    val monetVm: com.fertipos.agroshop.billing.MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetVm.hasRemoveAds.collectAsState()

    Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Intercept system back
        BackHandler(enabled = selected.value != 0) {
            // If we're on a history screen, go to Home to avoid loops back into Billing/Purchase
            if (selected.value == 6 || selected.value == 8) {
                navVm.navigateTo(0)
                return@BackHandler
            }
            // Default: go back to Home
            navVm.navigateTo(0)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selected.value) {
                0 -> HomeScreen(hasRemoveAds = hasRemoveAds, onNavigateToTab = {
                    if (it == 3) navVm.requestNewBill()
                    navVm.navigateTo(it)
                })
                1 -> CustomerScreen()
                2 -> ProductScreen()
                3 -> BillingScreen(navVm)
                4 -> ReportsScreen()
                7 -> PurchaseScreen(navVm)
                5 -> SettingsScreen()
                6 -> InvoiceHistoryScreen(navVm)
                8 -> PurchaseHistoryScreen(navVm)
                // Interest Book removed from parent app
            }
        }
        // Global banner at bottom for all tabs (hidden if user purchased remove-ads)
        if (!hasRemoveAds) {
            Spacer(Modifier.height(8.dp))
            BannerAd(modifier = Modifier.fillMaxWidth().navigationBarsPadding())
        }
    }
}


@Composable
fun OverviewChart(sales: Double, purchases: Double, profit: Double) {
    val total = (sales + purchases).takeIf { it > 0.0 } ?: 1.0
    val salesSweep = (sales / total).toFloat() * 360f
    val purchaseSweep = (purchases / total).toFloat() * 360f
    val salesColor = OverviewSalesColor
    val purchaseColor = OverviewPurchaseColor
    val ringBg = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val diameter = minOf(w, h) * 0.78f
            val strokeWidth = diameter * 0.18f
            val left = (w - diameter) / 2f
            val top = (h - diameter) / 2f
            // Base ring
            drawArc(
                color = ringBg,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth)
            )
            // Sales slice
            drawArc(
                color = salesColor,
                startAngle = -90f,
                sweepAngle = salesSweep,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth)
            )
            // Purchase slice
            drawArc(
                color = purchaseColor,
                startAngle = -90f + salesSweep,
                sweepAngle = purchaseSweep,
                useCenter = false,
                topLeft = Offset(left, top),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth)
            )

            // Center text: Profit value and label
            val profitText = "₹" + String.format("%,.0f", profit)
            val label = "Profit"
            val paintValue = Paint().apply {
                isAntiAlias = true
                color = onSurfaceArgb
                textAlign = Paint.Align.CENTER
                textSize = (strokeWidth * 0.7f)
            }
            val paintLabel = Paint().apply {
                isAntiAlias = true
                color = onSurfaceVariantArgb
                textAlign = Paint.Align.CENTER
                textSize = (strokeWidth * 0.38f)
            }
            val cx = w / 2f
            val cy = h / 2f
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(profitText, cx, cy - (paintLabel.textSize * 0.4f), paintValue)
                c.nativeCanvas.drawText(label, cx, cy + (paintLabel.textSize * 1.2f), paintLabel)
            }
        }
    }
}
@Composable
private fun HomeScreen(hasRemoveAds: Boolean, onNavigateToTab: (Int) -> Unit) {
    val profVm: com.fertipos.agroshop.ui.settings.CompanyProfileViewModel = hiltViewModel()
    val profile by profVm.profile.collectAsState()
    val context = LocalContext.current
    val reportsVm: com.fertipos.agroshop.ui.reports.ReportsViewModel = hiltViewModel()
    val plState = remember { mutableStateOf<com.fertipos.agroshop.ui.reports.ReportsViewModel.PLResult?>(null) }
    val selectedRange = remember { mutableStateOf(RangeOption.M1) }
    LaunchedEffect(selectedRange.value) {
        val now = System.currentTimeMillis()
        val spanDays = selectedRange.value.days
        val rangeMs = spanDays * 24 * 60 * 60 * 1000
        runCatching {
            reportsVm.computeProfitAndLoss(from = now - rangeMs, to = now, method = com.fertipos.agroshop.ui.reports.ReportsViewModel.CostingMethod.AVERAGE)
        }.onSuccess { plState.value = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
    {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Header with logo and shop name
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = CardDefaults.elevatedShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = profile.logoUri.takeIf { it.isNotBlank() },
                    contentDescription = stringResource(com.fertipos.agroshop.R.string.logo_preview),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name.ifBlank { stringResource(com.fertipos.agroshop.R.string.your_shop) },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Actions card with 2-row grid (all actions inside one card)
        val actions = listOf(
            TileData(stringResource(com.fertipos.agroshop.R.string.billing_title), Icons.Filled.ReceiptLong) { onNavigateToTab(3) },
            TileData(stringResource(com.fertipos.agroshop.R.string.customers_title), Icons.Filled.People) { onNavigateToTab(1) },
            TileData(stringResource(com.fertipos.agroshop.R.string.products_title), Icons.Filled.Inventory2) { onNavigateToTab(2) },
            TileData(stringResource(com.fertipos.agroshop.R.string.purchases_title), Icons.Filled.ShoppingCart) { onNavigateToTab(7) },
            TileData(stringResource(com.fertipos.agroshop.R.string.view_bills), Icons.Filled.History) {
                val act = (context as? android.app.Activity)
                if (!hasRemoveAds && act != null) InterstitialAds.showIfAvailable(act, onDismiss = { onNavigateToTab(6) }) else onNavigateToTab(6)
            },
            TileData(stringResource(com.fertipos.agroshop.R.string.view_purchases), Icons.Filled.History) {
                val act = (context as? android.app.Activity)
                if (!hasRemoveAds && act != null) InterstitialAds.showIfAvailable(
                    activity = act,
                    onDismiss = { onNavigateToTab(8) },
                    unitOverride = "ca-app-pub-2556604347710668/8432413656"
                ) else onNavigateToTab(8)
            },
            TileData(stringResource(com.fertipos.agroshop.R.string.reports_title), Icons.Filled.BarChart) { onNavigateToTab(4) },
            TileData(stringResource(com.fertipos.agroshop.R.string.settings_title), Icons.Filled.Settings) { onNavigateToTab(5) },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                val rows = actions.chunked(4)
                rows.forEach { rowItems ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        rowItems.forEach { t ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { t.onClick() }
                                    .padding(horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = t.icon,
                                    contentDescription = t.title,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    t.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                var menuOpen = remember { mutableStateOf(false) }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.BarChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Overview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { menuOpen.value = true }) {
                        Text(selectedRange.value.label)
                        Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                        RangeOption.values().forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.label) }, onClick = {
                                selectedRange.value = opt
                                menuOpen.value = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OverviewChart(
                    sales = plState.value?.salesSubtotal ?: 0.0,
                    purchases = plState.value?.purchasesSubtotal ?: 0.0,
                    profit = plState.value?.grossProfit ?: 0.0
                )
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(color = OverviewSalesColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Sales Value: "+"₹"+String.format("%,.2f", plState.value?.salesSubtotal ?: 0.0),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(color = OverviewPurchaseColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Purchase Value: "+"₹"+String.format("%,.2f", plState.value?.purchasesSubtotal ?: 0.0),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Monthly Sales & Profit chart card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Filled.BarChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Monthly Sales & Profit", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                MonthlySalesProfitChart()
            }
        }
    }
    }
}

@Composable
private fun HomeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title)
            Spacer(Modifier.padding(2.dp))
            Text(subtitle)
        }
    }
}

private data class TileData(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
private fun TileCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
