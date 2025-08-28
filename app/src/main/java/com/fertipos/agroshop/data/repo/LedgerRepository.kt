package com.fertipos.agroshop.data.repo

import com.fertipos.agroshop.data.local.AppDatabase
import com.fertipos.agroshop.data.local.dao.LedgerDao
import com.fertipos.agroshop.data.local.entities.LedgerEntry
import com.fertipos.agroshop.data.local.entities.LedgerPayment
import com.fertipos.agroshop.util.LedgerInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class LedgerRepository(
    private val db: AppDatabase,
    private val dao: LedgerDao
) {
    fun entries(): Flow<List<LedgerEntryWithComputed>> = dao.getAllEntries()
        .flatMapLatest { list ->
            flow {
                val computed = withContext(Dispatchers.IO) {
                    list.map { e ->
                        val payments = dao.getPaymentsFor(e.id)
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
        val payments = dao.getPaymentsFor(entryId).filter { it.date <= atMillis }
        val accrued = LedgerInterest.accruedInterest(entry, payments, atMillis)
        val paid = payments.sumOf { it.amount }
        val outstanding = entry.principal + accrued - paid
        return Triple(accrued, paid, outstanding)
    }

    suspend fun addPayment(payment: LedgerPayment) = dao.insertPayment(payment)

    suspend fun getPaymentsFor(entryId: Int): List<LedgerPayment> = dao.getPaymentsFor(entryId)

    /**
     * Partial payment rule (per user):
     * newPrincipal = totalAmount(now) + interest(now) - partialPayment
     * and reset fromDate to now, clearing existing payments.
     */
    suspend fun applyPartialPayment(entryId: Int, partialAmount: Double) {
        // Backward-compatible: default to now
        applyPartialPayment(entryId, partialAmount, System.currentTimeMillis())
    }

    /**
     * Partial payment rule:
     * newPrincipal = principal + interest(accrued till paymentDate) - partialPayment
     * and reset fromDate to paymentDate, clearing existing payments to avoid double counting.
     */
    suspend fun applyPartialPayment(entryId: Int, partialAmount: Double, paymentDateMillis: Long) {
        val entry = dao.getEntryOnce(entryId) ?: return
        val payments = dao.getPaymentsFor(entryId)
        val accrued = LedgerInterest.accruedInterest(entry, payments, paymentDateMillis)
        val newPrincipal = (entry.principal + accrued - partialAmount).coerceAtLeast(0.0)

        // reset ledger: clear payments and set fromDate to the payment date with new principal
        dao.clearPaymentsFor(entryId)
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
