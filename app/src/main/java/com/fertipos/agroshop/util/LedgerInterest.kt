package com.fertipos.agroshop.util

import com.fertipos.agroshop.data.local.entities.LedgerEntry
import com.fertipos.agroshop.data.local.entities.LedgerPayment
import java.time.Instant
import java.time.LocalDate
import java.time.Period as JavaPeriod
import java.time.ZoneId
import kotlin.math.pow

object LedgerInterest {
    enum class Type { SIMPLE, COMPOUND }
    enum class Period { MONTHLY, YEARLY }

    fun accruedInterest(entry: LedgerEntry, payments: List<LedgerPayment>, nowMillis: Long): Double {
        val type = when (entry.interestType.uppercase()) {
            "SIMPLE" -> Type.SIMPLE
            else -> Type.COMPOUND
        }
        // Rate basis applies to both Simple and Compound
        val rateBasis = when (entry.period?.uppercase()) {
            "MONTHLY" -> Period.MONTHLY
            "YEARLY" -> Period.YEARLY
            else -> null
        }
        // Compounding duration applies only to Compound
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
    ): Double {
        // Basis = MONTHLY => use total months as multiplier
        // Basis = YEARLY  => use total years as multiplier
        return when (basis) {
            Period.MONTHLY -> {
                val totalMonths = monthsFraction // already includes years*12 + months + partial
                principal * (ratePercent / 100.0) * totalMonths
            }
            Period.YEARLY -> {
                val totalYears = yearsFraction
                principal * (ratePercent / 100.0) * totalYears
            }
        }
    }

    private fun compoundInterest(
        principal: Double,
        ratePercent: Double,
        yearsFraction: Double,
        monthsFraction: Double,
        rateBasis: Period,      // Monthly or Yearly rate input
        compDuration: Period    // Monthly or Yearly compounding frequency
    ): Double {
        val periods = when (compDuration) {
            Period.MONTHLY -> monthsFraction
            Period.YEARLY -> yearsFraction
        }
        // Per-compounding-period rate
        val r = when (compDuration) {
            Period.MONTHLY -> when (rateBasis) {
                Period.MONTHLY -> ratePercent / 100.0                  // monthly rate applied monthly
                Period.YEARLY -> (ratePercent / 100.0) / 12.0          // yearly rate converted to monthly
            }
            Period.YEARLY -> when (rateBasis) {
                Period.YEARLY -> ratePercent / 100.0                   // yearly rate applied yearly
                Period.MONTHLY -> (1.0 + (ratePercent / 100.0)).pow(12.0) - 1.0 // monthly -> effective yearly
            }
        }
        val compounded = principal * (1 + r).pow(periods)
        return compounded - principal
    }

    // Compute precise calendar difference and return (yearsFraction, monthsFraction)
    // monthsFraction = totalMonths including partial month as days/30
    // yearsFraction = totalYears including partial as months/12 (built from monthsFraction)
    private fun yearsAndMonthsFraction(startMillis: Long, endMillis: Long): Pair<Double, Double> {
        if (endMillis <= startMillis) return 0.0 to 0.0
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

        var p = JavaPeriod.between(startDate, endDate)
        // Period gives years, months, days where days < length of month
        val years = p.years
        val months = p.months
        val days = p.days
        val totalMonths = years * 12.0 + months + (days / 30.0)
        val totalYears = totalMonths / 12.0
        return totalYears to totalMonths
    }
}
