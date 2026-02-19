package com.ledge.splitbook.data.repo

import androidx.room.withTransaction
import com.ledge.splitbook.data.dao.*
import com.ledge.splitbook.data.db.AppDatabase
import com.ledge.splitbook.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    private val db: AppDatabase,
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
    private val expenseDao: ExpenseDao,
    private val expenseSplitDao: ExpenseSplitDao,
    private val settlementDao: SettlementDao,
    private val tripDayDao: TripDayDao,
    private val placeDao: PlaceDao
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
        db.withTransaction {
            // Delete in dependency order to avoid RESTRICT FK issues on restored DBs
            val expenseIds = expenseDao.idsByGroup(id)
            if (expenseIds.isNotEmpty()) expenseSplitDao.deleteByExpenseIds(expenseIds)
            settlementDao.deleteByGroup(id)
            expenseDao.deleteByGroup(id)
            memberDao.deleteByGroup(id)
            placeDao.deleteByGroup(id)
            tripDayDao.deleteByGroup(id)
            groupDao.delete(current)
        }
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

    suspend fun getAllUniqueMemberNames(): List<String> = memberDao.getAllUniqueMemberNames()

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
    fun observeSplitCount(groupId: Long): Flow<Int> = expenseSplitDao.observeSplitCountForGroup(groupId)

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
                amount = kotlin.math.round(amount * 100.0) / 100.0,
                status = "completed"
            )
        )
}

@Singleton
class TripDayRepository @Inject constructor(
    private val tripDayDao: TripDayDao
) {
    fun observeDays(groupId: Long): Flow<List<TripDayEntity>> = tripDayDao.observeByGroup(groupId)

    suspend fun addDay(groupId: Long, dayNumber: Int, date: String? = null): Long =
        tripDayDao.insert(TripDayEntity(groupId = groupId, dayNumber = dayNumber, date = date))

    suspend fun deleteDay(id: Long) {
        tripDayDao.getById(id)?.let { tripDayDao.delete(it) }
    }
}

@Singleton
class PlaceRepository @Inject constructor(
    private val placeDao: PlaceDao
) {
    fun observePlaces(dayId: Long): Flow<List<PlaceEntity>> = placeDao.observeByDay(dayId)

    suspend fun addPlace(groupId: Long, dayId: Long, name: String): Long =
        placeDao.insert(PlaceEntity(groupId = groupId, dayId = dayId, name = name))

    suspend fun deletePlace(id: Long) {
        placeDao.getById(id)?.let { placeDao.delete(it) }
    }
}

@Singleton
class TripPlanRepository @Inject constructor(
    private val db: AppDatabase,
    private val tripDayDao: TripDayDao,
    private val placeDao: PlaceDao
) {
    suspend fun replaceTripDays(groupId: Long, dayDates: List<LocalDate>) {
        db.withTransaction {
            placeDao.deleteByGroup(groupId)
            tripDayDao.deleteByGroup(groupId)
            dayDates.forEachIndexed { idx, date ->
                tripDayDao.insert(
                    TripDayEntity(
                        groupId = groupId,
                        dayNumber = idx + 1,
                        date = date.toString()
                    )
                )
            }
        }
    }

    fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
}
