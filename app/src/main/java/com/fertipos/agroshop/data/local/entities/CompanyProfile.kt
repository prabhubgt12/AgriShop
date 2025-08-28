package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Shivam Agro Traders",
    val addressLine1: String = "Canal Road, Saicamp",
    val addressLine2: String = "",
    val city: String = "Byagawat",
    val state: String = "",
    val pincode: String = "584123",
    val gstin: String = "",
    val phone: String = "901948254",
    val email: String = "",
    // Persist image URI as string; UI will resolve and load image
    // Default points to a bundled drawable: res/drawable/ic_shivam_logo.png
    val logoUri: String = "android.resource://com.fertipos.agroshop/drawable/ic_shivam_logo",
    // Comma-separated product type list used to populate the Type dropdown
    // Default keeps the existing hardcoded options
    val productTypesCsv: String = "Fertilizer,Pecticide,Fungi,GP,Other",
    // Configurable low stock threshold used by Product screen to color stock chip
    val lowStockThreshold: Int = 10
)
