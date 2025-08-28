package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // LEND or BORROW
    val name: String,
    val principal: Double,
    val interestType: String, // SIMPLE or COMPOUND
    val period: String?, // Rate Basis: MONTHLY or YEARLY (applies to both types)
    val compoundPeriod: String = "MONTHLY", // Duration Type for COMPOUND only: MONTHLY or YEARLY
    val rateRupees: Double, // interest in rupees (e.g., 1, 1.5)
    val fromDate: Long, // epoch millis
    val notes: String? = null,
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
