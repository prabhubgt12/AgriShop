package com.ledge.ledgerbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.ledgerbook.data.local.dao.LedgerDao
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment

@Database(
    entities = [
        LedgerEntry::class,
        LedgerPayment::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
}
