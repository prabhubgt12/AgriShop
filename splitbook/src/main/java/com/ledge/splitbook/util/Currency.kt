package com.ledge.splitbook.util

import java.text.NumberFormat
import java.util.Locale

fun formatAmount(amount: Double, currency: String): String {
    val symbol = when {
        currency.contains("INR") || currency.contains("₹") -> "₹"
        currency.contains("USD") || currency.contains("$") -> "$"
        currency.contains("EUR") || currency.contains("€") -> "€"
        else -> "₹"
    }
    val nf = NumberFormat.getNumberInstance(Locale.ENGLISH).apply {
        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
    }
    return symbol + nf.format(amount)
}
