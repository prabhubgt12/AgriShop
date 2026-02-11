package com.ledge.ledgerbook.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rd_deposits",
    indices = [
        Index(value = ["rdAccountId", "dueDateMillis"], unique = true)
    ]
)
data class RdDeposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rdAccountId: Long,
    val dueDateMillis: Long,
    val paidDateMillis: Long? = null,
    val amountPaid: Double,
    val note: String? = null
)
