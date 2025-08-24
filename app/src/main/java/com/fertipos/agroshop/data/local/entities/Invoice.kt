package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerId")]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val date: Long = System.currentTimeMillis(),
    val subtotal: Double = 0.0,
    val gstAmount: Double = 0.0,
    val total: Double = 0.0,
    val notes: String? = null
)
