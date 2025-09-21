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

    suspend fun computeAtFromSnapshot(
        entryId: Int,
        atMillis: Long,
        prevPrincipal: Double,
        prevFromDate: Long
    ): Triple<Double, Double, Double> {
        val entry = dao.getEntryOnce(entryId) ?: return Triple(0.0, 0.0, 0.0)
        // Reconstruct the historical baseline for computation
        val snap = entry.copy(principal = prevPrincipal, fromDate = prevFromDate)
        val payments = dao.getPaymentsFor(entryId)
            .filter { it.date > prevFromDate && it.date <= atMillis }
        val accrued = LedgerInterest.accruedInterest(snap, payments, atMillis)
        val paid = payments.sumOf { it.amount }
        val outstanding = snap.principal + accrued - paid
        return Triple(accrued, paid, outstanding)
    }

    suspend fun addPayment(payment: LedgerPayment) = dao.insertPayment(payment)

    suspend fun getPaymentsFor(entryId: Int): List<LedgerPayment> = dao.getPaymentsFor(entryId)

    suspend fun applyPartialPayment(entryId: Int, partialAmount: Double, paymentDateMillis: Long = System.currentTimeMillis()) {
        // Backward-compatible minimal call without metadata
        applyPartialWithMeta(entryId, partialAmount, paymentDateMillis, userNote = null, attachmentUri = null)
    }

    private fun buildMetaNote(prevPrincipal: Double, prevFromDate: Long, attachmentUri: String?, userNote: String?): String {
        val parts = mutableListOf<String>()
        parts += "meta:prevPrincipal=${prevPrincipal}"
        parts += "prevFromDate=${prevFromDate}"
        if (!attachmentUri.isNullOrBlank()) parts += "att:${attachmentUri}"
        if (!userNote.isNullOrBlank()) parts += "note:${userNote}"
        return parts.joinToString(separator = "|")
    }

    private data class ParsedMeta(
        val prevPrincipal: Double?,
        val prevFromDate: Long?,
        val attachmentUri: String?,
        val userNote: String?
    )

    private fun parseMeta(note: String?): ParsedMeta {
        if (note.isNullOrBlank()) return ParsedMeta(null, null, null, null)
        var prevP: Double? = null
        var prevD: Long? = null
        var att: String? = null
        var usr: String? = null
        note.split('|').forEach { token ->
            when {
                token.startsWith("meta:prevPrincipal=") -> prevP = token.substringAfter("meta:prevPrincipal=").toDoubleOrNull()
                token.startsWith("prevFromDate=") -> prevD = token.substringAfter("prevFromDate=").toLongOrNull()
                token.startsWith("att:") -> att = token.substringAfter("att:")
                token.startsWith("note:") -> usr = token.substringAfter("note:")
            }
        }
        return ParsedMeta(prevP, prevD, att, usr)
    }

    suspend fun applyPartialWithMeta(
        entryId: Int,
        partialAmount: Double,
        paymentDateMillis: Long,
        userNote: String?,
        attachmentUri: String?
    ) {
        val entry = dao.getEntryOnce(entryId) ?: return
        val paymentsTillNow = dao.getPaymentsFor(entryId).filter { it.date >= entry.fromDate && it.date <= paymentDateMillis }
        val accruedTillPayment = LedgerInterest.accruedInterest(entry, paymentsTillNow, paymentDateMillis)
        val newPrincipal = (entry.principal + accruedTillPayment - partialAmount).coerceAtLeast(0.0)

        val metaNote = buildMetaNote(prevPrincipal = entry.principal, prevFromDate = entry.fromDate, attachmentUri = attachmentUri, userNote = userNote)

        // 1) Record payment for history with metadata in note
        dao.insertPayment(LedgerPayment(entryId = entryId, amount = partialAmount, date = paymentDateMillis, note = metaNote))

        // 2) Roll the baseline so future accrual starts from payment date on the remaining amount
        dao.updateEntry(
            entry.copy(
                principal = newPrincipal,
                fromDate = paymentDateMillis,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteLatestPayment(entryId: Int) {
        val last = dao.getLastPayment(entryId) ?: return
        val meta = parseMeta(last.note)
        val entry = dao.getEntryOnce(entryId) ?: return
        // Restore previous baseline if present
        val restored = entry.copy(
            principal = meta.prevPrincipal ?: entry.principal,
            fromDate = meta.prevFromDate ?: entry.fromDate,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateEntry(restored)
        dao.deletePaymentById(last.id)
    }

    suspend fun editLatestPayment(
        entryId: Int,
        newAmount: Double,
        newDateMillis: Long,
        userNote: String?,
        attachmentUri: String?
    ) {
        // Rollback latest, then apply new
        deleteLatestPayment(entryId)
        applyPartialWithMeta(entryId, newAmount, newDateMillis, userNote, attachmentUri)
    }
}

data class LedgerEntryWithComputed(
    val entry: LedgerEntry,
    val accruedInterest: Double,
    val paid: Double,
    val outstanding: Double,
    val total: Double
)
