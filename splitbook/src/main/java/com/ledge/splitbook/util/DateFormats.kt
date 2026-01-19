package com.ledge.splitbook.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormats {
    private val displayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    // Common input patterns we might encounter from SQLite or ISO strings
    private val patterns: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), // SQLite CURRENT_TIMESTAMP default
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,               // 2026-01-19T12:03:00
        DateTimeFormatter.ISO_OFFSET_DATE_TIME               // 2026-01-19T12:03:00Z or +05:30
    )

    fun formatExpenseDate(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        // Try LocalDate first
        try {
            val d = LocalDate.parse(raw)
            return d.format(displayFmt)
        } catch (_: Throwable) {}
        // Try known date-time patterns
        for (p in patterns) {
            try {
                val dt = LocalDateTime.parse(raw, p)
                return dt.toLocalDate().format(displayFmt)
            } catch (_: Throwable) {}
        }
        // Fallback: return as-is
        return raw
    }
}
