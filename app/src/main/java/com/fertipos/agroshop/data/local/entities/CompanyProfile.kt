package com.fertipos.agroshop.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company_profile")
data class CompanyProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Simple Shop",
    val addressLine1: String = "Address Line 1",
    val addressLine2: String = "Address Line 2",
    val city: String = "City Name",
    val state: String = "Karnataka",
    val pincode: String = "123456",
    val gstin: String = "",
    val phone: String = "1234567890",
    val email: String = "email@yourshopemail.com",
    // Persist image URI as string; UI will resolve and load image
    // Default points to a bundled drawable: res/drawable/ic_shivam_logo.png
    val logoUri: String = "android.resource://com.fertipos.agroshop/drawable/ic_shivam_logo",
    // Comma-separated product type list used to populate the Type dropdown
    // Default keeps the existing hardcoded options
    val productTypesCsv: String = "Fertilizer,Pecticide,Fungi,GP,Other",
    // Comma-separated unit list used to populate the Unit dropdown
    // Default keeps the existing hardcoded options
    val unitsCsv: String = "Kg,Pcs,L",
    // Configurable low stock threshold used by Product screen to color stock chip
    val lowStockThreshold: Int = 10
)
