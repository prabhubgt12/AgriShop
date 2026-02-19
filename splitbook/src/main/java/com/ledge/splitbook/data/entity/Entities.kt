package com.ledge.splitbook.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "groups"
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null
)

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0.0") val deposit: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val isAdmin: Boolean = false
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByMemberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("groupId"), Index("paidByMemberId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val amount: Double,
    val category: String,
    val paidByMemberId: Long,
    val note: String? = null,
    val placeId: Long? = null,
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null
)

enum class SplitType { EQUAL, CUSTOM_AMOUNT, PERCENTAGE }

@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId"), Index("memberId")]
)
data class ExpenseSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val memberId: Long,
    val type: SplitType,
    // For CUSTOM_AMOUNT: value is amount
    // For PERCENTAGE: value is percentage (0..100)
    // For EQUAL: value may be ignored
    val value: Double
)

@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromMemberId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["toMemberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("groupId"), Index("fromMemberId"), Index("toMemberId")]
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val fromMemberId: Long,
    val toMemberId: Long,
    val amount: Double,
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP") val createdAt: String? = null,
    val status: String = "completed" // pending|completed (MVP: default completed when marked)
)

@Entity(
    tableName = "trip_days",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class TripDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val dayNumber: Int,
    val date: String? = null
)

@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TripDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("dayId")]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val dayId: Long,
    val name: String
)
