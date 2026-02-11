package com.ledge.ledgerbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ledge.ledgerbook.data.local.dao.LedgerDao
import com.ledge.ledgerbook.data.local.dao.LoanDao
import com.ledge.ledgerbook.data.local.dao.RdDao
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import com.ledge.ledgerbook.data.local.entities.LoanProfile
import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit

@Database(
    entities = [
        LedgerEntry::class,
        LedgerPayment::class,
        LoanProfile::class,
        RdAccount::class,
        RdDeposit::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun loanDao(): LoanDao
    abstract fun rdDao(): RdDao
}
