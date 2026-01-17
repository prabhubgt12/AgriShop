package com.ledge.splitbook.data.backup

import android.content.Context
import android.content.Intent
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val DB_NAME = "splitbook.db"
        private const val BACKUP_FILENAME = "splitbook_backup_v1.zip" // exact filename for app separation
    }

    fun isSignedIn(): Boolean = DriveClient.isSignedIn(context)
    fun tryInitFromLastAccount(): Boolean = DriveClient.tryInitFromLastAccount(context)
    fun getSignInIntent(): Intent = DriveClient.getSignInIntent(context)
    fun handleSignInResult(data: Intent?): Boolean = DriveClient.handleSignInResult(context, data)
    fun signOut() = DriveClient.signOut(context)
    fun lastAccountEmail(): String? = DriveClient.lastAccountEmail(context)

    suspend fun listBackups(): List<File> = DriveClient.listBackups().filter { it.name == BACKUP_FILENAME }

    suspend fun lastBackupTime(): String? {
        val files = listBackups()
        val first = files.firstOrNull() ?: return null
        val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
        return first.modifiedTime?.let { sdf.format(Date(it.value)) }
    }

    suspend fun backupNow(): Boolean {
        val bytes = makeBackupZip() ?: return false
        val ok = DriveClient.uploadAppData(BACKUP_FILENAME, bytes)
        if (!ok) return false
        // keep only the latest backup
        val files = listBackups()
        files.drop(1).forEach { DriveClient.delete(it.id) }
        return true
    }

    private fun makeBackupZip(): ByteArray? {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return null
        val metaJson = """{"package":"com.ledge.splitbook","db":"$DB_NAME","version":1}""".toByteArray()
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // metadata
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(metaJson)
            zos.closeEntry()
            // database
            zos.putNextEntry(ZipEntry("db/$DB_NAME"))
            FileInputStream(dbFile).use { fis ->
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val r = fis.read(buf)
                    if (r <= 0) break
                    zos.write(buf, 0, r)
                }
            }
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    suspend fun restoreLatest(): Boolean {
        val latest = listBackups().firstOrNull() ?: return false
        val data = DriveClient.download(latest.id) ?: return false
        return restoreFromZip(data)
    }

    private fun restoreFromZip(zipBytes: ByteArray): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        // best-effort close by letting Room reopen later; overwrite file
        return try {
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                var restored = false
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == "db/$DB_NAME") {
                        dbFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(dbFile).use { fos ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val r = zis.read(buf)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                            }
                        }
                        restored = true
                        break
                    }
                    entry = zis.nextEntry
                }
                restored
            }
        } catch (e: Exception) {
            false
        }
    }
}
