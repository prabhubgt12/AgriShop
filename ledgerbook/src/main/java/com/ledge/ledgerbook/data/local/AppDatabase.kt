package com.ledge.ledgerbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.ledgerbook.data.local.dao.LedgerDao
import com.ledge.ledgerbook.data.local.dao.LoanDao
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import com.ledge.ledgerbook.data.local.entities.LoanProfile

@Database(
    entities = [
        LedgerEntry::class,
        LedgerPayment::class,
        LoanProfile::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun loanDao(): LoanDao
}
