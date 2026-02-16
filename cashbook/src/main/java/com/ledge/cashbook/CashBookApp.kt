package com.ledge.cashbook

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.ledge.cashbook.data.backup.AutoBackupScheduler
import com.ledge.cashbook.data.backup.DriveClient
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.ledge.cashbook.data.local.dao.CategoryDao
import com.ledge.cashbook.data.local.dao.CategoryKeywordDao
import com.ledge.cashbook.data.local.dao.CashDao
import com.ledge.cashbook.data.local.entities.Category
import com.ledge.cashbook.data.repo.CashRepository

@HiltAndroidApp
class CashBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply saved locale before any UI is composed
        try {
            val ep = EntryPointAccessors.fromApplication(this, DataStoreEntryPoint::class.java)
            val dataStore = ep.prefs()
            val key = stringPreferencesKey("app_locale")
            val tag = runBlocking { dataStore.data.first()[key] ?: "" }
            val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        } catch (_: Exception) { }

        try {
            val ep = EntryPointAccessors.fromApplication(this, DataStoreEntryPoint::class.java)
            val dataStore = ep.prefs()
            val codeKey = stringPreferencesKey("currency_code")
            val showKey = booleanPreferencesKey("currency_show_symbol")
            val code = runBlocking { dataStore.data.first()[codeKey] ?: "INR" }
            val show = runBlocking { dataStore.data.first()[showKey] ?: true }
            com.ledge.cashbook.util.CurrencyFormatter.setConfig(code, show)
        } catch (_: Exception) { }

        // Schedule auto backup if enabled
        try {
            val ep = EntryPointAccessors.fromApplication(this, DataStoreEntryPoint::class.java)
            val dataStore = ep.prefs()
            val key = booleanPreferencesKey("auto_backup_enabled")
            val enabled = runBlocking { dataStore.data.first()[key] ?: false }
            if (enabled) {
                // Ensure Drive is initialized; if not, try to init from last account
                val ok = DriveClient.isSignedIn(this) || DriveClient.tryInitFromLastAccount(this)
                if (ok) AutoBackupScheduler.schedule(this)
            }
        } catch (_: Exception) { }

        // One-time seed: create Category rows from existing distinct cash_txns.category strings if categories table is empty
        try {
            val ep = EntryPointAccessors.fromApplication(this, CategorySeedEntryPoint::class.java)
            val categoryDao = ep.categoryDao()
            val cashDao = ep.cashDao()
            runBlocking {
                val count = categoryDao.count()
                if (count == 0) {
                    val names = cashDao.distinctCategories()
                    val now = System.currentTimeMillis()
                    names.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }
                        .forEach { name ->
                            categoryDao.insert(Category(name = name, createdAt = now, updatedAt = now))
                        }
                }
            }
        } catch (_: Exception) { }

        // Generate any missing recurring monthly cash transactions on app open (offline-friendly).
        try {
            val ep = EntryPointAccessors.fromApplication(this, CategorySeedEntryPoint::class.java)
            val cashDao = ep.cashDao()
            val repo = CashRepository(
                db = EntryPointAccessors.fromApplication(this, RecurringEntryPoint::class.java).db(),
                dao = cashDao
            )
            runBlocking {
                repo.generateDueRecurringTxns()
            }
        } catch (_: Exception) { }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface DataStoreEntryPoint {
    fun prefs(): DataStore<Preferences>
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CategorySeedEntryPoint {
    fun categoryDao(): CategoryDao
    fun categoryKeywordDao(): CategoryKeywordDao
    fun cashDao(): CashDao
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface RecurringEntryPoint {
    fun db(): com.ledge.cashbook.data.local.AppDatabase
}
