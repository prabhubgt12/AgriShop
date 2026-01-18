package com.ledge.splitbook.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ledge.splitbook.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("SELECT * FROM groups ORDER BY id DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?
}

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MemberEntity): Long

    @Update
    suspend fun update(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY id ASC")
    fun observeByGroup(groupId: Long): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MemberEntity>

    @Query("SELECT * FROM members WHERE groupId = :groupId AND isAdmin = 1 LIMIT 1")
    suspend fun getAdminForGroup(groupId: Long): MemberEntity?

    @Query("UPDATE members SET isAdmin = 0 WHERE groupId = :groupId AND isAdmin = 1")
    suspend fun clearAdmin(groupId: Long)
}

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY id DESC")
    fun observeByGroup(groupId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteById(expenseId: Long)

    @Query("SELECT COUNT(*) FROM expenses WHERE groupId = :groupId AND paidByMemberId = :memberId")
    suspend fun countPaidByMember(groupId: Long, memberId: Long): Int
}

@Dao
interface ExpenseSplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<ExpenseSplitEntity>)

    @Query("SELECT * FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun getByExpense(expenseId: Long): List<ExpenseSplitEntity>

    @Query("SELECT * FROM expense_splits WHERE expenseId IN (:expenseIds)")
    suspend fun getByExpenseIds(expenseIds: List<Long>): List<ExpenseSplitEntity>

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteByExpense(expenseId: Long)

    @Query(
        """
        SELECT COUNT(*) FROM expense_splits es
        INNER JOIN expenses e ON es.expenseId = e.id
        WHERE e.groupId = :groupId AND es.memberId = :memberId
        """
    )
    suspend fun countSplitsForMemberInGroup(groupId: Long, memberId: Long): Int
}

@Dao
interface SettlementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settlement: SettlementEntity): Long

    @Update
    suspend fun update(settlement: SettlementEntity)

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY id DESC")
    fun observeByGroup(groupId: Long): Flow<List<SettlementEntity>>
}
