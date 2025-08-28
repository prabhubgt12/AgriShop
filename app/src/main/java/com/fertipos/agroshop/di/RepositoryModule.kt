package com.fertipos.agroshop.di

import com.fertipos.agroshop.data.local.AppDatabase
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.repo.BillingRepository
import com.fertipos.agroshop.data.local.dao.LedgerDao
import com.fertipos.agroshop.data.repo.LedgerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideBillingRepository(
        db: AppDatabase,
        invoiceDao: InvoiceDao,
        productDao: ProductDao
    ): BillingRepository = BillingRepository(db, invoiceDao, productDao)

    @Provides
    @Singleton
    fun provideLedgerRepository(
        db: AppDatabase,
        ledgerDao: LedgerDao
    ): LedgerRepository = LedgerRepository(db, ledgerDao)
}
