package com.ledge.cashbook.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_profile")
data class BusinessProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Cashbook",
    // Persist image URI as string; UI will resolve and load image
    // Default is empty (no logo)
    val logoUri: String = ""
)
