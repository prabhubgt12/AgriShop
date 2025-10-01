package com.ledge.ledgerbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledge.ledgerbook.ads.BannerAd
import com.ledge.ledgerbook.ads.InterstitialAds
import com.ledge.ledgerbook.billing.MonetizationViewModel
import android.app.Activity
import android.widget.NumberPicker
import android.widget.EditText
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.graphics.Color
import android.view.View
import android.view.MotionEvent
import androidx.compose.ui.platform.LocalContext
import android.view.ContextThemeWrapper
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.pow
import android.content.res.ColorStateList
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.ledge.ledgerbook.R
import com.ledge.ledgerbook.util.NumberToWords

// Compound type for interest calculation
enum class CompoundType { Monthly, Yearly }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenteredAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    containerColor: ComposeColor = ComposeColor(0xFF3A3A3A),
    contentColor: ComposeColor = ComposeColor.White,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight()
            ) {
                Column(Modifier.padding(16.dp)) {
                    title?.let { it(); Spacer(Modifier.height(16.dp)) }
                    text?.let { it(); Spacer(Modifier.height(16.dp)) }
                    Row(horizontalArrangement = Arrangement.End) {
                        dismissButton?.let { it(); Spacer(Modifier.width(8.dp)) }
                        confirmButton()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun WheelDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.monthValue) }
    var day by remember { mutableStateOf(initial.dayOfMonth) }

    val maxDay = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    if (day > maxDay) day = maxDay

    // Theme-oriented colors (Material3), with OEM-safe text enforcement kept below
    val isDark = isSystemInDarkTheme()
    val dialogBgColor = MaterialTheme.colorScheme.surface
    val contentComposeColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColor = contentComposeColor.toArgb()
    // Dividers: semi-white in dark, darker semi-black in light so it doesn't look white
    val dividerColor = if (isDark) ComposeColor(0x66FFFFFF).toArgb() else ComposeColor(0x66000000).toArgb()
    // Theme wrapper per mode to stop OEM tinting the NumberPicker text
    val themedCtx = ContextThemeWrapper(
        LocalContext.current,
        if (isDark) android.R.style.ThemeOverlay_Material_Dark else android.R.style.ThemeOverlay_Material_Light
    )

    fun stylePicker(np: NumberPicker) {
        try {
            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true
            val paint = p.get(np) as android.graphics.Paint
            paint.color = onSurfaceColor
        } catch (_: Exception) {}
        try {
            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true
            m.invoke(np, onSurfaceColor)
        } catch (_: Exception) {}
        // Also iterate children
        for (i in 0 until np.childCount) {
            val c = np.getChildAt(i)
            if (c is EditText) c.setTextColor(onSurfaceColor)
        }
        np.invalidate()
    }
    

    CenteredAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalDate.of(year, month, day)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = dialogBgColor,
        contentColor = contentComposeColor,
        text = {
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Day
                    AndroidView(
                        modifier = Modifier
                            .width(60.dp)
                            .height(120.dp),
                        factory = { _ -> NumberPicker(themedCtx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1
                        maxValue = maxDay
                        value = day.coerceAtMost(maxDay)
                        setFormatter { String.format("%02d", it) }
                        // Ensure initial styling after layout
                        post { stylePicker(this) }
                        setOnValueChangedListener { _, _, newVal ->
                            day = newVal
                            stylePicker(this)
                        }
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnScrollListener { _, _ ->
                            isActivated = false; isPressed = false
                            stylePicker(this)
                        }
                        for (i in 0 until childCount) {
                            val c = getChildAt(i)
                            if (c is EditText) {
                                c.setTextColor(onSurfaceColor)
                                c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                c.setBackgroundColor(Color.TRANSPARENT)
                                c.isCursorVisible = false
                                c.highlightColor = Color.TRANSPARENT
                                c.isFocusable = false
                                c.isFocusableInTouchMode = false
                                c.isClickable = false
                                c.isLongClickable = false
                                val states = arrayOf(
                                    intArrayOf(android.R.attr.state_pressed),
                                    intArrayOf(android.R.attr.state_focused),
                                    intArrayOf(android.R.attr.state_activated),
                                    intArrayOf(android.R.attr.state_selected),
                                    intArrayOf()
                                )
                                val colors = intArrayOf(onSurfaceColor, onSurfaceColor, onSurfaceColor, onSurfaceColor, onSurfaceColor)
                                c.setTextColor(ColorStateList(states, colors))
                            }
                        }
                        invalidate()
                    } }
                )
                // Month
                AndroidView(
                    modifier = Modifier
                        .width(60.dp)
                        .height(120.dp),
                    factory = { _ -> NumberPicker(themedCtx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1
                        maxValue = 12
                        value = month
                        // Use month short names to render as DD Mon YYYY
                        val monthNames = arrayOf(
                            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
                        )
                        displayedValues = null // reset before changing
                        displayedValues = monthNames
                        // Ensure initial styling after layout
                        post { stylePicker(this) }
                        // Align Month divider with Day/Year
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnValueChangedListener { _, _, newVal ->
                            month = newVal
                            stylePicker(this)
                        }
                        setOnScrollListener { _, _ ->
                            isActivated = false; isPressed = false
                            stylePicker(this)
                        }
                        for (i in 0 until childCount) {
                            val c = getChildAt(i)
                            if (c is EditText) {
                                c.setTextColor(onSurfaceColor)
                                c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                c.setBackgroundColor(Color.TRANSPARENT)
                                c.isCursorVisible = false
                                c.highlightColor = Color.TRANSPARENT
                                c.isFocusable = false
                                c.isFocusableInTouchMode = false
                                c.isClickable = false
                                c.isLongClickable = false
                            }
                        }
                        invalidate()
                    } }
                )
                // Year
                AndroidView(
                    modifier = Modifier
                        .width(60.dp)
                        .height(120.dp),
                    factory = { _ -> NumberPicker(themedCtx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1900
                        maxValue = 2100
                        value = year
                        // Ensure initial styling after layout
                        post { stylePicker(this) }
                        setOnValueChangedListener { _, _, newVal ->
                            year = newVal
                            stylePicker(this)
                        }
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnScrollListener { _, _ ->
                            stylePicker(this)
                        }
                        for (i in 0 until childCount) {
                            val c = getChildAt(i)
                            if (c is EditText) {
                                c.setTextColor(onSurfaceColor)
                                c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                c.setBackgroundColor(Color.TRANSPARENT)
                                c.isCursorVisible = false
                                c.highlightColor = Color.TRANSPARENT
                                c.isFocusable = false
                                c.isFocusableInTouchMode = false
                                c.isClickable = false
                                c.isLongClickable = false
                            }
                        }
                        invalidate()
                    } },
                    update = { /* no-op for Year */ }
                )
                }
            }
        }
    )
}

