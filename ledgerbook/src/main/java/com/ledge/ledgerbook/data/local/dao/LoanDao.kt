package com.ledge.ledgerbook.data.local.dao

import androidx.room.*
import com.ledge.ledgerbook.data.local.entities.LoanProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loan_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LoanProfile>>

    @Query("SELECT * FROM loan_profiles ORDER BY createdAt DESC")
    suspend fun getAll(): List<LoanProfile>

    @Query("SELECT * FROM loan_profiles WHERE id = :id")
    suspend fun getById(id: Long): LoanProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: LoanProfile): Long

    @Update
    suspend fun update(profile: LoanProfile)

    @Delete
    suspend fun delete(profile: LoanProfile)

    @Query("DELETE FROM loan_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
