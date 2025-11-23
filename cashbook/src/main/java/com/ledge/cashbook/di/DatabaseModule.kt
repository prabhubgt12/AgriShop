package com.ledge.cashbook.di

import android.content.Context
import androidx.room.Room
import com.ledge.cashbook.data.local.AppDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.dao.CategoryDao
import com.ledge.cashbook.data.local.dao.CategoryKeywordDao
import com.ledge.cashbook.data.repo.CashRepository
import com.ledge.cashbook.data.repo.CategoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "cashbook.db"

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .build()

    @Provides
    fun provideDao(db: AppDatabase): CashDao = db.cashDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideCategoryKeywordDao(db: AppDatabase): CategoryKeywordDao = db.categoryKeywordDao()

    @Provides
    @Singleton
    fun provideRepo(db: AppDatabase, dao: CashDao): CashRepository = CashRepository(db, dao)

    @Provides
    @Singleton
    fun provideCategoryRepo(
        db: AppDatabase,
        categoryDao: CategoryDao,
        keywordDao: CategoryKeywordDao
    ): CategoryRepository = CategoryRepository(db, categoryDao, keywordDao)
}
