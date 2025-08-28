package com.fertipos.agroshop.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.InvoiceSummaryDao
import com.fertipos.agroshop.data.local.dao.InvoicePlLinesDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.dao.UserDao
import com.fertipos.agroshop.data.local.dao.CompanyProfileDao
import com.fertipos.agroshop.data.local.dao.PurchaseDao
import com.fertipos.agroshop.data.local.dao.PurchaseSummaryDao
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.local.entities.User
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import com.fertipos.agroshop.data.local.entities.Purchase
import com.fertipos.agroshop.data.local.entities.PurchaseItem
import com.fertipos.agroshop.data.local.entities.LedgerEntry
import com.fertipos.agroshop.data.local.entities.LedgerPayment
import com.fertipos.agroshop.data.local.dao.LedgerDao

@Database(
    entities = [
        User::class,
        Customer::class,
        Product::class,
        Invoice::class,
        InvoiceItem::class,
        CompanyProfile::class,
        Purchase::class,
        PurchaseItem::class,
        LedgerEntry::class,
        LedgerPayment::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceSummaryDao(): InvoiceSummaryDao
    abstract fun invoicePlLinesDao(): InvoicePlLinesDao
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseSummaryDao(): PurchaseSummaryDao
    abstract fun ledgerDao(): LedgerDao
}
