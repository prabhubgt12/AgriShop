package com.ledge.cashbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_txns")
data class CashTxn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val date: Long,
    val amount: Double,
    val isCredit: Boolean,
    val note: String? = null,
    val attachmentUri: String? = null,
    val category: String? = null
)
