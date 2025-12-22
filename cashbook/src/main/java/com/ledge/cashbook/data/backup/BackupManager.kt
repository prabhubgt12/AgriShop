package com.ledge.cashbook.data.backup

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates/restores portable backups of the Room database by zipping the DB and its WAL/SHM sidecars.
 * DB name is defined in DatabaseModule as "cashbook.db".
 */
object BackupManager {
    private const val DB_NAME = "cashbook.db"
    private const val PREFS_NAME = "cashbook_settings"
    private const val TAG = "CashbookRestore"

    @Volatile private var lastErrorMessage: String? = null
    fun lastError(): String? = lastErrorMessage

    fun createBackupZip(context: Context): ByteArray {
        val dbMain = context.getDatabasePath(DB_NAME)
        val dbWal = File(dbMain.parentFile, "$DB_NAME-wal")
        val dbShm = File(dbMain.parentFile, "$DB_NAME-shm")
        val files = listOf(dbMain, dbWal, dbShm).filter { it.exists() }
        val attachmentsDir = File(context.filesDir, "attachments")
        // Preferences DataStore file
        val prefsFile = context.preferencesDataStoreFile(PREFS_NAME)

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // Database files at zip root
            files.forEach { f ->
                FileInputStream(f).use { fis ->
                    val entry = ZipEntry(f.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }

            // Preferences under prefs/ prefix
            if (prefsFile.exists()) {
                FileInputStream(prefsFile).use { fis ->
                    val entry = ZipEntry("prefs/${prefsFile.name}")
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }

            // Attachments under attachments/ prefix
            if (attachmentsDir.exists()) {
                attachmentsDir.listFiles()?.forEach { att ->
                    if (att.isFile) {
                        FileInputStream(att).use { fis ->
                            val entry = ZipEntry("attachments/" + att.name)
                            zos.putNextEntry(entry)
                            fis.copyTo(zos)
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        return bos.toByteArray()
    }

    fun restoreBackupZip(context: Context, zipBytes: ByteArray): Boolean {
        return try {
            val dbDir = context.getDatabasePath(DB_NAME).parentFile
            if (dbDir?.exists() != true) dbDir?.mkdirs()
            val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val prefsFile = context.preferencesDataStoreFile(PREFS_NAME)
            val prefsDir = prefsFile.parentFile?.apply { if (!exists()) mkdirs() }
            val tmp = File.createTempFile("restore", ".zip", context.cacheDir)
            tmp.outputStream().use { it.write(zipBytes) }
            java.util.zip.ZipFile(tmp).use { zf ->
                // Proactively clear existing DB files to avoid mixed WAL/SHM state
                runCatching {
                    Log.d(TAG, "Deleting existing DB files before restore")
                    File(dbDir, DB_NAME).delete()
                    File(dbDir, "$DB_NAME-wal").delete()
                    File(dbDir, "$DB_NAME-shm").delete()
                }
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val outFile = if (e.name.startsWith("attachments/")) {
                        File(attachmentsDir, e.name.removePrefix("attachments/"))
                    } else if (e.name.startsWith("prefs/")) {
                        // Always write to our DataStore preferences file
                        prefsFile
                    } else {
                        File(dbDir, e.name)
                    }
                    Log.d(TAG, "Restoring entry ${e.name} -> ${outFile.absolutePath}")
                    outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    zf.getInputStream(e).use { ins ->
                        outFile.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                }
            }
            tmp.delete()
            true
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            Log.e(TAG, "restoreBackupZip failed", e)
            false
        }
    }
}
