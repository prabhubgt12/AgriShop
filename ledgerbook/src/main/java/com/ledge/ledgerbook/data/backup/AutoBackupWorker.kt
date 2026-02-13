package com.ledge.ledgerbook.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Only attempt if Drive is initialized; try to init from last account
            val ctx = applicationContext
            if (!DriveClient.isSignedIn(ctx)) {
                val ok = DriveClient.tryInitFromLastAccount(ctx)
                if (!ok) return Result.retry()
            }
            val bytes = BackupManager.createBackupZip(ctx)
            val ok = DriveClient.uploadAppData("ledgerbook-backup.zip", bytes)
            if (ok) Result.success() else Result.retry()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
