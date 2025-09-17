package com.ledge.cashbook.di

import android.content.Context
import androidx.room.Room
import com.ledge.cashbook.data.local.AppDatabase
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.repo.CashRepository
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: AppDatabase): CashDao = db.cashDao()

    @Provides
    @Singleton
    fun provideRepo(db: AppDatabase, dao: CashDao): CashRepository = CashRepository(db, dao)
}
