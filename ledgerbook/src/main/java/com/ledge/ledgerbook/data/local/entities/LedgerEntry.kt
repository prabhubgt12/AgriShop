package com.ledge.ledgerbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // LEND or BORROW
    val name: String,
    val principal: Double,
    val interestType: String, // SIMPLE or COMPOUND
    val period: String?, // MONTHLY or YEARLY (rate basis)
    val compoundPeriod: String = "MONTHLY", // for COMPOUND: MONTHLY or YEARLY
    val rateRupees: Double,
    val fromDate: Long,
    val notes: String? = null,
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
