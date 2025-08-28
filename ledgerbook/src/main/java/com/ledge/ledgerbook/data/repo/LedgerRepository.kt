package com.ledge.ledgerbook.data.repo

import com.ledge.ledgerbook.data.local.AppDatabase
import com.ledge.ledgerbook.data.local.dao.LedgerDao
import com.ledge.ledgerbook.data.local.entities.LedgerEntry
import com.ledge.ledgerbook.data.local.entities.LedgerPayment
import com.ledge.ledgerbook.util.LedgerInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class LedgerRepository(
    private val db: AppDatabase,
    private val dao: LedgerDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun entries(): Flow<List<LedgerEntryWithComputed>> = dao.getAllEntries()
        .flatMapLatest { list ->
            flow {
                val computed = withContext(Dispatchers.IO) {
                    list.map { e ->
                        // Consider only payments strictly after the current baseline (fromDate)
                        val payments = dao.getPaymentsFor(e.id).filter { it.date > e.fromDate }
                        val now = System.currentTimeMillis()
                        val accrued = LedgerInterest.accruedInterest(e, payments, now)
                        val paid = payments.sumOf { it.amount }
                        val total = e.principal + accrued
                        val outstanding = total - paid
                        LedgerEntryWithComputed(e, accrued, paid, outstanding, total)
                    }
                }
                emit(computed)
            }
        }

    suspend fun getEntry(id: Int): LedgerEntry? = dao.getEntryOnce(id)

    suspend fun addEntry(entry: LedgerEntry): Int = dao.insertEntry(entry).toInt()

    suspend fun updateEntry(entry: LedgerEntry) = dao.updateEntry(entry.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteEntry(entry: LedgerEntry) {
        dao.clearPaymentsFor(entry.id)
        dao.deleteEntry(entry)
    }

    suspend fun addPayment(entryId: Int, amount: Double, date: Long, note: String? = null) {
        dao.insertPayment(LedgerPayment(entryId = entryId, amount = amount, date = date, note = note))
    }

    suspend fun computeAt(entryId: Int, atMillis: Long): Triple<Double, Double, Double> {
        val entry = dao.getEntryOnce(entryId) ?: return Triple(0.0, 0.0, 0.0)
        val payments = dao.getPaymentsFor(entryId)
            .filter { it.date > entry.fromDate && it.date <= atMillis }
        val accrued = LedgerInterest.accruedInterest(entry, payments, atMillis)
        val paid = payments.sumOf { it.amount }
        val outstanding = entry.principal + accrued - paid
        return Triple(accrued, paid, outstanding)
    }

    suspend fun addPayment(payment: LedgerPayment) = dao.insertPayment(payment)

    suspend fun getPaymentsFor(entryId: Int): List<LedgerPayment> = dao.getPaymentsFor(entryId)

    suspend fun applyPartialPayment(entryId: Int, partialAmount: Double, paymentDateMillis: Long = System.currentTimeMillis()) {
        val entry = dao.getEntryOnce(entryId) ?: return
        val paymentsTillNow = dao.getPaymentsFor(entryId).filter { it.date >= entry.fromDate && it.date <= paymentDateMillis }
        val accruedTillPayment = LedgerInterest.accruedInterest(entry, paymentsTillNow, paymentDateMillis)
        val newPrincipal = (entry.principal + accruedTillPayment - partialAmount).coerceAtLeast(0.0)

        // 1) Record payment for history
        dao.insertPayment(LedgerPayment(entryId = entryId, amount = partialAmount, date = paymentDateMillis))

        // 2) Roll the baseline so future accrual starts from payment date on the remaining amount
        dao.updateEntry(
            entry.copy(
                principal = newPrincipal,
                fromDate = paymentDateMillis,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}

data class LedgerEntryWithComputed(
    val entry: LedgerEntry,
    val accruedInterest: Double,
    val paid: Double,
    val outstanding: Double,
    val total: Double
)
