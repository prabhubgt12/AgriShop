package com.ledge.cashbook.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.cashbook.data.local.dao.BusinessProfileDao
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.dao.ProductBarcodeDao
import com.ledge.cashbook.data.local.entities.BusinessProfile
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.local.entities.CategoryKeyword
import com.ledge.cashbook.data.local.entities.ProductBarcode
import com.ledge.cashbook.data.local.entities.RecurringTxn

@Database(
    entities = [CashAccount::class, CashTxn::class, Category::class, CategoryKeyword::class, RecurringTxn::class, BusinessProfile::class, ProductBarcode::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashDao(): CashDao
    abstract fun categoryDao(): com.ledge.cashbook.data.local.dao.CategoryDao
    abstract fun categoryKeywordDao(): com.ledge.cashbook.data.local.dao.CategoryKeywordDao
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun productBarcodeDao(): ProductBarcodeDao
}
