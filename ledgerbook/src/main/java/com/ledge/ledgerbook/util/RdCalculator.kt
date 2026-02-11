package com.ledge.ledgerbook.util

import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

object RdCalculator {
    data class Summary(
        val totalDeposited: Double,
        val accruedInterest: Double,
        val totalValue: Double
    )

    fun maturityDateMillis(account: RdAccount): Long {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(account.startDateMillis).atZone(zone).toLocalDate()
        val maturity = start.plusMonths(account.tenureMonths.toLong())
        return maturity.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun summaryAsOf(account: RdAccount, deposits: List<RdDeposit>, asOfMillis: Long): Summary {
        val paid = deposits.filter { it.paidDateMillis != null }
        val totalDeposited = paid.sumOf { it.amountPaid }
        val accrued = paid.sumOf { d ->
            accruedInterestForDeposit(
                principal = d.amountPaid,
                annualRatePercent = account.annualRatePercent,
                // Accrue from the scheduled due date for the installment.
                // Even if the user marks it paid later, RD interest is typically computed from its installment date.
                fromMillis = d.dueDateMillis,
                toMillis = asOfMillis
            )
        }
        return Summary(totalDeposited = totalDeposited, accruedInterest = accrued, totalValue = totalDeposited + accrued)
    }

    fun maturitySummary(account: RdAccount, deposits: List<RdDeposit>): Summary {
        val maturityMillis = maturityDateMillis(account)
        return summaryAsOf(account, deposits, maturityMillis)
    }

    private fun accruedInterestForDeposit(
        principal: Double,
        annualRatePercent: Double,
        fromMillis: Long,
        toMillis: Long
    ): Double {
        if (principal <= 0.0) return 0.0
        if (toMillis <= fromMillis) return 0.0

        val zone = ZoneId.systemDefault()
        val fromDate: LocalDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val toDate: LocalDate = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(fromDate, toDate).toDouble()
        if (days <= 0.0) return 0.0

        val r = annualRatePercent / 100.0
        val tYears = days / 365.0
        val n = 4.0 // quarterly compounding
        val amount = principal * (1.0 + r / n).pow(n * tYears)
        return amount - principal
    }
}
