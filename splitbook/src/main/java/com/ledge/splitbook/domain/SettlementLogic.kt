package com.ledge.splitbook.domain

import kotlin.math.abs
import kotlin.math.min

object SettlementLogic {
    data class Transfer(val fromMemberId: Long, val toMemberId: Long, val amount: Double)

    // Input: net balance per member (positive = creditor, negative = debtor)
    fun settle(nets: Map<Long, Double>, epsilon: Double = 0.01): List<Transfer> {
        val creditors = nets.filter { it.value > epsilon }
            .map { it.key to round2(it.value) }
            .toMutableList()
        val debtors = nets.filter { it.value < -epsilon }
            .map { it.key to round2(-it.value) } // store as positive owed
            .toMutableList()

        // Sort largest first to reduce number of transfers
        creditors.sortByDescending { it.second }
        debtors.sortByDescending { it.second }

        val transfers = mutableListOf<Transfer>()
        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (cId, cAmt) = creditors[ci]
            val (dId, dAmt) = debtors[di]
            val pay = round2(min(cAmt, dAmt))
            if (pay > 0.0) {
                transfers += Transfer(fromMemberId = dId, toMemberId = cId, amount = pay)
            }
            val cLeft = round2(cAmt - pay)
            val dLeft = round2(dAmt - pay)
            creditors[ci] = cId to cLeft
            debtors[di] = dId to dLeft
            if (cLeft <= epsilon) ci++
            if (dLeft <= epsilon) di++
        }
        return transfers
    }

    private fun round2(x: Double): Double = kotlin.math.round(x * 100.0) / 100.0
}
