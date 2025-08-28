package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledger_payments")
data class LedgerPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entryId: Int,
    val amount: Double,
    val date: Long,
    val note: String? = null
)
