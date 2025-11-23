package com.ledge.cashbook.data.repo

import com.ledge.cashbook.data.local.AppDatabase
import com.ledge.cashbook.data.local.dao.CategoryDao
import com.ledge.cashbook.data.local.dao.CategoryKeywordDao
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.local.entities.CategoryKeyword
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

class CategoryRepository(
    private val db: AppDatabase,
    private val categoryDao: CategoryDao,
    private val keywordDao: CategoryKeywordDao
) {
    fun categories(): Flow<List<Category>> = categoryDao.categories()

    suspend fun categoriesOnce(): List<Category> = categoryDao.allOnce()

    suspend fun upsertCategoryWithKeywords(
        id: Long?, name: String, color: Int?, isSystem: Boolean = false, keywords: List<String>
    ): Long {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val categoryId = if (id == null || id == 0L) {
                categoryDao.insert(Category(name = name.trim(), color = color, isSystem = isSystem, createdAt = now, updatedAt = now))
            } else {
                val existing = categoryDao.getById(id) ?: return@withTransaction 0L
                categoryDao.update(existing.copy(name = name.trim(), color = color, updatedAt = now))
                id
            }
            // Replace keywords set atomically
            keywordDao.deleteForCategory(categoryId)
            keywords.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .forEach { kw -> keywordDao.insert(CategoryKeyword(categoryId = categoryId, keyword = kw)) }
            categoryId
        }
    }

    suspend fun deleteCategoryCascade(id: Long) {
        val c = categoryDao.getById(id) ?: return
        categoryDao.delete(c)
        // ON DELETE CASCADE will remove keywords
    }

    fun keywords(categoryId: Long): Flow<List<CategoryKeyword>> = keywordDao.keywordsForCategory(categoryId)

    suspend fun allKeywords(): List<CategoryKeyword> = keywordDao.all()
}
