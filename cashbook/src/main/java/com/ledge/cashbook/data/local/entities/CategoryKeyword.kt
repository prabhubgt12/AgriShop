package com.ledge.cashbook.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_keywords",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["keyword"], unique = false)
    ]
)
data class CategoryKeyword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val keyword: String,
    val weight: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
