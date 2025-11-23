package com.ledge.cashbook.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.local.entities.CategoryKeyword

@Database(
    entities = [CashAccount::class, CashTxn::class, Category::class, CategoryKeyword::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashDao(): CashDao
    abstract fun categoryDao(): com.ledge.cashbook.data.local.dao.CategoryDao
    abstract fun categoryKeywordDao(): com.ledge.cashbook.data.local.dao.CategoryKeywordDao
}
