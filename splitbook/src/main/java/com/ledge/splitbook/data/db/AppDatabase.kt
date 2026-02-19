package com.ledge.splitbook.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ledge.splitbook.data.dao.*
import com.ledge.splitbook.data.entity.*

@Database(
    entities = [
        CategoryEntity::class,
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        SettlementEntity::class,
        TripDayEntity::class,
        PlaceEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(SplitTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun expenseSplitDao(): ExpenseSplitDao
    abstract fun settlementDao(): SettlementDao
    abstract fun tripDayDao(): TripDayDao
    abstract fun placeDao(): PlaceDao

    companion object {
        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "splitbook.db"
        )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
