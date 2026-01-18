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
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        SettlementEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(SplitTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun expenseSplitDao(): ExpenseSplitDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "splitbook.db"
        ).fallbackToDestructiveMigration() // MVP; add real migrations later
            .build()
    }
}
