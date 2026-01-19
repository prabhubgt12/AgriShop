package com.ledge.splitbook.ui.vm

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo: BackupRepository
) : ViewModel() {

    data class UiState(
        val signedIn: Boolean = false,
        val accountEmail: String? = null,
        val lastBackupTime: String? = null,
        val isRunning: Boolean = false,
        val runningOp: String? = null, // "backup" or "restore"
        val error: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ok = repo.tryInitFromLastAccount()
            refresh()
        }
    }

    fun getSignInIntent(): Intent = repo.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            val ok = repo.handleSignInResult(data)
            if (!ok) {
                _ui.value = _ui.value.copy(error = "Sign-in failed")
            }
            refresh()
        }
    }

    fun signOut() {
        repo.signOut()
        viewModelScope.launch { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val signed = repo.isSignedIn()
            val last = repo.lastBackupTime()
            _ui.value = _ui.value.copy(
                signedIn = signed,
                accountEmail = repo.lastAccountEmail(),
                lastBackupTime = last,
                isRunning = false,
                error = null
            )
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isRunning = true, runningOp = "backup", error = null)
            val ok = repo.backupNow()
            val last = repo.lastBackupTime()
            _ui.value = _ui.value.copy(
                isRunning = false,
                runningOp = null,
                lastBackupTime = last,
                error = if (!ok) (com.ledge.splitbook.data.backup.DriveClient.lastError() ?: "Backup failed") else null
            )
        }
    }

    fun restoreLatest() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isRunning = true, runningOp = "restore", error = null)
            val ok = repo.restoreLatest()
            val last = repo.lastBackupTime()
            _ui.value = _ui.value.copy(
                isRunning = false,
                runningOp = null,
                lastBackupTime = last,
                error = if (!ok) (com.ledge.splitbook.data.backup.DriveClient.lastError() ?: "Restore failed") else null
            )
        }
    }
}
