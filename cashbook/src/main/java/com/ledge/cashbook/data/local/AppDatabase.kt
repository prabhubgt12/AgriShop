package com.ledge.cashbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn

@Database(
    entities = [CashAccount::class, CashTxn::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashDao(): CashDao
}
