package com.ledge.ledgerbook.util

import android.content.Context
import com.ledge.ledgerbook.R

object InterestRateFormatter {

    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
    }

    /**
     * NOTE: In this app, rate basis is encoded as:
     * - "MONTHLY" => Rupee-based rate
     * - "YEARLY"  => Percentage-based rate
     */
    fun format(rate: Double, rateBasis: String?): String {
        val ctx = context ?: return if (rateBasis?.equals("MONTHLY", ignoreCase = true) == true) {
            CurrencyFormatter.formatNoDecimals(rate)
        } else {
            "${CurrencyFormatter.formatNumericUpTo2(rate)}%"
        }

        val basis = (rateBasis ?: "MONTHLY").uppercase()
        return if (basis == "MONTHLY") {
            "${rate} ${ctx.getString(R.string.rate_basis_rupee)}"
        } else {
            "${CurrencyFormatter.formatNumericUpTo2(rate)}${ctx.getString(R.string.rate_basis_percentage)}"
        }
    }
}
