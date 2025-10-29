package com.ledge.ledgerbook.data.repo

import com.ledge.ledgerbook.data.local.AppDatabase
import com.ledge.ledgerbook.data.local.dao.LoanDao
import com.ledge.ledgerbook.data.local.entities.LoanProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: LoanDao
) {
    fun observeAll(): Flow<List<LoanProfile>> = dao.observeAll()
    suspend fun getAll(): List<LoanProfile> = dao.getAll()
    suspend fun getById(id: Long): LoanProfile? = dao.getById(id)
    suspend fun save(profile: LoanProfile): Long = dao.insert(profile)
    suspend fun update(profile: LoanProfile) = dao.update(profile)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
