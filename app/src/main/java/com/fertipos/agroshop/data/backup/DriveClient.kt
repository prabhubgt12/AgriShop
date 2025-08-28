package com.fertipos.agroshop.data.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

object DriveClient {
    private const val SCOPE_APPDATA = DriveScopes.DRIVE_APPDATA

    @Volatile
    private var driveService: Drive? = null

    @Volatile
    private var lastErrorMessage: String? = null

    fun isSignedIn(context: Context): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null && driveService != null

    fun lastError(): String? = lastErrorMessage

    fun tryInitFromLastAccount(context: Context): Boolean {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SCOPE_APPDATA)).apply {
                selectedAccount = account.account
            }
            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("AgroShop").build()
            true
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            false
        }
    }

    fun hasDriveScope(context: Context): Boolean {
        return try {
            val acct = GoogleSignIn.getLastSignedInAccount(context) ?: return false
            GoogleSignIn.hasPermissions(acct, Scope(SCOPE_APPDATA))
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            false
        }
    }

    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun handleSignInResult(context: Context, data: Intent?): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SCOPE_APPDATA)).apply {
                selectedAccount = account.account
            }
            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("AgroShop").build()
            true
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            false
        }
    }

    fun signOut(context: Context) {
        val client = GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(SCOPE_APPDATA))
                .build()
        )
        client.signOut()
        driveService = null
    }

    suspend fun uploadAppData(filename: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext false
        try {
            // Try find existing file with same name in App Folder
            val list = service.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id,name)")
                .execute()
            val existing = list.files?.firstOrNull { it.name == filename }

            // Separate metadata for create vs update. Do NOT set parents on update.
            val createMetadata = File().apply {
                name = filename
                parents = listOf("appDataFolder")
            }
            val updateMetadata = File().apply {
                name = filename
            }
            val media = InputStreamContent("application/zip", ByteArrayInputStream(bytes))

            if (existing != null) {
                // Do not change parents when updating; only content/name.
                service.files().update(existing.id, updateMetadata, media).execute()
            } else {
                service.files().create(createMetadata, media).setFields("id").execute()
            }
            true
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            false
        }
    }

    suspend fun listBackups(): List<File> = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext emptyList()
        try {
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id,name,modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            emptyList()
        }
    }

    suspend fun download(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val service = driveService ?: return@withContext null
        try {
            val out = java.io.ByteArrayOutputStream()
            service.files().get(fileId).executeMediaAndDownloadTo(out)
            out.toByteArray()
        } catch (e: Exception) {
            lastErrorMessage = e.localizedMessage ?: e.toString()
            null
        }
    }
}
