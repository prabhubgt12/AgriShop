package com.ledge.cashbook.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.cashbook.R
import com.ledge.cashbook.data.backup.BackupManager
import com.ledge.cashbook.data.backup.DriveClient
import com.ledge.cashbook.data.local.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val db: AppDatabase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    suspend fun restoreLatestFromDrive(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Only consider CashBook backups to avoid cross-app restores
            val latest = DriveClient.listBackups()
                .firstOrNull { it.name == "cashbook-backup.zip" }
            if (latest == null) {
                return@withContext false to appContext.getString(R.string.download_failed)
            }

            val bytes = DriveClient.download(latest.id)
            if (bytes == null) {
                val err = DriveClient.lastError() ?: appContext.getString(R.string.download_failed)
                return@withContext false to err
            }

            // Close Room before replacing DB files
            runCatching { db.close() }

            val ok = BackupManager.restoreBackupZip(appContext, bytes)
            if (ok) true to appContext.getString(R.string.restore_complete)
            else false to (BackupManager.lastError() ?: appContext.getString(R.string.restore_failed))
        } catch (e: Exception) {
            false to (e.localizedMessage ?: appContext.getString(R.string.restore_failed))
        }
    }
}
