package com.ledge.ledgerbook.di



import android.content.Context

import androidx.room.Room

import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase

import com.ledge.ledgerbook.data.local.AppDatabase

import com.ledge.ledgerbook.data.local.dao.LedgerDao

import com.ledge.ledgerbook.data.local.dao.LoanDao

import com.ledge.ledgerbook.data.local.dao.RdDao

import com.ledge.ledgerbook.data.repo.LedgerRepository

import com.ledge.ledgerbook.data.repo.LoanRepository

import com.ledge.ledgerbook.data.repo.RdRepository

import dagger.Module

import dagger.Provides

import dagger.hilt.InstallIn

import dagger.hilt.android.qualifiers.ApplicationContext

import dagger.hilt.components.SingletonComponent

import javax.inject.Singleton



@Module

@InstallIn(SingletonComponent::class)

object DatabaseModule {



    private const val DB_NAME = "ledgerbook.db"



    // Migration from version 4 to 5
    private val MIGRATION_4_5 = object : Migration(4, 5) {

        override fun migrate(database: SupportSQLiteDatabase) {

            // Add the new autoPay column to rd_accounts table
            database.execSQL("ALTER TABLE rd_accounts ADD COLUMN autoPay INTEGER NOT NULL DEFAULT 0")

        }

    }



    @Provides

    @Singleton

    fun provideDb(@ApplicationContext context: Context): AppDatabase =

        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)

            .addMigrations(MIGRATION_4_5)

            .fallbackToDestructiveMigrationFrom(1, 2, 3) // Only use destructive migration for versions before 4

            .build()



    @Provides

    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()



    @Provides

    fun provideLoanDao(db: AppDatabase): LoanDao = db.loanDao()



    @Provides

    fun provideRdDao(db: AppDatabase): RdDao = db.rdDao()



    @Provides

    @Singleton

    fun provideLedgerRepository(db: AppDatabase, dao: LedgerDao): LedgerRepository =

        LedgerRepository(db, dao)



    @Provides

    @Singleton

    fun provideLoanRepository(db: AppDatabase, dao: LoanDao): LoanRepository =

        LoanRepository(db, dao)



    @Provides

    @Singleton

    fun provideRdRepository(dao: RdDao): RdRepository =

        RdRepository(dao)

}

