package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String = "Fertilizer", // e.g., Fertilizer, Pesticide
    val unit: String, // e.g., Kg, Litre, Packet
    val sellingPrice: Double,
    val purchasePrice: Double,
    val stockQuantity: Double = 0.0,
    val gstPercent: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
