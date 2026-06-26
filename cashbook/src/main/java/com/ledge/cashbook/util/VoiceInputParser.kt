package com.ledge.cashbook.util

/**
 * Parses voice input to extract item name and amount.
 * Supports formats like:
 * - "Milk 55" -> item="Milk", amount=55.0
 * - "55 Milk" -> item="Milk", amount=55.0
 * - "Bought milk for 55 rupees" -> item="Bought milk for", amount=55.0
 * - "Milk fifty five" -> item="Milk", amount=55.0 (basic number words)
 */
object VoiceInputParser {
    
    private val numberWords = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100
    )
    
    /**
     * Parses voice input and returns a pair of (item, amount)
     * @param input Voice input text
     * @return Pair where first is item name and second is amount (null if parsing fails)
     */
    fun parse(input: String): Pair<String, Double?> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "" to null
        
        // Try to find a numeric amount first (digits with optional decimal)
        val digitPattern = """(\d+\.?\d*)""".toRegex()
        val digitMatch = digitPattern.find(trimmed)
        
        if (digitMatch != null) {
            val amount = digitMatch.groupValues[1].toDoubleOrNull()
            if (amount != null && amount > 0) {
                // Remove the number from the string to get the item
                val item = trimmed.replace(digitMatch.value, "").trim()
                    .replace(Regex("""\b(for|rupees|rs|₹)\b"""), "").trim()
                return item to amount
            }
        }
        
        // Try to find number words
        val words = trimmed.lowercase().split(Regex("""\s+"""))
        var totalAmount = 0.0
        var currentAmount = 0.0
        var itemParts = mutableListOf<String>()
        
        for (word in words) {
            val numValue = numberWords[word]
            if (numValue != null) {
                // Handle compound numbers like "twenty five"
                if (numValue >= 20 && numValue < 100) {
                    currentAmount += numValue
                } else if (numValue == 100) {
                    currentAmount *= 100
                } else {
                    currentAmount += numValue
                }
            } else if (word in listOf("for", "rupees", "rs", "₹")) {
                // Skip these words
                continue
            } else {
                // This is likely part of the item name
                if (currentAmount > 0) {
                    totalAmount += currentAmount
                    currentAmount = 0.0
                }
                itemParts.add(word)
            }
        }
        
        // Add any remaining amount
        if (currentAmount > 0) {
            totalAmount += currentAmount
        }
        
        if (totalAmount > 0) {
            val item = itemParts.joinToString(" ").trim()
            return item to totalAmount
        }
        
        // If no amount found, return the input as item with null amount
        return trimmed to null
    }
    
    /**
     * Checks if the input contains a valid amount
     */
    fun hasValidAmount(input: String): Boolean {
        val (_, amount) = parse(input)
        return amount != null && amount > 0
    }
}
