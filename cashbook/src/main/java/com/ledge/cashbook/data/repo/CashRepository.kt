package com.ledge.cashbook.data.repo

import com.ledge.cashbook.data.local.AppDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn
import kotlinx.coroutines.flow.Flow

class CashRepository(
    private val db: AppDatabase,
    private val dao: CashDao
) {
    fun accounts(): Flow<List<CashAccount>> = dao.accounts()
    suspend fun addAccount(name: String): Int = dao.insertAccount(CashAccount(name = name.trim())).toInt()
    suspend fun deleteAccount(acc: CashAccount) = dao.deleteAccount(acc)
    suspend fun getAccount(id: Int) = dao.getAccount(id)

    fun txns(accountId: Int): Flow<List<CashTxn>> = dao.txns(accountId)
    suspend fun addTxn(accountId: Int, date: Long, amount: Double, isCredit: Boolean, note: String?) =
        dao.insertTxn(CashTxn(accountId = accountId, date = date, amount = amount, isCredit = isCredit, note = note))
    suspend fun deleteTxn(txn: CashTxn) = dao.deleteTxn(txn)
    suspend fun clear(accountId: Int) = dao.clearTxns(accountId)

    suspend fun creditSum(accountId: Int): Double = dao.creditSum(accountId)
    suspend fun debitSum(accountId: Int): Double = dao.debitSum(accountId)
}
