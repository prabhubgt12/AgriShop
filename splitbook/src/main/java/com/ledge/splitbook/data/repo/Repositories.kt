package com.ledge.splitbook.data.repo

import androidx.room.withTransaction
import com.ledge.splitbook.data.dao.*
import com.ledge.splitbook.data.db.AppDatabase
import com.ledge.splitbook.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun add(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val existing = categoryDao.getByName(trimmed)
        return existing?.id ?: categoryDao.insert(CategoryEntity(name = trimmed))
    }

    suspend fun rename(id: Long, newName: String) {
        val current = categoryDao.getById(id) ?: return
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        categoryDao.update(current.copy(name = trimmed))
    }

    suspend fun delete(id: Long) {
        val current = categoryDao.getById(id) ?: return
        categoryDao.delete(current)
    }
}

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao,
) {
    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeAll()
    suspend fun createGroup(name: String, icon: String? = null): Long =
        groupDao.insert(GroupEntity(name = name, icon = icon))

    suspend fun renameGroup(id: Long, newName: String) {
        val current = groupDao.getById(id) ?: return
        groupDao.update(current.copy(name = newName))
    }

    suspend fun deleteGroup(id: Long) {
        val current = groupDao.getById(id) ?: return
        groupDao.delete(current)
    }
}

@Singleton
class MemberRepository @Inject constructor(
    private val db: AppDatabase,
    private val memberDao: MemberDao,
    private val expenseDao: ExpenseDao,
    private val splitDao: ExpenseSplitDao,
) {
    fun observeMembers(groupId: Long): Flow<List<MemberEntity>> = memberDao.observeByGroup(groupId)
    suspend fun addMember(groupId: Long, name: String, deposit: Double = 0.0, isAdmin: Boolean = false): Long =
        memberDao.insert(
            MemberEntity(
                groupId = groupId,
                name = name,
                deposit = if (isAdmin) 0.0 else deposit,
                isAdmin = isAdmin
            )
        )

    suspend fun setAdmin(groupId: Long, memberId: Long) {
        // Ensure only one admin per group
        db.withTransaction {
            memberDao.clearAdmin(groupId)
        }
        val current = memberDao.getByIds(listOf(memberId)).firstOrNull() ?: return
        memberDao.update(current.copy(isAdmin = true, deposit = 0.0))
    }

    suspend fun renameMember(memberId: Long, newName: String) {
        val current = memberDao.getByIds(listOf(memberId)).firstOrNull() ?: return
        memberDao.update(current.copy(name = newName))
    }

    suspend fun updateMemberDeposit(memberId: Long, newDeposit: Double) {
        val current = memberDao.getByIds(listOf(memberId)).firstOrNull() ?: return
        if (current.isAdmin) return // admin deposit is managed implicitly as pool; ignore updates
        memberDao.update(current.copy(deposit = newDeposit))
    }

    suspend fun removeMemberIfUnused(groupId: Long, memberId: Long): Boolean {
        val pays = expenseDao.countPaidByMember(groupId, memberId)
        val splits = splitDao.countSplitsForMemberInGroup(groupId, memberId)
        if (pays == 0 && splits == 0) {
            val current = memberDao.getByIds(listOf(memberId)).firstOrNull() ?: return false
            memberDao.delete(current)
            return true
        }
        return false
    }
}

@Singleton
class ExpenseRepository @Inject constructor(
    private val db: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val expenseSplitDao: ExpenseSplitDao,
) {
    fun observeExpenses(groupId: Long): Flow<List<ExpenseEntity>> = expenseDao.observeByGroup(groupId)

    suspend fun getExpense(expenseId: Long): ExpenseEntity? = expenseDao.getById(expenseId)
    suspend fun getSplits(expenseId: Long): List<ExpenseSplitEntity> = expenseSplitDao.getByExpense(expenseId)

    suspend fun addExpense(
        groupId: Long,
        amount: Double,
        category: String,
        paidByMemberId: Long,
        note: String?,
        createdAt: String?,
        splits: List<ExpenseSplitEntity>
    ): Long {
        require(splits.isNotEmpty())
        return db.withTransaction {
            val expenseId = expenseDao.insert(
                ExpenseEntity(
                    groupId = groupId,
                    amount = amount,
                    category = category,
                    paidByMemberId = paidByMemberId,
                    note = note,
                    createdAt = createdAt
                )
            )
            val withIds = splits.map {
                it.copy(id = 0, expenseId = expenseId)
            }
            expenseSplitDao.insertAll(withIds)
            expenseId
        }
    }

    suspend fun updateExpense(
        expenseId: Long,
        amount: Double,
        category: String,
        paidByMemberId: Long,
        note: String?,
        createdAt: String?,
        splits: List<ExpenseSplitEntity>
    ) {
        db.withTransaction {
            val current = expenseDao.getById(expenseId) ?: return@withTransaction
            expenseDao.update(
                current.copy(
                    amount = amount,
                    category = category,
                    paidByMemberId = paidByMemberId,
                    note = note,
                    createdAt = createdAt
                )
            )
            expenseSplitDao.deleteByExpense(expenseId)
            val withIds = splits.map { it.copy(id = 0, expenseId = expenseId) }
            expenseSplitDao.insertAll(withIds)
        }
    }

    suspend fun deleteExpense(expenseId: Long) {
        db.withTransaction {
            expenseSplitDao.deleteByExpense(expenseId)
            expenseDao.deleteById(expenseId)
        }
    }
}

@Singleton
class SettlementRepository @Inject constructor(
    private val settlementDao: SettlementDao
) {
    fun observeSettlements(groupId: Long): Flow<List<SettlementEntity>> = settlementDao.observeByGroup(groupId)
    suspend fun markPaid(groupId: Long, from: Long, to: Long, amount: Double): Long =
        settlementDao.insert(
            SettlementEntity(
                groupId = groupId,
                fromMemberId = from,
                toMemberId = to,
                amount = amount,
                status = "completed"
            )
        )
}
