package com.ledge.cashbook.data.local.dao

import androidx.room.*
import com.ledge.cashbook.data.local.entities.CategoryKeyword
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryKeywordDao {
    @Query("SELECT * FROM category_keywords WHERE categoryId = :categoryId ORDER BY keyword COLLATE NOCASE")
    fun keywordsForCategory(categoryId: Long): Flow<List<CategoryKeyword>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(keyword: CategoryKeyword): Long

    @Update
    suspend fun update(keyword: CategoryKeyword)

    @Delete
    suspend fun delete(keyword: CategoryKeyword)

    @Query("DELETE FROM category_keywords WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)

    @Query("SELECT * FROM category_keywords")
    suspend fun all(): List<CategoryKeyword>
}
