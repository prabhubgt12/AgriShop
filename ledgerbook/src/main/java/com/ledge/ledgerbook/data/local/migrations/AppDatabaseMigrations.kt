package com.ledge.ledgerbook.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for LedgerBook App
 * 
 * This file contains all migrations to prevent data loss during app upgrades.
 * Each migration handles specific schema changes between versions.
 */

/**
 * Migration from version 1 to 2
 * 
 * Changes:
 * - Add loan_profiles table
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create loan_profiles table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS loan_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                type TEXT NOT NULL, 
                name TEXT NOT NULL, 
                principal REAL NOT NULL, 
                annualRatePercent REAL NOT NULL, 
                tenureMonths INTEGER NOT NULL, 
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/**
 * Migration from version 2 to 3
 * 
 * Changes:
 * - Add firstEmiDateMillis column to loan_profiles table
 */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add firstEmiDateMillis column to loan_profiles table
        database.execSQL("ALTER TABLE loan_profiles ADD COLUMN firstEmiDateMillis INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration from version 3 to 4
 * 
 * Changes:
 * - Add rd_accounts table
 * - Add rd_deposits table
 */
object Migration3To4 : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create rd_accounts table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS rd_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                name TEXT NOT NULL, 
                installmentAmount REAL NOT NULL, 
                annualRatePercent REAL NOT NULL, 
                startDateMillis INTEGER NOT NULL, 
                tenureMonths INTEGER NOT NULL, 
                isClosed INTEGER NOT NULL, 
                createdAt INTEGER NOT NULL, 
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Create rd_deposits table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS rd_deposits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                rdAccountId INTEGER NOT NULL, 
                dueDateMillis INTEGER NOT NULL, 
                amountPaid REAL NOT NULL, 
                note TEXT
            )
        """.trimIndent())

        // Create unique index for rd_deposits
        database.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_rd_deposits_rdAccountId_dueDateMillis 
            ON rd_deposits (rdAccountId, dueDateMillis)
        """.trimIndent())
    }
}

/**
 * Migration from version 4 to 5
 * 
 * Changes:
 * - Add paidDateMillis column to rd_deposits table
 */
object Migration4To5 : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the new paidDateMillis column to rd_deposits table
        database.execSQL("ALTER TABLE rd_deposits ADD COLUMN paidDateMillis INTEGER")
    }
}

/**
 * All migrations array for easy reference
 */
val ALL_MIGRATIONS = arrayOf(
    Migration1To2,
    Migration2To3,
    Migration3To4,
    Migration4To5
)
