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
import com.ledge.splitbook.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase
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

    suspend fun lastBackupTime(): String? = withContext(Dispatchers.IO) {
        val files = listBackups()
        val first = files.firstOrNull() ?: return@withContext null
        val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault())
        first.modifiedTime?.let { sdf.format(Date(it.value)) }
    }

    suspend fun backupNow(): Boolean = withContext(Dispatchers.IO) {
        val bytes = makeBackupZip() ?: return@withContext false
        val ok = DriveClient.uploadAppData(BACKUP_FILENAME, bytes)
        if (!ok) return@withContext false
        // keep only the latest backup
        val files = listBackups()
        files.drop(1).forEach { DriveClient.delete(it.id) }
        true
    }

    private fun makeBackupZip(): ByteArray? {
        // Flush SQLite WAL to disk without closing the singleton Room database.
        // Closing the injected AppDatabase would permanently close the connection pool
        // for the running process, breaking writes (e.g., create group) until app restart.
        runCatching {
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        }
        val dbMain = context.getDatabasePath(DB_NAME)
        if (!dbMain.exists()) return null
        val dbWal = java.io.File(dbMain.parentFile, "$DB_NAME-wal")
        val dbShm = java.io.File(dbMain.parentFile, "$DB_NAME-shm")
        val files = listOf(dbMain, dbWal, dbShm).filter { it.exists() }
        val metaJson = """{"package":"com.ledge.splitbook","db":"$DB_NAME","version":1}""".toByteArray()
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            // metadata
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(metaJson)
            zos.closeEntry()
            // database files at zip root (match Cashbook style)
            files.forEach { f ->
                FileInputStream(f).use { fis ->
                    val entry = ZipEntry(f.name)
                    zos.putNextEntry(entry)
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val r = fis.read(buf)
                        if (r <= 0) break
                        zos.write(buf, 0, r)
                    }
                    zos.closeEntry()
                }
            }
        }
        return bos.toByteArray()
    }

    suspend fun restoreLatest(): Boolean = withContext(Dispatchers.IO) {
        val latest = listBackups().firstOrNull() ?: return@withContext false
        val data = DriveClient.download(latest.id) ?: return@withContext false
        restoreFromZip(data)
    }

    private fun restoreFromZip(zipBytes: ByteArray): Boolean {
        val dbDir = context.getDatabasePath(DB_NAME).parentFile
        val dbMain = context.getDatabasePath(DB_NAME)
        // Close Room before replacing DB files and clear previous state
        runCatching { db.close() }
        runCatching { java.io.File(dbMain.absolutePath + "-wal").delete() }
        runCatching { java.io.File(dbMain.absolutePath + "-shm").delete() }
        return try {
            val tmp = java.io.File.createTempFile("restore", ".zip", context.cacheDir)
            tmp.outputStream().use { it.write(zipBytes) }
            java.util.zip.ZipFile(tmp).use { zf ->
                if (dbDir?.exists() != true) dbDir?.mkdirs()
                // Delete existing DB, WAL, SHM to avoid mixed state
                runCatching { dbMain.delete() }
                runCatching { java.io.File(dbMain.absolutePath + "-wal").delete() }
                runCatching { java.io.File(dbMain.absolutePath + "-shm").delete() }
                val entries = zf.entries()
                var restored = false
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    // Support both legacy path (db/...) and root files
                    val name = e.name
                    val outFile = when {
                        name == DB_NAME || name.endsWith("/$DB_NAME") -> dbMain
                        name == "$DB_NAME-wal" || name.endsWith("/$DB_NAME-wal") -> java.io.File(dbMain.parentFile, "$DB_NAME-wal")
                        name == "$DB_NAME-shm" || name.endsWith("/$DB_NAME-shm") -> java.io.File(dbMain.parentFile, "$DB_NAME-shm")
                        else -> null
                    }
                    if (outFile != null) {
                        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        zf.getInputStream(e).use { ins ->
                            java.io.FileOutputStream(outFile).use { outs -> ins.copyTo(outs); outs.fd.sync() }
                        }
                        restored = true
                    }
                }
                restored
            }
        } catch (e: Exception) {
            false
        }
    }
}
