package com.ledge.splitbook.util

import android.net.Uri

fun buildUpiUri(vpa: String, payee: String, amount: Double, note: String): Uri {
    val amt = String.format("%.2f", amount)
    return Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", vpa)
        .appendQueryParameter("pn", payee)
        .appendQueryParameter("am", amt)
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note)
        .build()
}
