package com.ledge.ledgerbook.util

import java.text.NumberFormat
import java.util.Locale
import java.text.DecimalFormat
import java.util.Currency

object CurrencyFormatter {
    @Volatile private var currentCode: String = "INR"
    @Volatile private var currentShowSymbol: Boolean = true

    fun setConfig(code: String, showSymbol: Boolean) {
        currentCode = code
        currentShowSymbol = showSymbol
    }

    private fun formatterFor(
        currencyCode: String,
        minFraction: Int = 0,
        maxFraction: Int = 2,
        locale: Locale = Locale.getDefault()
    ): NumberFormat {
        return NumberFormat.getCurrencyInstance(locale).apply {
            try {
                currency = Currency.getInstance(currencyCode)
            } catch (_: IllegalArgumentException) {
                // Fallback to default currency if code invalid
            }
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
        }
    }

    // Indian number formatter using en-IN locale for proper Indian grouping
    private fun formatIndianNumber(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
        return formatter.format(amount)
    }

    // Indian number formatter with no decimals
    private fun formatIndianNumberNoDecimals(amount: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0
        return formatter.format(amount)
    }

    // Explicit-format API - now defaults to no decimals
    fun format(amount: Double, currencyCode: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val formatted = if (currencyCode.equals("INR", ignoreCase = true)) {
            formatIndianNumberNoDecimals(amount)
        } else {
            val nf = formatterFor(currencyCode, 0, 0, locale)
            nf.format(amount)
        }
        
        if (showSymbol) {
            // Add currency symbol for INR
            if (currencyCode.equals("INR", ignoreCase = true)) {
                return "₹$formatted"
            } else {
                val nf = formatterFor(currencyCode, 0, 0, locale)
                val s = nf.format(amount)
                return s
            }
        } else {
            // Remove currency symbol while keeping grouping and sign
            val symbol = try { Currency.getInstance(currencyCode).getSymbol(locale) } catch (_: Exception) { "" }
            return if (symbol.isNotBlank()) formatted.replace(symbol, "").trim() else formatted
        }
    }

    // Plain numeric without currency symbol, up to 2 decimals; omit .00 for whole numbers
    private val plain: DecimalFormat = DecimalFormat("0.##")
    fun formatNumericUpTo2(amount: Double): String = plain.format(amount)

    // No decimals version honoring currency and symbol choice
    // Explicit-format API (no decimals)
    fun formatNoDecimals(amount: Double, currencyCode: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val formatted = if (currencyCode.equals("INR", ignoreCase = true)) {
            formatIndianNumberNoDecimals(amount)
        } else {
            val nf = formatterFor(currencyCode, 0, 0, locale)
            nf.format(amount)
        }
        
        if (showSymbol) {
            // Add currency symbol for INR
            if (currencyCode.equals("INR", ignoreCase = true)) {
                return "₹$formatted"
            } else {
                val nf = formatterFor(currencyCode, 0, 0, locale)
                val s = nf.format(amount)
                return s
            }
        } else {
            // Remove currency symbol while keeping grouping and sign
            val symbol = try { Currency.getInstance(currencyCode).getSymbol(locale) } catch (_: Exception) { "" }
            return if (symbol.isNotBlank()) formatted.replace(symbol, "").trim() else formatted
        }
    }

    // Backward-compat (will be migrated out): Fixed INR formatting
    fun formatInr(amount: Double): String = format(amount)
}
