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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.pow
import android.widget.NumberPicker
import android.widget.EditText
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.graphics.Color
import android.view.View
import android.view.MotionEvent
import android.content.res.ColorStateList
 
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape

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
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
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

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val dividerColor = MaterialTheme.colorScheme.outline.toArgb()

    CenteredAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalDate.of(year, month, day)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AndroidView(
                        modifier = Modifier
                            .width(60.dp)
                            .height(120.dp),
                        factory = { ctx -> NumberPicker(ctx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1900
                        maxValue = 2100
                        value = year
                        setOnValueChangedListener { _, _, newVal ->
                            year = newVal
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            for (i in 0 until childCount) {
                                val c = getChildAt(i)
                                if (c is EditText) {
                                    c.setTextColor(onSurfaceColor)
                                }
                            }
                            invalidate()
                        }
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnScrollListener { _, _ ->
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            invalidate()
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
                AndroidView(
                    modifier = Modifier
                        .width(60.dp)
                        .height(120.dp),
                    factory = { ctx -> NumberPicker(ctx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1
                        maxValue = 12
                        value = month
                        setFormatter { String.format("%02d", it) }
                        setOnValueChangedListener { _, _, newVal ->
                            month = newVal
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            for (i in 0 until childCount) {
                                val c = getChildAt(i)
                                if (c is EditText) c.setTextColor(onSurfaceColor)
                            }
                            invalidate()
                        }
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnScrollListener { _, _ ->
                            isActivated = false; isPressed = false
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            invalidate()
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
                AndroidView(
                    modifier = Modifier
                        .width(60.dp)
                        .height(120.dp),
                    factory = { ctx -> NumberPicker(ctx).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        setFadingEdgeLength(0)
                        setBackgroundColor(Color.TRANSPARENT)
                        wrapSelectorWheel = true
                        overScrollMode = View.OVER_SCROLL_NEVER
                        minValue = 1
                        maxValue = maxDay
                        value = day.coerceAtMost(maxDay)
                        setFormatter { String.format("%02d", it) }
                        setOnValueChangedListener { _, _, newVal ->
                            day = newVal
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            for (i in 0 until childCount) {
                                val c = getChildAt(i)
                                if (c is EditText) c.setTextColor(onSurfaceColor)
                            }
                            invalidate()
                        }
                        try {
                            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider"); f.isAccessible = true; f.set(this, ColorDrawable(dividerColor))
                            val h = NumberPicker::class.java.getDeclaredField("mSelectionDividerHeight"); h.isAccessible = true; h.setInt(this, 1)
                            val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            val m = NumberPicker::class.java.getDeclaredMethod("setTextColor", Int::class.javaPrimitiveType); m.isAccessible = true; m.invoke(this, onSurfaceColor)
                        } catch (_: Exception) {}
                        setOnScrollListener { _, _ ->
                            isActivated = false; isPressed = false
                            try {
                                val p = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint"); p.isAccessible = true; val paint = p.get(this) as android.graphics.Paint; paint.color = onSurfaceColor
                            } catch (_: Exception) {}
                            invalidate()
                        }
                        for (i in 0 until childCount) {
                            val c = getChildAt(i)
                            if (c is EditText) {
                                c.setTextColor(onSurfaceColor)
                                c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                c.setBackgroundColor(Color.TRANSPARENT)
                                c.isCursorVisible = false
                                c.highlightColor = Color.TRANSPARENT
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
                    } },
                    update = { picker -> picker.maxValue = YearMonth.of(year, month).lengthOfMonth() }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                text = "Ledger Book",
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
                title = "Ledger Book",
                icon = Icons.Default.Book,
                modifier = Modifier.weight(1f)
            ) { onOpenLedger() }

            Tile(
                title = "Settings",
                icon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            ) { onOpenSettings() }
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

    val df = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Interest Calculator", style = MaterialTheme.typography.titleLarge)
            // Compact minimum height to align fields with chips without breaking focus
            val compactHeight = 40.dp

            // Principal & Rate
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Principal", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = compactHeight),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                OutlinedTextField(
                    value = ratePerMonth,
                    onValueChange = { ratePerMonth = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Rate (%/Month)", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = compactHeight),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            // Toggle: Dates / Duration
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = useDates,
                    onClick = { useDates = true },
                    label = { Text("Dates") }
                )
                FilterChip(
                    selected = !useDates,
                    onClick = { useDates = false },
                    label = { Text("Duration") }
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
                            label = { Text("From", style = MaterialTheme.typography.labelSmall) },
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
                            label = { Text("To", style = MaterialTheme.typography.labelSmall) },
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
                        label = { Text("Years", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    OutlinedTextField(
                        value = monthsText,
                        onValueChange = { monthsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Months", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Days", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = compactHeight),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            // Toggle: Simple / Compound (first row)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = simpleInterest,
                    onClick = { simpleInterest = true },
                    label = { Text("Simple") }
                )
                FilterChip(
                    selected = !simpleInterest,
                    onClick = { simpleInterest = false },
                    label = { Text("Compound") }
                )
            }
            // Compound type chips on a separate row to avoid wrap/render issues
            if (!simpleInterest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = compoundType == CompoundType.Monthly,
                        onClick = { compoundType = CompoundType.Monthly },
                        label = { Text("Monthly") }
                    )
                    FilterChip(
                        selected = compoundType == CompoundType.Yearly,
                        onClick = { compoundType = CompoundType.Yearly },
                        label = { Text("Yearly") }
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
                }, modifier = Modifier.weight(1f)) { Text("Clear") }
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
                            p * ((1 + rateM).pow(totalMonths) - 1)
                        } else {
                            val yearsFraction = totalMonths / 12.0
                            val rateY = (1 + rateM).pow(12.0) - 1
                            p * ((1 + rateY).pow(yearsFraction) - 1)
                        }
                    }
                    val total = p + interest

                    resultDuration = "${years}y ${months}m ${days}d"
                    resultPrincipal = "%.2f".format(p)
                    resultRate = "%.2f%%/mo".format(rM)
                    resultInterest = "%.2f".format(interest)
                    resultTotal = "%.2f".format(total)
                }, modifier = Modifier.weight(1f)) { Text("Calculate") }
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
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultDuration, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultRate, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultPrincipal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Interest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultInterest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(resultTotal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}
