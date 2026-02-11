package com.ledge.ledgerbook.util

object InterestRateFormatter {
    /**
     * NOTE: In this app, rate basis is encoded as:
     * - "MONTHLY" => Rupee-based rate
     * - "YEARLY"  => Percentage-based rate
     */
    fun format(rate: Double, rateBasis: String?): String {
        val basis = (rateBasis ?: "MONTHLY").uppercase()
        return if (basis == "MONTHLY") {
            CurrencyFormatter.formatNoDecimals(rate)
        } else {
            "${CurrencyFormatter.formatNumericUpTo2(rate)}%"
        }
    }
}
