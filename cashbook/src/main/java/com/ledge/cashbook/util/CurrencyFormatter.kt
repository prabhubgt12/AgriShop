package com.ledge.cashbook.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {
    @Volatile private var currentCode: String = "INR"
    @Volatile private var currentShowSymbol: Boolean = true

    fun setConfig(code: String, showSymbol: Boolean) {
        currentCode = code
        currentShowSymbol = showSymbol
    }

    private fun formatterFor(code: String, minFrac: Int, maxFrac: Int, locale: Locale = Locale.getDefault()): NumberFormat {
        val nf = NumberFormat.getCurrencyInstance(locale)
        try {
            nf.currency = Currency.getInstance(code)
        } catch (_: Exception) {
            nf.currency = Currency.getInstance("INR")
        }
        nf.maximumFractionDigits = maxFrac
        nf.minimumFractionDigits = minFrac
        return nf
    }

    fun format(amount: Double, code: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val nf = formatterFor(code, 0, 2, locale)
        val s = nf.format(amount)
        if (showSymbol) return s
        val symbol = try { Currency.getInstance(code).getSymbol(locale) } catch (_: Exception) { "" }
        return if (symbol.isNotBlank()) s.replace(symbol, "").trim() else s
    }

    private val plain: DecimalFormat = DecimalFormat("0.##")
    fun formatNumericUpTo2(amount: Double): String = plain.format(amount)

    fun formatNoDecimals(amount: Double, code: String = currentCode, showSymbol: Boolean = currentShowSymbol, locale: Locale = Locale.getDefault()): String {
        val nf = formatterFor(code, 0, 0, locale)
        val s = nf.format(amount)
        if (showSymbol) return s
        val symbol = try { Currency.getInstance(code).getSymbol(locale) } catch (_: Exception) { "" }
        return if (symbol.isNotBlank()) s.replace(symbol, "").trim() else s
    }

    // Backward-compat helpers
    val inr: NumberFormat
        get() = formatterFor(currentCode, 0, 2, Locale.getDefault())

    fun formatInr(amount: Double): String = format(amount)
}
