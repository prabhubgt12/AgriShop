package com.ledge.ledgerbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rd_accounts")
data class RdAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val installmentAmount: Double,
    val annualRatePercent: Double,
    val startDateMillis: Long,
    val tenureMonths: Int,
    val autoPay: Boolean = false,
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
