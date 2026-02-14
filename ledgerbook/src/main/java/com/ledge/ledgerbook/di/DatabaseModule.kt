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



    // Migration from version 3 to 4 (add RD tables)
    private val MIGRATION_3_4 = object : Migration(3, 4) {

        override fun migrate(database: SupportSQLiteDatabase) {

            // Create the rd_accounts table (without autoPay column for version 4)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `rd_accounts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `installmentAmount` REAL NOT NULL,
                    `annualRatePercent` REAL NOT NULL,
                    `startDateMillis` INTEGER NOT NULL,
                    `tenureMonths` INTEGER NOT NULL,
                    `isClosed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())

            // Create the rd_deposits table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `rd_deposits` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `rdAccountId` INTEGER NOT NULL,
                    `dueDateMillis` INTEGER NOT NULL,
                    `paidDateMillis` INTEGER,
                    `amountPaid` REAL NOT NULL,
                    `note` TEXT
                )
            """.trimIndent())

            // Create the unique index for rd_deposits
            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_rd_deposits_rdAccountId_dueDateMillis`
                ON `rd_deposits` (`rdAccountId`, `dueDateMillis`)
            """.trimIndent())

        }

    }

    // Migration from version 4 to 5 (add autoPay column)
    private val MIGRATION_4_5 = object : Migration(4, 5) {

        override fun migrate(database: SupportSQLiteDatabase) {

            // Add the autoPay column to rd_accounts table
            database.execSQL("ALTER TABLE rd_accounts ADD COLUMN autoPay INTEGER NOT NULL DEFAULT 0")

        }

    }



    @Provides

    @Singleton

    fun provideDb(@ApplicationContext context: Context): AppDatabase =

        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)

            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)

            .fallbackToDestructiveMigrationFrom(1, 2) // Only destructive for versions 1-2

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

