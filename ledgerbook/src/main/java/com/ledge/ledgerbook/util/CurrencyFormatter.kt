package com.ledge.ledgerbook.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    fun formatInr(amount: Double): String = format.format(amount)
}
