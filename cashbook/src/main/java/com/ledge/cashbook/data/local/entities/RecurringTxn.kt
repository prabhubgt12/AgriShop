package com.ledge.cashbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Minimal recurring-transaction rule for CashBook.
 *
 * MVP scope:
 * - Monthly repeat only
 * - No end date
 * - Starts from the created transaction's date (normalized to start-of-day)
 * - Runs until explicitly stopped (isActive = false)
 */
@Entity(tableName = "recurring_txns")
data class RecurringTxn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: Int,
    val amount: Double,
    val isCredit: Boolean,
    val note: String?,
    val category: String?,
    /** First scheduled occurrence day (start-of-day millis). */
    val startDate: Long,
    /** Last day for which a transaction was generated (start-of-day millis). */
    val lastGeneratedDate: Long,
    /** Whether this rule should continue generating future months. */
    val isActive: Boolean = true
)

