package com.fertipos.agroshop.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fertipos.agroshop.data.local.dao.CustomerDao
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import com.fertipos.agroshop.data.local.dao.ProductDao
import com.fertipos.agroshop.data.local.dao.UserDao
import com.fertipos.agroshop.data.local.dao.CompanyProfileDao
import com.fertipos.agroshop.data.local.entities.Customer
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.local.entities.User
import com.fertipos.agroshop.data.local.entities.CompanyProfile

@Database(
    entities = [User::class, Customer::class, Product::class, Invoice::class, InvoiceItem::class, CompanyProfile::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun companyProfileDao(): CompanyProfileDao
}
