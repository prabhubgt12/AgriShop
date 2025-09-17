package com.ledge.cashbook.data.local.dao

import androidx.room.*
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {
    // Accounts
    @Query("SELECT * FROM cash_accounts ORDER BY name COLLATE NOCASE")
    fun accounts(): Flow<List<CashAccount>>

    @Insert
    suspend fun insertAccount(account: CashAccount): Long

    @Delete
    suspend fun deleteAccount(account: CashAccount)

    @Query("SELECT * FROM cash_accounts WHERE id = :id")
    suspend fun getAccount(id: Int): CashAccount?

    // Transactions
    @Query("SELECT * FROM cash_txns WHERE accountId = :accountId ORDER BY date ASC, id ASC")
    fun txns(accountId: Int): Flow<List<CashTxn>>

    @Insert
    suspend fun insertTxn(txn: CashTxn): Long

    @Delete
    suspend fun deleteTxn(txn: CashTxn)

    @Query("DELETE FROM cash_txns WHERE accountId = :accountId")
    suspend fun clearTxns(accountId: Int)

    @Query("SELECT IFNULL(SUM(CASE WHEN isCredit THEN amount ELSE 0 END), 0) FROM cash_txns WHERE accountId = :accountId")
    suspend fun creditSum(accountId: Int): Double

    @Query("SELECT IFNULL(SUM(CASE WHEN isCredit THEN 0 ELSE amount END), 0) FROM cash_txns WHERE accountId = :accountId")
    suspend fun debitSum(accountId: Int): Double
}
