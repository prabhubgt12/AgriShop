package com.ledge.splitbook.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    @Volatile private var code: String = "INR"
    @Volatile private var showSymbol: Boolean = true

    fun setConfig(code: String, showSymbol: Boolean) {
        this.code = code
        this.showSymbol = showSymbol
    }

    private fun symbolFor(code: String): String = when (code.uppercase(Locale.ENGLISH)) {
        "INR" -> "₹"
        "USD" -> "${'$'}"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CNY" -> "¥"
        "AUD" -> "A$"
        "CAD" -> "C$"
        "SGD" -> "S$"
        "AED" -> "AED"
        else -> "₹"
    }

    fun format(amount: Double, code: String? = null): String {
        val nf = NumberFormat.getNumberInstance(Locale.ENGLISH).apply {
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
            minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        }
        val chosen = (code ?: this.code).uppercase(Locale.ENGLISH)
        val num = nf.format(amount)
        return if (showSymbol) symbolFor(chosen) + num else num
    }
}

// Backward-compatible helper used throughout the app
fun formatAmount(amount: Double, currency: String): String {
    // Try to parse a code like "INR" from the setting label (e.g., "INR ₹"), otherwise use it as-is
    val code = currency.takeWhile { !it.isWhitespace() }.ifBlank { currency }
    return CurrencyFormatter.format(amount, code)
}
