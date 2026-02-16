package com.ledge.cashbook.data.repo

import androidx.room.withTransaction
import com.ledge.cashbook.data.local.AppDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.entities.CashAccount
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.data.local.entities.RecurringTxn
import kotlinx.coroutines.flow.Flow

class CashRepository(
    private val db: AppDatabase,
    private val dao: CashDao
) {
    fun accounts(): Flow<List<CashAccount>> = dao.accounts()
    suspend fun addAccount(name: String): Int = dao.insertAccount(CashAccount(name = name.trim())).toInt()
    suspend fun deleteAccount(acc: CashAccount) = dao.deleteAccount(acc)
    suspend fun getAccount(id: Int) = dao.getAccount(id)
    suspend fun updateAccountName(id: Int, newName: String) {
        val acc = dao.getAccount(id)
        if (acc != null) dao.updateAccount(acc.copy(name = newName.trim()))
    }

    fun txns(accountId: Int): Flow<List<CashTxn>> = dao.txns(accountId)

    suspend fun addTxn(
        accountId: Int,
        date: Long,
        amount: Double,
        isCredit: Boolean,
        note: String?,
        attachmentUri: String?,
        category: String?
    ) = dao.insertTxn(
        CashTxn(
            accountId = accountId,
            date = date,
            amount = amount,
            isCredit = isCredit,
            note = note,
            attachmentUri = attachmentUri,
            category = category
        )
    )

    /**
    * Create a normal transaction and (optionally) attach a monthly-recurring rule that
    * starts from the transaction date (normalized to start-of-day) and runs until stopped.
    */
    suspend fun addTxnWithMonthlyRecurring(
        accountId: Int,
        date: Long,
        amount: Double,
        isCredit: Boolean,
        note: String?,
        attachmentUri: String?,
        category: String?
    ) {
        db.withTransaction {
            val txnId = dao.insertTxn(
                CashTxn(
                    accountId = accountId,
                    date = date,
                    amount = amount,
                    isCredit = isCredit,
                    note = note,
                    attachmentUri = attachmentUri,
                    category = category
                )
            ).toInt()
            val dayTrunc = truncateToDay(date)
            val ruleId = dao.insertRecurring(
                RecurringTxn(
                    accountId = accountId,
                    amount = amount,
                    isCredit = isCredit,
                    note = note,
                    category = category,
                    startDate = dayTrunc,
                    lastGeneratedDate = dayTrunc,
                    isActive = true
                )
            ).toInt()
            dao.setTxnRecurringId(txnId, ruleId)
        }
    }
    suspend fun deleteTxn(txn: CashTxn) = dao.deleteTxn(txn)
    suspend fun updateTxn(txn: CashTxn) = dao.updateTxn(txn)

    suspend fun stopRecurringFor(txn: CashTxn) {
        val recurringId = txn.recurringId ?: return
        val rule = dao.getRecurring(recurringId) ?: return
        if (!rule.isActive) return
        dao.updateRecurring(rule.copy(isActive = false))
    }

    suspend fun isRecurringActive(recurringId: Int): Boolean {
        return dao.isRecurringActive(recurringId) ?: false
    }
    suspend fun clear(accountId: Int) = dao.clearTxns(accountId)
    suspend fun moveTxns(ids: List<Int>, to: Int) = dao.moveTxns(ids, to)

    suspend fun creditSum(accountId: Int): Double = dao.creditSum(accountId)
    suspend fun debitSum(accountId: Int): Double = dao.debitSum(accountId)

    // Aggregates across all accounts
    fun totalCredit(): Flow<Double> = dao.totalCredit()
    fun totalDebit(): Flow<Double> = dao.totalDebit()
    fun dueAccountsCount(): Flow<Int> = dao.dueAccountsCount()

    // --- Recurring generation on app open ---

    suspend fun generateDueRecurringTxns(todayMillis: Long = System.currentTimeMillis()) {
        val todayDay = truncateToDay(todayMillis)
        val rules = dao.activeRecurring()
        if (rules.isEmpty()) return
        db.withTransaction {
            for (rule in rules) {
                var last = rule.lastGeneratedDate
                while (true) {
                    val next = addOneMonth(last)
                    if (next > todayDay) break
                    // Prevent duplicates for (recurringId, date)
                    val existing = dao.countRecurringTxnOn(rule.id, next)
                    if (existing == 0) {
                        dao.insertTxn(
                            CashTxn(
                                accountId = rule.accountId,
                                date = next,
                                amount = rule.amount,
                                isCredit = rule.isCredit,
                                note = rule.note,
                                attachmentUri = null,
                                category = rule.category,
                                recurringId = rule.id
                            )
                        )
                    }
                    last = next
                }
                if (last != rule.lastGeneratedDate) {
                    dao.updateRecurring(rule.copy(lastGeneratedDate = last))
                }
            }
        }
    }

    companion object {
        fun truncateToDay(millis: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun addOneMonth(dayMillis: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = dayMillis
            cal.add(java.util.Calendar.MONTH, 1)
            return cal.timeInMillis
        }
    }
}
