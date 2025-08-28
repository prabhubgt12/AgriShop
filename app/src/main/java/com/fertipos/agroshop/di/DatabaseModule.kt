package com.fertipos.agroshop.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fertipos.agroshop.data.local.AppDatabase
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.InvoiceSummaryDao
import com.fertipos.agroshop.data.local.dao.InvoicePlLinesDao
import com.fertipos.agroshop.data.local.dao.PurchaseDao
import com.fertipos.agroshop.data.local.dao.PurchaseSummaryDao
import com.fertipos.agroshop.data.local.dao.LedgerDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.dao.UserDao
import com.fertipos.agroshop.data.local.dao.CompanyProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE invoices ADD COLUMN paid REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE purchases ADD COLUMN paid REAL NOT NULL DEFAULT 0.0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add compoundPeriod to ledger_entries with default 'MONTHLY'
            database.execSQL(
                "ALTER TABLE ledger_entries ADD COLUMN compoundPeriod TEXT NOT NULL DEFAULT 'MONTHLY'"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add productTypesCsv to company_profile with default preserving existing behavior
            database.execSQL(
                "ALTER TABLE company_profile ADD COLUMN productTypesCsv TEXT NOT NULL DEFAULT 'Fertilizer,Pecticide,Fungi,GP,Other'"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ledger_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "principal REAL NOT NULL, " +
                        "interestType TEXT NOT NULL, " +
                        "period TEXT, " +
                        "rateRupees REAL NOT NULL, " +
                        "fromDate INTEGER NOT NULL, " +
                        "notes TEXT, " +
                        "isClosed INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL" +
                ")"
            )
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS ledger_payments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "entryId INTEGER NOT NULL, " +
                        "amount REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "note TEXT, " +
                        "FOREIGN KEY(entryId) REFERENCES ledger_entries(id) ON DELETE CASCADE" +
                ")"
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add lowStockThreshold to company_profile with default 10
            database.execSQL(
                "ALTER TABLE company_profile ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 10"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "agroshop.db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideInvoiceDao(db: AppDatabase): InvoiceDao = db.invoiceDao()

    @Provides
    fun provideInvoiceSummaryDao(db: AppDatabase): InvoiceSummaryDao = db.invoiceSummaryDao()

    @Provides
    fun provideInvoicePlLinesDao(db: AppDatabase): InvoicePlLinesDao = db.invoicePlLinesDao()

    @Provides
    fun providePurchaseDao(db: AppDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun providePurchaseSummaryDao(db: AppDatabase): PurchaseSummaryDao = db.purchaseSummaryDao()

    @Provides
    fun provideCompanyProfileDao(db: AppDatabase): CompanyProfileDao = db.companyProfileDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()
}
