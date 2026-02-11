package com.ledge.ledgerbook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ledge.ledgerbook.data.local.entities.RdAccount
import com.ledge.ledgerbook.data.local.entities.RdDeposit
import kotlinx.coroutines.flow.Flow

@Dao
interface RdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: RdAccount): Long

    @Update
    suspend fun updateAccount(account: RdAccount)

    @Delete
    suspend fun deleteAccount(account: RdAccount)

    @Query("SELECT * FROM rd_accounts ORDER BY createdAt DESC, id DESC")
    fun getAllAccounts(): Flow<List<RdAccount>>

    @Query("SELECT * FROM rd_accounts WHERE id = :id")
    suspend fun getAccountOnce(id: Long): RdAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeposit(deposit: RdDeposit): Long

    @Update
    suspend fun updateDeposit(deposit: RdDeposit)

    @Delete
    suspend fun deleteDeposit(deposit: RdDeposit)

    @Query("SELECT * FROM rd_deposits WHERE rdAccountId = :accountId ORDER BY dueDateMillis ASC, id ASC")
    fun depositsForAccount(accountId: Long): Flow<List<RdDeposit>>

    @Query("SELECT * FROM rd_deposits ORDER BY dueDateMillis ASC, id ASC")
    fun allDeposits(): Flow<List<RdDeposit>>

    @Query("SELECT * FROM rd_deposits WHERE rdAccountId = :accountId ORDER BY dueDateMillis ASC, id ASC")
    suspend fun depositsForAccountOnce(accountId: Long): List<RdDeposit>

    @Query("DELETE FROM rd_deposits WHERE rdAccountId = :accountId")
    suspend fun clearDepositsForAccount(accountId: Long)
}
