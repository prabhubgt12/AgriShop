package com.ledge.ledgerbook.util

import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import java.time.Instant
import java.time.ZoneId
import java.time.Period as JavaPeriod
import kotlin.math.pow

object LedgerInterest {
    enum class Type { SIMPLE, COMPOUND }
    enum class Period { MONTHLY, YEARLY }

    fun accruedInterest(entry: LedgerEntry, payments: List<LedgerPayment>, nowMillis: Long): Double {
        val type = when (entry.interestType.uppercase()) {
            "SIMPLE" -> Type.SIMPLE
            else -> Type.COMPOUND
        }
        val rateBasis = when (entry.period?.uppercase()) {
            "MONTHLY" -> Period.MONTHLY
            "YEARLY" -> Period.YEARLY
            else -> null
        }
        val compDuration = when (entry.compoundPeriod.uppercase()) {
            "MONTHLY" -> Period.MONTHLY
            else -> Period.YEARLY
        }
        val (yearsFrac, monthsFrac) = yearsAndMonthsFraction(entry.fromDate, nowMillis)
        return when (type) {
            Type.SIMPLE -> simpleInterest(entry.principal, entry.rateRupees, yearsFrac, monthsFrac, rateBasis ?: Period.MONTHLY)
            Type.COMPOUND -> compoundInterest(entry.principal, entry.rateRupees, yearsFrac, monthsFrac, rateBasis ?: Period.MONTHLY, compDuration)
        }
    }

    private fun simpleInterest(
        principal: Double,
        ratePercent: Double,
        yearsFraction: Double,
        monthsFraction: Double,
        basis: Period
    ): Double = when (basis) {
        Period.MONTHLY -> principal * (ratePercent / 100.0) * monthsFraction
        Period.YEARLY -> principal * (ratePercent / 100.0) * yearsFraction
    }

    private fun compoundInterest(
        principal: Double,
        ratePercent: Double,
        yearsFraction: Double,
        monthsFraction: Double,
        rateBasis: Period,
        compDuration: Period
    ): Double {
        val periods = when (compDuration) {
            Period.MONTHLY -> monthsFraction
            Period.YEARLY -> yearsFraction
        }
        val r = when (compDuration) {
            Period.MONTHLY -> when (rateBasis) {
                Period.MONTHLY -> ratePercent / 100.0
                Period.YEARLY -> (ratePercent / 100.0) / 12.0
            }
            Period.YEARLY -> when (rateBasis) {
                Period.YEARLY -> ratePercent / 100.0
                Period.MONTHLY -> (1.0 + (ratePercent / 100.0)).pow(12.0) - 1.0
            }
        }
        val compounded = principal * (1 + r).pow(periods)
        return compounded - principal
    }

    private fun yearsAndMonthsFraction(startMillis: Long, endMillis: Long): Pair<Double, Double> {
        if (endMillis <= startMillis) return 0.0 to 0.0
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        val p = JavaPeriod.between(startDate, endDate)
        val totalMonths = p.years * 12.0 + p.months + (p.days / 30.0)
        val totalYears = totalMonths / 12.0
        return totalYears to totalMonths
    }
}
