package com.ledge.splitbook.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledge.splitbook.data.entity.MemberEntity
import com.ledge.splitbook.data.repo.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val membersRepo: MemberRepository
) : ViewModel() {

    data class UiState(
        val groupId: Long = 0L,
        val members: List<MemberEntity> = emptyList(),
        val message: String = ""
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun load(groupId: Long) {
        _ui.value = UiState(groupId = groupId)
        viewModelScope.launch {
            membersRepo.observeMembers(groupId).collectLatest { list ->
                _ui.value = _ui.value.copy(members = list)
            }
        }
    }

    fun add(name: String, deposit: Double = 0.0, isAdmin: Boolean = false) {
        val gid = _ui.value.groupId
        if (gid == 0L) return
        viewModelScope.launch {
            membersRepo.addMember(gid, name, deposit, isAdmin)
            _ui.value = _ui.value.copy(message = "Added $name")
        }
    }

    fun rename(memberId: Long, newName: String) {
        viewModelScope.launch {
            membersRepo.renameMember(memberId, newName)
            _ui.value = _ui.value.copy(message = "Renamed")
        }
    }

    fun remove(memberId: Long) {
        val gid = _ui.value.groupId
        viewModelScope.launch {
            val ok = membersRepo.removeMemberIfUnused(gid, memberId)
            _ui.value = _ui.value.copy(message = if (ok) "Removed" else "Cannot remove: used in expenses")
        }
    }
}
