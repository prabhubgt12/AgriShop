package com.fertipos.agroshop.data.backup

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates/restores portable backups of the Room database by zipping the DB and its WAL/SHM sidecars.
 * DB name is defined in DatabaseModule as "agroshop.db".
 */
object BackupManager {
    private const val DB_NAME = "agroshop.db"

    fun createBackupZip(context: Context): ByteArray {
        val dbMain = context.getDatabasePath(DB_NAME)
        val dbWal = File(dbMain.parentFile, "$DB_NAME-wal")
        val dbShm = File(dbMain.parentFile, "$DB_NAME-shm")
        val files = listOf(dbMain, dbWal, dbShm).filter { it.exists() }

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            files.forEach { f ->
                FileInputStream(f).use { fis ->
                    val entry = ZipEntry(f.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
        return bos.toByteArray()
    }

    /**
     * Restores a backup from a ZIP containing DB_NAME and optional sidecars.
     * Caller should trigger app restart after restore to ensure Room reopens the DB cleanly.
     */
    fun restoreBackupZip(context: Context, zipBytes: ByteArray): Boolean {
        // Write into databases/ directory
        return try {
            val dbDir = context.getDatabasePath(DB_NAME).parentFile
            if (dbDir?.exists() != true) dbDir?.mkdirs()
            val tmp = File.createTempFile("restore", ".zip", context.cacheDir)
            tmp.outputStream().use { it.write(zipBytes) }
            java.util.zip.ZipFile(tmp).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val outFile = File(dbDir, e.name)
                    zf.getInputStream(e).use { ins ->
                        outFile.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                }
            }
            tmp.delete()
            true
        } catch (_: Exception) {
            false
        }
    }
}
