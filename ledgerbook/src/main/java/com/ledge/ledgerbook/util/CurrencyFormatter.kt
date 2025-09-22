package com.ledge.ledgerbook.util

import java.text.NumberFormat
import java.util.Locale
import java.text.DecimalFormat

object CurrencyFormatter {
    private val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    fun formatInr(amount: Double): String = format.format(amount)

    // Plain numeric without currency symbol, up to 2 decimals; omit .00 for whole numbers
    private val plain: DecimalFormat = DecimalFormat("0.##")
    fun formatNumericUpTo2(amount: Double): String = plain.format(amount)
}