@Composable
fun HomeScreen(
    onOpenLedger: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLogout: () -> Unit
) {
    // Back from home should finish activity (app-only logout)
    BackHandler(enabled = true) { onRequestLogout() }
    val monetizationVM: MonetizationViewModel = hiltViewModel()
    val hasRemoveAds by monetizationVM.hasRemoveAds.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!hasRemoveAds) {
            InterstitialAds.preload(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Text(
                text = stringResource(R.string.title_ledger_book),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Tile(
                title = stringResource(R.string.title_khata_book),
                icon = Icons.Default.Book,
                modifier = Modifier.weight(1f)
            ) { onOpenLedger() }

            Tile(
                title = stringResource(R.string.settings_title),
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            ) {
                if (!hasRemoveAds) {
                    val activity = context as? Activity
                    if (activity != null) {
                        InterstitialAds.showIfAvailable(activity) { onOpenSettings() }
                    } else {
                        onOpenSettings()
                    }
                } else {
                    onOpenSettings()
                }
            }
        }

        if (!hasRemoveAds) {
            BannerAd(
                modifier = Modifier.fillMaxWidth(),
                adUnitId = "ca-app-pub-2556604347710668/1187622105"
            )
        }

        // Interest Calculator Card
        InterestCalculatorCard()
    }
}

