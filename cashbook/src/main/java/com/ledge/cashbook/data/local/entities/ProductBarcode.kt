package com.ledge.cashbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_barcodes")
data class ProductBarcode(
    @PrimaryKey val barcode: String,
    val name: String,
    val price: Double? = null,
    val category: String = ""
)
