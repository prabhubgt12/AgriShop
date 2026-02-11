package com.ledge.ledgerbook.data.repo

import com.ledge.ledgerbook.data.local.dao.RdDao
import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit
import kotlinx.coroutines.flow.Flow

class RdRepository(
    private val dao: RdDao
) {
    fun accounts(): Flow<List<RdAccount>> = dao.getAllAccounts()

    fun allDeposits(): Flow<List<RdDeposit>> = dao.allDeposits()

    suspend fun getAccount(id: Long): RdAccount? = dao.getAccountOnce(id)

    suspend fun saveAccount(account: RdAccount): Long {
        return if (account.id == 0L) {
            dao.upsertAccount(account)
        } else {
            dao.updateAccount(account.copy(updatedAt = System.currentTimeMillis()))
            account.id
        }
    }

    suspend fun deleteAccount(account: RdAccount) {
        dao.clearDepositsForAccount(account.id)
        dao.deleteAccount(account)
    }

    fun deposits(accountId: Long): Flow<List<RdDeposit>> = dao.depositsForAccount(accountId)

    suspend fun addDeposit(deposit: RdDeposit): Long = dao.upsertDeposit(deposit)

    suspend fun updateDeposit(deposit: RdDeposit) = dao.updateDeposit(deposit)

    suspend fun deleteDeposit(deposit: RdDeposit) = dao.deleteDeposit(deposit)

    suspend fun getDepositsOnce(accountId: Long): List<RdDeposit> = dao.depositsForAccountOnce(accountId)
}
