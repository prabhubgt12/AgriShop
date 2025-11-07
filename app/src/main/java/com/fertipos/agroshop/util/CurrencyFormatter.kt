package com.fertipos.agroshop.util

import java.text.NumberFormat
import java.text.DecimalFormat
import java.util.Locale
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
                // ignore invalid code, keep default
            }
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
        }
    }

    fun format(amount: Double, currencyCode: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val nf = formatterFor(currencyCode, 0, 2, locale)
        val s = nf.format(amount)
        if (showSymbol) return s
        val symbol = try { Currency.getInstance(currencyCode).getSymbol(locale) } catch (_: Exception) { "" }
        return if (symbol.isNotBlank()) s.replace(symbol, "").trim() else s
    }

    private val plain: DecimalFormat = DecimalFormat("0.##")
    fun formatNumericUpTo2(amount: Double): String = plain.format(amount)

    fun formatNoDecimals(amount: Double, currencyCode: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val nf = formatterFor(currencyCode, 0, 0, locale)
        val s = nf.format(amount)
        if (showSymbol) return s
        val symbol = try { Currency.getInstance(currencyCode).getSymbol(locale) } catch (_: Exception) { "" }
        return if (symbol.isNotBlank()) s.replace(symbol, "").trim() else s
    }

    // Backward-compat helpers (legacy callers)
    val inr: NumberFormat
        get() = formatterFor(currentCode, 0, 2, Locale.getDefault())

    fun formatInr(amount: Double): String = format(amount)
}
