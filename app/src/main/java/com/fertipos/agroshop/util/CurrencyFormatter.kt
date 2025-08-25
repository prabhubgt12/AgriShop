package com.fertipos.agroshop.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {
    private val inLocale: Locale = Locale("en", "IN")

    val inr: NumberFormat
        get() = NumberFormat.getCurrencyInstance(inLocale).apply {
            currency = Currency.getInstance("INR")
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }

    fun formatInr(amount: Double): String = inr.format(amount)
}
