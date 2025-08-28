package com.ledge.ledgerbook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Update
    suspend fun updateEntry(entry: LedgerEntry)

    @Delete
    suspend fun deleteEntry(entry: LedgerEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: LedgerPayment): Long

    @Query("DELETE FROM ledger_payments WHERE entryId = :entryId")
    suspend fun clearPaymentsFor(entryId: Int)

    @Query("SELECT * FROM ledger_entries ORDER BY fromDate DESC, id DESC")
    fun getAllEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getEntryOnce(id: Int): LedgerEntry?

    @Query("SELECT * FROM ledger_payments WHERE entryId = :entryId ORDER BY date ASC, id ASC")
    suspend fun getPaymentsFor(entryId: Int): List<LedgerPayment>

    @Query("SELECT IFNULL(SUM(amount), 0) FROM ledger_payments WHERE entryId = :entryId")
    suspend fun getPaidTotalFor(entryId: Int): Double
}
