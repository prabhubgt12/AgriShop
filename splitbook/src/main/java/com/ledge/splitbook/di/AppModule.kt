package com.ledge.splitbook.di

import android.content.Context
import com.ledge.splitbook.data.dao.*
import com.ledge.splitbook.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.build(context)

    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun provideMemberDao(db: AppDatabase): MemberDao = db.memberDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideExpenseSplitDao(db: AppDatabase): ExpenseSplitDao = db.expenseSplitDao()
    @Provides fun provideSettlementDao(db: AppDatabase): SettlementDao = db.settlementDao()
}
