package com.ledge.ledgerbook.util

import kotlin.math.floor

object NumberToWords {
    private data class LangPack(
        val zero: String,
        val andWord: String,
        val rupees: String,
        val paise: String,
        val hundred: String,
        val thousand: String,
        val lakh: String,
        val crore: String,
        val belowTwenty: Array<String>,
        val tens: Array<String>
    )

    private val EN = LangPack(
        zero = "Zero",
        andWord = "and",
        rupees = "Rupees",
        paise = "Paise",
        hundred = "Hundred",
        thousand = "Thousand",
        lakh = "Lakh",
        crore = "Crore",
        belowTwenty = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        ),
        tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
    )

    private val HI = LangPack(
        zero = "शून्य",
        andWord = "और",
        rupees = "रुपये",
        paise = "पैसे",
        hundred = "सौ",
        thousand = "हज़ार",
        lakh = "लाख",
        crore = "करोड़",
        belowTwenty = arrayOf(
            "", "एक", "दो", "तीन", "चार", "पांच", "छह", "सात", "आठ", "नौ",
            "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस"
        ),
        tens = arrayOf("", "", "बीस", "तीस", "चालीस", "पचास", "साठ", "सत्तर", "अस्सी", "नब्बे")
    )

    private val KN = LangPack(
        zero = "ಸೊನ್ನೆ",
        andWord = "ಮತ್ತು",
        rupees = "ರೂಪಾಯಿ",
        paise = "ಪೈಸೆ",
        hundred = "ನೂರು",
        thousand = "ಸಾವಿರ",
        lakh = "ಲಕ್ಷ",
        crore = "ಕೋಟಿ",
        belowTwenty = arrayOf(
            "", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು",
            "ಹತ್ತು", "ಹನ್ನೊಂದು", "ಹನ್ನೆರಡು", "ಹದಿಮೂರು", "ಹದಿನಾಲ್ಕು", "ಹದಿನೈದು", "ಹದಿನಾರು", "ಹದಿನೇಳು", "ಹದಿನೆಂಟು", "ಹತ್ತೊಂಬತ್ತು"
        ),
        tens = arrayOf("", "", "ಇಪ್ಪತ್ತು", "ಮೂವತ್ತು", "ನಲವತ್ತು", "ಐವತ್ತು", "ಅರವತ್ತು", "ಎಪ್ಪತ್ತು", "ಎಂಭತ್ತು", "ತೊಂಬತ್ತು")
    )

    private fun packFor(lang: String?): LangPack = when (lang?.lowercase()) {
        "hi", "hi-in" -> HI
        "kn", "kn-in" -> KN
        else -> EN
    }

    // Indian numbering system groups: Crore, Lakh, Thousand, Hundred, Rest
    fun inIndianSystem(amount: Double, lang: String? = null): String {
        val lp = packFor(lang)
        val totalPaise = floor(amount * 100.0 + 0.5).toLong()
        val rupees = totalPaise / 100
        val paise = (totalPaise % 100).toInt()

        if (rupees == 0L && paise == 0) return "${lp.zero} ${lp.rupees}"

        val words = StringBuilder()
        if (rupees > 0) {
            words.append(convertRupees(rupees, lp)).append(" ${lp.rupees}")
        }
        if (paise > 0) {
            if (words.isNotEmpty()) words.append(" ${lp.andWord} ")
            words.append(convertTwoDigits(paise, lp)).append(" ${lp.paise}")
        }
        return words.toString()
    }

    private fun convertRupees(n: Long, lp: LangPack): String {
        if (n == 0L) return lp.zero
        val parts = ArrayList<String>()

        var num = n
        val crore = num / 10000000
        if (crore > 0) {
            parts += convertUpToThreeDigits(crore.toInt(), lp) + " ${lp.crore}"
            num %= 10000000
        }
        val lakh = num / 100000
        if (lakh > 0) {
            parts += convertUpToThreeDigits(lakh.toInt(), lp) + " ${lp.lakh}"
            num %= 100000
        }
        val thousand = num / 1000
        if (thousand > 0) {
            parts += convertUpToThreeDigits(thousand.toInt(), lp) + " ${lp.thousand}"
            num %= 1000
        }
        val hundred = num / 100
        if (hundred > 0) {
            parts += lp.belowTwenty[hundred.toInt()] + " ${lp.hundred}"
            num %= 100
        }
        if (num > 0) {
            parts += convertTwoDigits(num.toInt(), lp)
        }
        return parts.joinToString(separator = " ").trim()
    }

    private fun convertUpToThreeDigits(n: Int, lp: LangPack): String {
        var num = n
        val parts = ArrayList<String>()
        val hundred = num / 100
        if (hundred > 0) {
            parts += lp.belowTwenty[hundred] + " ${lp.hundred}"
            num %= 100
        }
        if (num > 0) parts += convertTwoDigits(num, lp)
        return parts.joinToString(" ")
    }

    private fun convertTwoDigits(n: Int, lp: LangPack): String {
        if (n < 20) return lp.belowTwenty[n]
        val t = n / 10
        val r = n % 10
        return (lp.tens[t] + if (r > 0) " " + lp.belowTwenty[r] else "").trim()
    }
}
