package com.ledge.splitbook.domain

import kotlin.math.abs
import kotlin.math.floor

object SplitLogic {
    data class MemberShare(val memberId: Long, val amount: Double)

    fun splitEqual(total: Double, memberIds: List<Long>): List<MemberShare> {
        require(total > 0) { "Amount must be > 0" }
        require(memberIds.isNotEmpty()) { "Members required" }
        val base = floor((total / memberIds.size) * 100) / 100.0
        val remainder = ((total * 100).toLong() - (base * 100).toLong() * memberIds.size).toInt()
        val sorted = memberIds.sorted()
        return sorted.mapIndexed { idx, id ->
            val extra = if (idx < remainder) 0.01 else 0.0
            MemberShare(id, round2(base + extra))
        }
    }

    fun splitCustom(total: Double, perMember: Map<Long, Double>): List<MemberShare> {
        require(total > 0)
        require(perMember.isNotEmpty())
        val sum = perMember.values.sum()
        require(abs(sum - total) < 0.01) { "Custom amounts must sum to total" }
        return perMember.toSortedMap().map { (id, amt) -> MemberShare(id, round2(amt)) }
    }

    fun splitPercentage(total: Double, percentages: Map<Long, Double>): List<MemberShare> {
        require(total > 0)
        require(percentages.isNotEmpty())
        val totalPct = percentages.values.sum()
        require(abs(totalPct - 100.0) < 0.01) { "Percentages must total 100" }
        val shares = percentages.toSortedMap().map { (id, pct) ->
            id to floor((total * pct / 100.0) * 100) / 100.0
        }.toMutableList()
        var allocated = shares.sumOf { it.second }
        var centsRemainder = ((total * 100).toLong() - (allocated * 100).toLong()).toInt()
        var i = 0
        while (centsRemainder > 0 && shares.isNotEmpty()) {
            val idx = i % shares.size
            shares[idx] = shares[idx].first to round2(shares[idx].second + 0.01)
            allocated = round2(allocated + 0.01)
            centsRemainder--
            i++
        }
        return shares.map { MemberShare(it.first, it.second) }
    }

    fun round2(x: Double): Double = kotlin.math.round(x * 100.0) / 100.0
}
