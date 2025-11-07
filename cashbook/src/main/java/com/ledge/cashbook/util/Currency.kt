package com.ledge.cashbook.util

object Currency {
    fun inr(amount: Double): String = CurrencyFormatter.format(amount)
}
