package com.ledge.cashbook.util

import java.text.NumberFormat
import java.util.Locale

object Currency {
    private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
    fun inr(amount: Double): String = inr.format(amount)
}