@Composable
private fun Tile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun InterestCalculatorCard() {
    // Input states
    var principal by remember { mutableStateOf("") }
    var ratePerMonth by remember { mutableStateOf("") }

    // Mode: true = Dates, false = Duration
    var useDates by remember { mutableStateOf(true) }

    // Date inputs
    var fromDateText by remember { mutableStateOf("") } // yyyy-MM-dd
    var toDateText by remember { mutableStateOf("") }

    // Duration inputs
    var yearsText by remember { mutableStateOf("") }
    var monthsText by remember { mutableStateOf("") }
    var daysText by remember { mutableStateOf("") }

    // Interest type: true = Simple, false = Compound
    var simpleInterest by remember { mutableStateOf(true) }

    var compoundType by remember { mutableStateOf(CompoundType.Monthly) }

    // Result states
    var resultDuration by remember { mutableStateOf("") }
    var resultPrincipal by remember { mutableStateOf("") }
    var resultRate by remember { mutableStateOf("") }
    var resultInterest by remember { mutableStateOf("") }
    var resultTotal by remember { mutableStateOf("") }

    val df = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
 
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.interest_calculator), style = MaterialTheme.typography.titleLarge)
            // Compact minimum height to align fields with chips without breaking focus
            val compactHeight = 40.dp

            // Principal & Rate
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.label_principal_generic), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = compactHeight),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = ratePerMonth,
                    onValueChange = { ratePerMonth = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.label_rate_per_month), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = compactHeight),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
            }
            // Principal in words (localized, Indian numbering)
            val localeTag = LocalContext.current.resources.configuration.locales[0]?.toLanguageTag()
            val principalWords = remember(principal, localeTag) {
                val amt = principal.toDoubleOrNull() ?: 0.0
                NumberToWords.inIndianSystem(amt, localeTag)
            }
            if (principal.isNotBlank() && principalWords.isNotBlank()) {
                Text(
                    text = principalWords,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Toggle: Dates / Duration
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = useDates,
                    onClick = { useDates = true },
                    label = { Text(stringResource(R.string.dates)) }
                )
                FilterChip(
                    selected = !useDates,
                    onClick = { useDates = false },
                    label = { Text(stringResource(R.string.duration)) }
                )
            }

            if (useDates) {
                // Wheel (roller) date pickers using NumberPicker
                var showFromPicker by remember { mutableStateOf(false) }
                var showToPicker by remember { mutableStateOf(false) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = fromDateText,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.from), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = compactHeight),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            readOnly = true
                        )
                        // Full overlay to reliably capture taps
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showFromPicker = true }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = toDateText,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.to), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = compactHeight),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            readOnly = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showToPicker = true }
                        )
                    }
                }

                if (showFromPicker) {
                    WheelDatePickerDialog(
                        initial = fromDateText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it, df) }.getOrNull() }
                            ?: LocalDate.now(),
                        onDismiss = { showFromPicker = false },
                        onConfirm = { date ->
                            fromDateText = date.format(df)
                            showFromPicker = false
                        }
                    )
                }

                if (showToPicker) {
                    WheelDatePickerDialog(
                        initial = toDateText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it, df) }.getOrNull() }
                            ?: LocalDate.now(),
                        onDismiss = { showToPicker = false },
                        onConfirm = { date ->
                            toDateText = date.format(df)
                            showToPicker = false
                        }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = yearsText,
                        onValueChange = { yearsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.years), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.months), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.days), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
            }

            // Toggle: Simple / Compound (first row)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = simpleInterest,
                    onClick = { simpleInterest = true },
                    label = { Text(stringResource(R.string.simple)) }
                )
                FilterChip(
                    selected = !simpleInterest,
                    onClick = { simpleInterest = false },
                    label = { Text(stringResource(R.string.compound)) }
                )
            }
            // Compound type chips on a separate row to avoid wrap/render issues
            if (!simpleInterest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = compoundType == CompoundType.Monthly,
                        onClick = { compoundType = CompoundType.Monthly },
                        label = { Text(stringResource(R.string.monthly)) }
                    )
                    FilterChip(
                        selected = compoundType == CompoundType.Yearly,
                        onClick = { compoundType = CompoundType.Yearly },
                        label = { Text(stringResource(R.string.yearly)) }
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    // Clear all
                    principal = ""
                    ratePerMonth = ""
                    fromDateText = ""
                    toDateText = ""
                    yearsText = ""
                    monthsText = ""
                    daysText = ""
                    resultDuration = ""
                    resultRate = ""
                    resultPrincipal = ""
                    resultInterest = ""
                    resultTotal = ""
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.clear)) }
                Button(onClick = {
                    val p = principal.toDoubleOrNull() ?: 0.0
                    val rM = ratePerMonth.toDoubleOrNull() ?: 0.0

                    // duration calculation
                    val (years, months, days) = if (useDates) {
                        try {
                            val start = LocalDate.parse(fromDateText, df)
                            val end = LocalDate.parse(toDateText, df)
                            val period = if (!end.isBefore(start)) Period.between(start, end) else Period.between(end, start)
                            Triple(period.years, period.months, period.days)
                        } catch (_: DateTimeParseException) {
                            Triple(0, 0, 0)
                        }
                    } else {
                        Triple(
                            yearsText.toIntOrNull() ?: 0,
                            monthsText.toIntOrNull() ?: 0,
                            daysText.toIntOrNull() ?: 0
                        )
                    }

                    val totalMonths = years * 12 + months + (days / 30.0)
                    val rateM = rM / 100.0
                    val interest = if (simpleInterest) {
                        p * rateM * totalMonths
                    } else {
                        if (compoundType == CompoundType.Monthly) {
                            // Monthly compounding using entered monthly rate
                            p * ((1 + rateM).pow(totalMonths) - 1)
                        } else {
                            // Yearly compounding: use NOMINAL annual rate from monthly input
                            // Example: 2%/mo => 24%/yr; 2 years on 1000 => 1000 * ((1+0.24)^2 - 1) ≈ 537.60
                            val yearsFraction = totalMonths / 12.0
                            val nominalRateY = 12.0 * rateM
                            p * ((1 + nominalRateY).pow(yearsFraction) - 1)
                        }
                    }

                    val total = p + interest

                    resultDuration = "${years}y ${months}m ${days}d"
                    resultPrincipal = "%.2f".format(p)
                    resultRate = "%.2f%%/mo".format(rM)
                    resultInterest = "%.2f".format(interest)
                    resultTotal = "%.2f".format(total)
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.calculate)) }
            }

            // Results in a single compact card
            if (resultTotal.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultDuration, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.rate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultRate, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.label_principal_generic), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultPrincipal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.label_interest), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultInterest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.label_total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultTotal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}
