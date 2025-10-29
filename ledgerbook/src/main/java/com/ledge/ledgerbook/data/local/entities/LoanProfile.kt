package com.ledge.ledgerbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loan_profiles")
data class LoanProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // Home, Personal, Auto
    val name: String,
    val principal: Double,
    val annualRatePercent: Double,
    val tenureMonths: Int,
    val firstEmiDateMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
